package com.tars.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAIHttpClient {   // keeping the class name so GeminiService needs no changes

    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    public String call(String prompt) throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "model", model,
                        "messages", java.util.List.of(
                                java.util.Map.of("role", "user", "content", prompt)
                        ),
                        "temperature", 0.2
                )
        );

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        log.debug("OpenAI response status: {}", response.statusCode());

        if (response.statusCode() == 429) {
            throw new RuntimeException("OpenAI API error: 503"); // reuse same message for retry logic
        }
        if (response.statusCode() == 401) {
            throw new RuntimeException("OpenAI API unauthorized - check api key");
        }
        if (response.statusCode() != 200) {
            log.error("OpenAI error body: {}", response.body()); // ← add this
            throw new RuntimeException("OpenAI API error: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.at("/choices/0/message/content").asText();
    }
}