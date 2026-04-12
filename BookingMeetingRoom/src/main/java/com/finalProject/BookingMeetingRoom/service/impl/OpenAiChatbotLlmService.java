package com.finalProject.BookingMeetingRoom.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finalProject.BookingMeetingRoom.common.enums.ChatbotIntent;
import com.finalProject.BookingMeetingRoom.common.utils.ChatbotMessageParser;
import com.finalProject.BookingMeetingRoom.service.ChatbotLlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenAiChatbotLlmService implements ChatbotLlmService {

    private final ObjectMapper objectMapper;

    @Value("${application.ai.llm.enabled:false}")
    private boolean llmEnabled;

    @Value("${application.ai.llm.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${application.ai.llm.model:gpt-4o-mini}")
    private String model;

    @Value("${application.ai.llm.api-key:}")
    private String apiKey;

    @Value("${application.ai.llm.timeout-ms:10000}")
    private int timeoutMs;

    @Override
    public Optional<ChatbotMessageParser.ParseResult> parse(String message, List<String> recentUserMessages) {
        if (!llmEnabled) return Optional.empty();
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        if (message == null || message.isBlank()) return Optional.empty();

        try {
            String endpoint = normalizeBaseUrl(baseUrl) + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("temperature", 0.1);
            body.put("response_format", Map.of("type", "json_object"));

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt()));
            messages.add(Map.of("role", "user", "content", buildUserPrompt(message, recentUserMessages)));
            body.put("messages", messages);

            RestTemplate restTemplate = new RestTemplate();
            restTemplate.setRequestFactory(clientHttpRequestFactory(timeoutMs));

            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) return Optional.empty();

            JsonNode parsed = objectMapper.readTree(stripCodeFence(content));
            ChatbotMessageParser.ParseResult result = toParseResult(message, parsed);
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("LLM parse skipped due to error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private org.springframework.http.client.SimpleClientHttpRequestFactory clientHttpRequestFactory(int timeoutMillis) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, timeoutMillis));
        factory.setReadTimeout(Math.max(1000, timeoutMillis));
        return factory;
    }

    private ChatbotMessageParser.ParseResult toParseResult(String originalMessage, JsonNode parsed) {
        ChatbotIntent intent = parseIntent(parsed.path("intent").asText(null));
        String roomCode = normalizedOrNull(parsed.path("roomCode").asText(null));
        LocalDate date = parseDate(parsed.path("date").asText(null));
        LocalTime start = parseTime(parsed.path("startTime").asText(null));
        LocalTime end = parseTime(parsed.path("endTime").asText(null));
        Integer minCapacity = parsed.path("minCapacity").isNumber() ? parsed.path("minCapacity").asInt() : null;

        String normalizedMessage = originalMessage == null ? "" : originalMessage.trim().toLowerCase(Locale.ROOT);
        return new ChatbotMessageParser.ParseResult(intent, normalizedMessage, roomCode, date, start, end, false, minCapacity);
    }

    private ChatbotIntent parseIntent(String intentRaw) {
        if (intentRaw == null || intentRaw.isBlank()) return ChatbotIntent.FALLBACK;
        try {
            return ChatbotIntent.valueOf(intentRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ChatbotIntent.FALLBACK;
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return null;
        try {
            return LocalTime.parse(timeStr.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String normalizedOrNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isBlank() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeBaseUrl(String url) {
        String u = url == null ? "https://api.openai.com/v1" : url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private String stripCodeFence(String content) {
        String s = content.trim();
        if (s.startsWith("```") && s.endsWith("```")) {
            int firstLineBreak = s.indexOf('\n');
            if (firstLineBreak > 0) {
                s = s.substring(firstLineBreak + 1, s.length() - 3).trim();
            }
        }
        return s;
    }

    private String buildUserPrompt(String message, List<String> recentUserMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Current user message:\n").append(message).append("\n\n");
        sb.append("Recent user context (newest first):\n");
        if (recentUserMessages == null || recentUserMessages.isEmpty()) {
            sb.append("- none\n");
        } else {
            for (String ctx : recentUserMessages) {
                if (ctx == null || ctx.isBlank()) continue;
                sb.append("- ").append(ctx).append("\n");
            }
        }

        sb.append("\nReturn only JSON with these fields: intent, roomCode, date, startTime, endTime, minCapacity. ");
        sb.append("Use null if unknown. Date format yyyy-MM-dd. Time format HH:mm:ss. ");
        sb.append("Intent must be one of CHECK_AVAILABLE_ROOMS_TODAY, SUGGEST_ROOMS_BY_CAPACITY, BOOK_ROOM, FALLBACK.");
        return sb.toString();
    }

    private String systemPrompt() {
        return "You are an intent and slot extractor for a meeting-room booking chatbot. " +
                "Classify intent and extract roomCode/date/startTime/endTime/minCapacity from multilingual user text. " +
                "Do not invent values. Use null when not provided.";
    }
}
