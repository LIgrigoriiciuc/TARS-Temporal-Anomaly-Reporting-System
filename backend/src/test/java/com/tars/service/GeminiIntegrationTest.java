package com.tars.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * NOT a unit test — makes a real call to Gemini API.
 * Run manually to verify your API key works and see raw responses.
 * Excluded from normal test suite via @Tag so CI doesn't run it.
 *
 * To run: right-click in IDE → Run, or:
 * mvn test -Dtest=GeminiIntegrationTest -Dgroups=integration
 */
@org.junit.jupiter.api.Tag("integration")
class GeminiIntegrationTest {

    private static final String API_KEY = "YOUR-API-KEY"; // replace before running
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

    @Test
    void callGemini_realRequest_printRawResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiHttpClient client = new GeminiHttpClient(objectMapper);
        ReflectionTestUtils.setField(client, "apiKey", API_KEY);
        ReflectionTestUtils.setField(client, "apiUrl", API_URL);

        String prompt = """
                You are a temporal anomaly analyst for the TARS system.
                
                Anomaly types: PAR, DUP, DEV, RFT, ERO, LOP
                Paradox risk levels: LOW, MEDIUM, HIGH, CRITICAL
                
                NEW OBSERVATION REPORT:
                Report ID: 1
                Timeline: ALPHA
                Year: 2045
                Keywords: loop, origin, paradox
                Description: I observed a person entering a building that they claimed to have never visited,
                yet security logs show their keycard was used 47 times over the past month.
                
                HISTORICAL CONTEXT: No related reports from other agents found.
                
                Respond ONLY with a valid JSON object. No markdown, no explanation outside the JSON.
                Schema:
                {
                  "confirmed": boolean,
                  "type": "PAR|DUP|DEV|RFT|ERO|LOP or null if not confirmed",
                  "paradoxRisk": "LOW|MEDIUM|HIGH|CRITICAL or null if not confirmed",
                  "explanation": "your reasoning as a string",
                  "contributingReportIds": []
                }
                """;

        System.out.println("=== SENDING PROMPT ===");
        System.out.println(prompt);
        System.out.println("======================");

        String rawResponse = client.call(prompt);

        System.out.println("=== RAW GEMINI RESPONSE ===");
        System.out.println(rawResponse);
        System.out.println("===========================");

        // Strip markdown fences if present, then pretty print
        String cleaned = rawResponse.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("```json\s*", "").replaceFirst("```\s*", "");
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.lastIndexOf("```")).trim();
        }

        System.out.println("=== PARSED JSON ===");
        try {
            Object parsed = objectMapper.readValue(cleaned, Object.class);
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed));
        } catch (Exception e) {
            System.out.println("Still not valid JSON after stripping: " + e.getMessage());
            System.out.println("Cleaned string was: " + cleaned);
        }
        System.out.println("===================");
    }
}