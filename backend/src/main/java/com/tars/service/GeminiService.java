package com.tars.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.model.AnomalyAnalysis;
import com.tars.model.Anomaly;
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
import java.util.Arrays;
import java.util.List;
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

    // Year window for historical context — ±50 years around the submitted year
    private static final int YEAR_WINDOW = 50;

    /**
     * Called from ReportService after saving the report.
     * Runs in a separate thread — HTTP response to agent is already returned by now.
     * Has its own @Transactional because it's a different thread from the caller.
     */
    @Async
    @Transactional
    public void analyzeReport(Long reportId) {
        ObservationReport report = reportRepository.findById(reportId).orElse(null);
        if (report == null) {
            log.error("GeminiService: report {} not found", reportId);
            return;
        }

        // Create a PENDING analysis record immediately so the agent can poll and see something
        AnomalyAnalysis analysis = AnomalyAnalysis.builder()
                .report(report)
                .analysisStatus(AnalysisStatus.PENDING)
                .build();
        analysis = analysisRepository.save(analysis);

        try {
            // Step 1 — fetch historical context for Gemini prompt
            List<ObservationReport> historicalReports = fetchHistoricalContext(report);

            // Step 2 — build prompt and call Gemini
            String prompt = buildPrompt(report, historicalReports);
            String rawResponse = callGemini(prompt);

            // Step 3 — parse response, retry with stricter prompt if needed
            JsonNode parsed = parseGeminiResponse(rawResponse);
            if (parsed == null) {
                log.warn("GeminiService: first parse failed for report {}, retrying", reportId);
                String retryResponse = callGemini(buildStrictPrompt(report, historicalReports));
                parsed = parseGeminiResponse(retryResponse);
            }

            // Step 4 — save results
            if (parsed != null) {
                saveCompletedAnalysis(analysis, parsed, report, historicalReports);
            } else {
                // Both calls failed to return valid JSON — mark unresolved
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

        // Use first keyword for matching — good enough for context
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

    /**
     * Stricter version used on retry — same data, more explicit JSON enforcement.
     */
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

        // Extract the text content from Gemini's response envelope
        JsonNode root = objectMapper.readTree(response.body());
        return root.at("/candidates/0/content/parts/0/text").asText();
    }

    /**
     * Tries to parse Gemini's text output as JSON.
     * Returns null if parsing fails — caller decides whether to retry.
     */
    private JsonNode parseGeminiResponse(String rawText) {
        try {
            // Strip markdown code fences if Gemini ignored instructions
            String cleaned = rawText.trim()
                    .replaceAll("^```json", "")
                    .replaceAll("^```", "")
                    .replaceAll("```$", "")
                    .trim();
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("GeminiService: JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    protected void saveCompletedAnalysis(AnomalyAnalysis analysis, JsonNode parsed, ObservationReport report, List<ObservationReport> historicalReports) {
        boolean confirmed = parsed.path("confirmed").asBoolean(false);
        String explanation = parsed.path("explanation").asText("");

        String contributingIds = extractIds(parsed, "contributingReportIds");

        // Our selection — what we sent to Gemini
        String correlatedIds = historicalReports.stream()
                .map(r -> String.valueOf(r.getId()))
                .collect(Collectors.joining(","));

        analysis.setConfirmed(confirmed);
        analysis.setExplanation(explanation);
        analysis.setCorrelatedReportIds(correlatedIds);
        analysis.setAnalysisStatus(AnalysisStatus.COMPLETED);
        analysisRepository.save(analysis);

        if (confirmed) {
            // Parse type and paradoxRisk safely
            AnomalyType type = parseEnum(AnomalyType.class, parsed.path("type").asText());
            ParadoxRisk paradoxRisk = parseEnum(ParadoxRisk.class, parsed.path("paradoxRisk").asText());

            Timeline timeline = report.getTimeline();
            Anomaly anomaly = Anomaly.builder()
                    .analysis(analysis)
                    .type(type)
                    .paradoxRisk(paradoxRisk)
                    .timeline(timeline)
                    .year(report.getYear())
                    .contributingReportIds(contributingIds)
                    .build();
            anomalyRepository.save(anomaly);

            report.setStatus(ReportStatus.CONFIRMED);
        } else {
            report.setStatus(ReportStatus.REJECTED);
        }
        reportRepository.save(report);
    }

    private String extractIds(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            node.forEach(n -> {
                if (!sb.isEmpty()) sb.append(",");
                sb.append(n.asText());
            });
            return sb.toString();
        }
        return null;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}