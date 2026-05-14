package com.finalProject.BookingMeetingRoom.service.aiplatform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finalProject.BookingMeetingRoom.common.config.AiPlatformProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiLlmService {

    private final AiPlatformProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public boolean isAvailable() {
        return props.isEnabled() && props.getApiKey() != null && !props.getApiKey().isBlank();
    }

    public String runText(String systemPrompt, String userPrompt, double temperature) {
        if (!isAvailable()) {
            return null;
        }
        try {
            String body = buildChatBody(systemPrompt, userPrompt, temperature);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(props.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("LLM call failed: status={} body={}", response.statusCode(), response.body());
                return null;
            }
            return extractContent(response.body());
        } catch (Exception ex) {
            log.warn("LLM call failed: {}", ex.getMessage());
            return null;
        }
    }

    public JsonNode runJson(String systemPrompt, String userPrompt, double temperature) {
        String text = runText(systemPrompt, userPrompt, temperature);
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        String json = extractJsonBlock(trimmed);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            log.warn("LLM JSON parse failed: {}", ex.getMessage());
            return null;
        }
    }

    private String buildChatBody(String systemPrompt, String userPrompt, double temperature) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", props.getModel());
        payload.put("temperature", temperature);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : ""),
                Map.of("role", "user", "content", userPrompt != null ? userPrompt : "")
        ));
        return objectMapper.writeValueAsString(payload);
    }

    private String extractContent(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return content.isTextual() ? content.asText() : null;
        } catch (Exception ex) {
            log.warn("LLM response parse failed: {}", ex.getMessage());
            return null;
        }
    }

    private String extractJsonBlock(String text) {
        int objStart = text.indexOf('{');
        int objEnd = text.lastIndexOf('}');
        int arrStart = text.indexOf('[');
        int arrEnd = text.lastIndexOf(']');

        if (arrStart != -1 && arrEnd > arrStart && (arrStart < objStart || objStart == -1)) {
            return text.substring(arrStart, arrEnd + 1);
        }
        if (objStart != -1 && objEnd > objStart) {
            return text.substring(objStart, objEnd + 1);
        }
        return null;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
