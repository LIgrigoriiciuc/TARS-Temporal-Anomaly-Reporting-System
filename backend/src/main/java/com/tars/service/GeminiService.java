package com.tars.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.model.Anomaly;
import com.tars.model.AnomalyAnalysis;
import com.tars.model.ObservationReport;
import com.tars.model.enums.AnalysisStatus;
import com.tars.model.enums.AnomalyType;
import com.tars.model.enums.ParadoxRisk;
import com.tars.model.enums.ReportStatus;
import com.tars.model.mappers.ReportMapper;
import com.tars.service.AlertService;
import com.tars.repository.AnomalyAnalysisRepository;
import com.tars.repository.AnomalyRepository;
import com.tars.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final ReportRepository reportRepository;
    private final AnomalyAnalysisRepository analysisRepository;
    private final com.tars.repository.SubscriptionRepository subscriptionRepository;
    private final AnomalyRepository anomalyRepository;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final AlertService alertService;
    private final GeminiHttpClient geminiHttpClient;

    private static final int YEAR_WINDOW = 50;
    private static final double OVERLAP_THRESHOLD = 0.75;

    @Async
    @Transactional
    public void analyzeReport(Long reportId) {
        ObservationReport report = reportRepository.findById(reportId).orElse(null);
        if (report == null) {
            log.error("GeminiService: report {} not found", reportId);
            return;
        }

        AnomalyAnalysis analysis = AnomalyAnalysis.builder()
                .report(report)
                .analysisStatus(AnalysisStatus.PENDING)
                .build();
        analysis = analysisRepository.save(analysis);

        try {
            // NFR-11 — ENTERPRISE gets priority processing
            boolean isEnterprise = isEnterpriseAgent(report.getAgent());
            log.info("GeminiService: {} queue for report {}", isEnterprise ? "priority" : "standard", reportId);

            List<ObservationReport> historicalReports = fetchHistoricalContext(report);
            String prompt = buildPrompt(report, historicalReports, isEnterprise);
            String rawResponse = callGemini(prompt);

            log.info("GeminiService: raw response for report {}: {}", reportId, rawResponse);
            JsonNode parsed = parseGeminiResponse(rawResponse);
            if (parsed == null) {
                log.warn("GeminiService: first parse failed for report {}, retrying", reportId);
                String retryResponse = callGemini(buildStrictPrompt(report, historicalReports, isEnterprise));
                log.info("GeminiService: retry response for report {}: {}", reportId, retryResponse);
                parsed = parseGeminiResponse(retryResponse);
            }

            if (parsed != null) {
                saveCompletedAnalysis(analysis, parsed, report, historicalReports);
            } else {
                log.error("GeminiService: both attempts failed for report {}", reportId);
                analysis.setAnalysisStatus(AnalysisStatus.UNRESOLVED);
                analysis.setExplanation("Analysis could not be completed — AI response was not parseable.");
                analysisRepository.save(analysis);
                report.setStatus(ReportStatus.REJECTED);
                reportRepository.save(report);
                pushToAgent(report);
            }

        } catch (Exception e) {
            log.error("GeminiService: failed for report {}: {}", reportId, e.getMessage());
            analysis.setAnalysisStatus(AnalysisStatus.FAILED);
            analysis.setExplanation("Analysis failed due to a technical error.");
            analysisRepository.save(analysis);
            pushToAgent(report);
        }
    }

    @Transactional
    protected void saveCompletedAnalysis(AnomalyAnalysis analysis, JsonNode parsed,
                                         ObservationReport report,
                                         List<ObservationReport> historicalReports) {
        boolean confirmed = parsed.path("confirmed").asBoolean(false);
        String explanation = parsed.path("explanation").asText("");

        // What we sent to Gemini
        String correlatedIds = historicalReports.stream()
                .map(r -> String.valueOf(r.getId()))
                .collect(Collectors.joining(","));

        // What Gemini picked as causal — never includes the submitting agent's own report
        Set<Long> contributingSet = extractIdSet(parsed, "contributingReportIds");
        contributingSet.remove(report.getId());
        String contributingIds = contributingSet.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        analysis.setConfirmed(confirmed);
        analysis.setExplanation(explanation);
        analysis.setCorrelatedReportIds(correlatedIds);
        analysis.setContributingReportIds(contributingIds);
        analysis.setAnalysisStatus(AnalysisStatus.COMPLETED);

        if (confirmed) {
            AnomalyType type = parseEnum(AnomalyType.class, parsed.path("type").asText());
            ParadoxRisk paradoxRisk = parseEnum(ParadoxRisk.class, parsed.path("paradoxRisk").asText());

            // Check unverified anomalies first, then verified
            Anomaly anomaly = findOverlappingAnomaly(contributingSet, report.getTimeline().getId());

            if (anomaly != null) {
                // Link to existing anomaly
                log.info("GeminiService: report {} linked to anomaly {}", report.getId(), anomaly.getId());
                analysis.setAnomaly(anomaly);

                // Check if this new agent provides corroboration for an unverified anomaly
                if (!anomaly.isVerified()) {
                    Long submittingAgentId = report.getAgent().getId();
                    boolean newAgent = isNewAgent(anomaly.getContributingReportIds(), submittingAgentId);
                    if (newAgent) {
                        anomaly.setVerified(true);
                        anomalyRepository.save(anomaly);
                        log.info("GeminiService: anomaly {} corroborated and verified", anomaly.getId());
                        alertService.triggerIfCritical(anomaly);
                    }
                }
            } else {
                // New anomaly — always starts unverified (single observer so far)
                String foundingIds = report.getId() +
                        (contributingIds.isBlank() ? "" : "," + contributingIds);

                Anomaly newAnomaly = Anomaly.builder()
                        .type(type)
                        .paradoxRisk(paradoxRisk)
                        .timeline(report.getTimeline())
                        .year(report.getYear())
                        .contributingReportIds(foundingIds)
                        .verified(false)
                        .build();
                anomalyRepository.save(newAnomaly);
                analysis.setAnomaly(newAnomaly);
                log.info("GeminiService: new unverified anomaly created for report {}", report.getId());
                alertService.triggerIfCritical(newAnomaly);
            }

            report.setStatus(ReportStatus.CONFIRMED);
        } else {
            report.setStatus(ReportStatus.REJECTED);
        }

        analysisRepository.save(analysis);
        reportRepository.save(report);
        pushToAgent(report);
    }

    /**
     * Checks if the submitting agent is different from all agents
     * whose reports are in the founding contributing pool.
     * If yes — this is genuine independent corroboration.
     */
    private boolean isNewAgent(String foundingContributingIds, Long submittingAgentId) {
        Set<Long> foundingReportIds = parseIdSet(foundingContributingIds);
        if (foundingReportIds.isEmpty()) return true;

        // Load the founding reports and collect their agent IDs
        Set<Long> foundingAgentIds = reportRepository.findAllById(foundingReportIds)
                .stream()
                .map(r -> r.getAgent().getId())
                .collect(Collectors.toSet());

        return !foundingAgentIds.contains(submittingAgentId);
    }

    /**
     * Checks all anomalies on this timeline (unverified first, then verified)
     * for 75% overlap with the new contributing set.
     */
    private Anomaly findOverlappingAnomaly(Set<Long> newContributing, Long timelineId) {
        if (newContributing.isEmpty()) return null;

        // Unverified first (need corroboration more urgently), then verified
        List<Anomaly> all = anomalyRepository.findByTimelineId(timelineId);
        List<Anomaly> candidates = new ArrayList<>();
        all.stream().filter(a -> !a.isVerified()).forEach(candidates::add);
        all.stream().filter(Anomaly::isVerified).forEach(candidates::add);

        for (Anomaly candidate : candidates) {
            Set<Long> existingPool = parseIdSet(candidate.getContributingReportIds());
            if (existingPool.isEmpty()) continue;

            Set<Long> intersection = new HashSet<>(newContributing);
            intersection.retainAll(existingPool);

            int smallerSize = Math.min(newContributing.size(), existingPool.size());
            double overlap = (double) intersection.size() / smallerSize;

            if (overlap >= OVERLAP_THRESHOLD) {
                return candidate;
            }
        }
        return null;
    }

    private List<ObservationReport> fetchHistoricalContext(ObservationReport report) {
        if (report.getTimeline() == null || report.getYear() == null) return List.of();

        String keyword = null;
        if (report.getKeywords() != null && !report.getKeywords().isBlank()) {
            keyword = Arrays.stream(report.getKeywords().split(","))
                    .map(String::trim)
                    .findFirst()
                    .orElse(null);
        }

        return reportRepository.findHistoricalContext(
                report.getTimeline().getId(),
                report.getYear() - YEAR_WINDOW,
                report.getYear() + YEAR_WINDOW,
                keyword,
                report.getAgent().getId() // exclude submitting agent
        );
    }

    private String buildPrompt(ObservationReport report, List<ObservationReport> historical, boolean priority) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a temporal anomaly analyst for the TARS system (Temporal Anomaly Reporting System).
                Your task is to analyze a new observation report and determine whether it constitutes a confirmed temporal anomaly.
                
                Anomaly types:
                - PAR: Causal paradox — cause and effect are inverted
                - DUP: Temporal duplication — same object/person exists twice simultaneously
                - DEV: Timeline deviation — event occurred differently from reference records
                - RFT: Rift/breach — physical fracture in the space-time continuum
                - ERO: Temporal erosion — existence or memory of an element gradually disappears
                - LOP: Temporal loop — sequence of events repeats indefinitely
                
                Paradox risk levels: LOW, MEDIUM, HIGH, CRITICAL
                """ + (priority ? "PRIORITY: High-priority analysis request.\n\n" : "\n"));

        sb.append("NEW OBSERVATION REPORT:\n");
        sb.append("Report ID: ").append(report.getId()).append("\n");
        sb.append("Timeline: ").append(report.getTimeline() != null ? report.getTimeline().getName() : "unknown").append("\n");
        sb.append("Year: ").append(report.getYear()).append("\n");
        sb.append("Keywords: ").append(sanitize(report.getKeywords())).append("\n");
        sb.append("Description: ").append(sanitize(report.getDescription())).append("\n\n");

        if (!historical.isEmpty()) {
            sb.append("HISTORICAL CONTEXT (reports from other agents on the same timeline and period):\n");
            for (ObservationReport h : historical) {
                sb.append("- Report ID ").append(h.getId())
                        .append(" | Year: ").append(h.getYear())
                        .append(" | Status: ").append(h.getStatus())
                        .append(" | Keywords: ").append(sanitize(h.getKeywords()))
                        .append(" | Description: ").append(sanitize(h.getDescription()))
                        .append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("HISTORICAL CONTEXT: No related reports from other agents found.\n\n");
        }

        sb.append("""
                Respond ONLY with a valid JSON object. No markdown, no explanation outside the JSON.
                Schema:
                {
                  "confirmed": boolean,
                  "type": "PAR|DUP|DEV|RFT|ERO|LOP or null if not confirmed",
                  "paradoxRisk": "LOW|MEDIUM|HIGH|CRITICAL or null if not confirmed",
                  "explanation": "your reasoning as a string",
                  "contributingReportIds": [IDs from the historical context that directly determined this anomaly, empty if none or not confirmed]
                }
                """);

        return sb.toString();
    }

    private String buildStrictPrompt(ObservationReport report, List<ObservationReport> historical, boolean priority) {
        return buildPrompt(report, historical, priority) +
                "\nCRITICAL: Your response must start with { and end with }. No other characters outside the JSON object.";
    }

    private String callGemini(String prompt) throws Exception {
        return geminiHttpClient.call(prompt);
    }

    private JsonNode parseGeminiResponse(String rawText) {
        try {
            String cleaned = rawText.trim();
            // Strip ```json or ``` fences Gemini adds despite instructions
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceFirst("```json\s*", "").replaceFirst("```\s*", "");
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.lastIndexOf("```")).trim();
            }
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("GeminiService: JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    private boolean isEnterpriseAgent(com.tars.model.Agent agent) {
        return subscriptionRepository.findByAgentId(agent.getId())
                .map(s -> s.getPlan() == com.tars.model.enums.PlanType.ENTERPRISE)
                .orElse(false);
    }

    private void pushToAgent(ObservationReport report) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/analysis/" + report.getAgent().getId(),
                    ReportMapper.toSubmittedDto(report)
            );
        } catch (Exception e) {
            log.warn("GeminiService: WebSocket push failed for report {}: {}", report.getId(), e.getMessage());
        }
    }

    private Set<Long> extractIdSet(JsonNode root, String field) {
        Set<Long> ids = new HashSet<>();
        JsonNode node = root.path(field);
        if (node.isArray()) {
            node.forEach(n -> {
                try { ids.add(n.asLong()); } catch (Exception ignored) {}
            });
        }
        return ids;
    }

    private Set<Long> parseIdSet(String csv) {
        if (csv == null || csv.isBlank()) return new HashSet<>();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * NFR-12 — sanitizes free-text fields before injecting into Gemini prompt.
     * Strips prompt injection attempts like "ignore previous instructions".
     */
    private String sanitize(String input) {
        if (input == null) return "N/A";
        return input
                // Remove common prompt injection patterns
                .replaceAll("(?i)ignore (previous|above|all) instructions.*", "[REDACTED]")
                .replaceAll("(?i)you are now.*", "[REDACTED]")
                .replaceAll("(?i)disregard.*instructions.*", "[REDACTED]")
                // Strip special characters that could break JSON structure
                .replace("\\", "")
                .replace("```", "")
                // Limit length to prevent token stuffing
                .substring(0, Math.min(input.length(), 1000))
                .trim();
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}