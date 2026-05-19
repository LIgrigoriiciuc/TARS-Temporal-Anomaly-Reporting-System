package com.tars.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.model.Anomaly;
import com.tars.model.AnomalyAnalysis;
import com.tars.model.ObservationReport;
import com.tars.model.Timeline;
import com.tars.model.enums.AnalysisStatus;
import com.tars.model.enums.AnomalyType;
import com.tars.model.enums.ParadoxRisk;
import com.tars.model.enums.ReportStatus;
import com.tars.repository.AnomalyAnalysisRepository;
import com.tars.repository.AnomalyRepository;
import com.tars.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final ReportRepository reportRepository;
    private final AnomalyAnalysisRepository analysisRepository;
    private final AnomalyRepository anomalyRepository;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

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

        // Create PENDING analysis immediately so agent can see something while waiting
        AnomalyAnalysis analysis = AnomalyAnalysis.builder()
                .report(report)
                .analysisStatus(AnalysisStatus.PENDING)
                .build();
        analysis = analysisRepository.save(analysis);

        try {
            List<ObservationReport> historicalReports = fetchHistoricalContext(report);
            String prompt = buildPrompt(report, historicalReports);
            String rawResponse = callGemini(prompt);

            JsonNode parsed = parseGeminiResponse(rawResponse);
            if (parsed == null) {
                log.warn("GeminiService: first parse failed for report {}, retrying", reportId);
                String retryResponse = callGemini(buildStrictPrompt(report, historicalReports));
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
            }

        } catch (Exception e) {
            log.error("GeminiService: failed for report {}: {}", reportId, e.getMessage());
            analysis.setAnalysisStatus(AnalysisStatus.FAILED);
            analysis.setExplanation("Analysis failed due to a technical error.");
            analysisRepository.save(analysis);
        }
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
                keyword
        );
    }

    @Transactional
    protected void saveCompletedAnalysis(AnomalyAnalysis analysis, JsonNode parsed,
                                         ObservationReport report, List<ObservationReport> historicalReports) {
        boolean confirmed = parsed.path("confirmed").asBoolean(false);
        String explanation = parsed.path("explanation").asText("");

        // Our selection — what we sent to Gemini
        String correlatedIds = historicalReports.stream()
                .map(r -> String.valueOf(r.getId()))
                .collect(Collectors.joining(","));

        // Gemini's selection — what it thinks caused the anomaly, minus the current report
        Set<Long> contributingSet = extractIdSet(parsed, "contributingReportIds");
        contributingSet.remove(report.getId()); // never include self
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

            // Check if this analysis belongs to an existing anomaly (75% overlap)
            Anomaly anomaly = findOverlappingAnomaly(contributingSet, report.getTimeline().getId());

            if (anomaly != null) {
                // Link to existing anomaly and expand its contributing pool
                log.info("GeminiService: report {} linked to existing anomaly {}", report.getId(), anomaly.getId());
                mergeContributingIds(anomaly, contributingSet, report.getId());
                anomalyRepository.save(anomaly);
                analysis.setAnomaly(anomaly);
            } else {
                // Create new anomaly
                Anomaly newAnomaly = Anomaly.builder()
                        .type(type)
                        .paradoxRisk(paradoxRisk)
                        .timeline(report.getTimeline())
                        .year(report.getYear())
                        .contributingReportIds(report.getId() + (contributingIds.isBlank() ? "" : "," + contributingIds))
                        .build();
                anomalyRepository.save(newAnomaly);
                analysis.setAnomaly(newAnomaly);
                log.info("GeminiService: new anomaly created for report {}", report.getId());
            }

            report.setStatus(ReportStatus.CONFIRMED);
        } else {
            report.setStatus(ReportStatus.REJECTED);
        }

        analysisRepository.save(analysis);
        reportRepository.save(report);
    }

    /**
     * Finds an existing anomaly on the same timeline whose contributing pool
     * overlaps >= 75% with the new analysis's contributing IDs.
     * Excludes the current report's ID from comparison — every report matches itself trivially.
     */
    private Anomaly findOverlappingAnomaly(Set<Long> newContributing, Long timelineId) {
        if (newContributing.isEmpty()) return null;

        List<Anomaly> candidates = anomalyRepository.findByTimelineId(timelineId);

        for (Anomaly candidate : candidates) {
            Set<Long> existingPool = parseIdSet(candidate.getContributingReportIds());
            if (existingPool.isEmpty()) continue;

            Set<Long> intersection = new HashSet<>(newContributing);
            intersection.retainAll(existingPool);

            // Overlap relative to the smaller set
            int smallerSize = Math.min(newContributing.size(), existingPool.size());
            double overlap = (double) intersection.size() / smallerSize;

            if (overlap >= OVERLAP_THRESHOLD) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Merges new contributing IDs into the anomaly's pool (union, no duplicates).
     * Also adds the current report's own ID to the pool.
     */
    private void mergeContributingIds(Anomaly anomaly, Set<Long> newIds, Long currentReportId) {
        Set<Long> existing = parseIdSet(anomaly.getContributingReportIds());
        existing.addAll(newIds);
        existing.add(currentReportId);
        anomaly.setContributingReportIds(
                existing.stream().map(String::valueOf).collect(Collectors.joining(","))
        );
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

    private String buildPrompt(ObservationReport report, List<ObservationReport> historical) {
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
                
                """);

        sb.append("NEW OBSERVATION REPORT:\n");
        sb.append("Report ID: ").append(report.getId()).append("\n");
        sb.append("Timeline: ").append(report.getTimeline() != null ? report.getTimeline().getName() : "unknown").append("\n");
        sb.append("Year: ").append(report.getYear()).append("\n");
        sb.append("Keywords: ").append(report.getKeywords()).append("\n");
        sb.append("Description: ").append(report.getDescription()).append("\n\n");

        if (!historical.isEmpty()) {
            sb.append("HISTORICAL CONTEXT (related reports from the same timeline and period):\n");
            for (ObservationReport h : historical) {
                sb.append("- Report ID ").append(h.getId())
                        .append(" | Year: ").append(h.getYear())
                        .append(" | Status: ").append(h.getStatus())
                        .append(" | Keywords: ").append(h.getKeywords())
                        .append(" | Description: ").append(h.getDescription())
                        .append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("HISTORICAL CONTEXT: No related reports found.\n\n");
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

    private String buildStrictPrompt(ObservationReport report, List<ObservationReport> historical) {
        return buildPrompt(report, historical) +
                "\nCRITICAL: Your response must start with { and end with }. No other characters outside the JSON object.";
    }

    private String callGemini(String prompt) throws Exception {
        String requestBody = """
                {
                  "contents": [{
                    "parts": [{"text": %s}]
                  }]
                }
                """.formatted(objectMapper.writeValueAsString(prompt));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429 || response.statusCode() == 403) {
            throw new RuntimeException("Gemini API quota exceeded or unauthorized: " + response.statusCode());
        }
        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.at("/candidates/0/content/parts/0/text").asText();
    }

    private JsonNode parseGeminiResponse(String rawText) {
        try {
            String cleaned = rawText.trim()
                    .replaceAll("(?s)^```json", "")
                    .replaceAll("(?s)^```", "")
                    .replaceAll("```$", "")
                    .trim();
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("GeminiService: JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}