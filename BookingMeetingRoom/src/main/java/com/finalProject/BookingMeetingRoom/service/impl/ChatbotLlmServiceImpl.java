package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.ChatbotIntent;
import com.finalProject.BookingMeetingRoom.common.utils.ChatbotMessageParser;
import com.finalProject.BookingMeetingRoom.service.ChatbotLlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * LLM-assisted chatbot message parser.
 * If AI_LLM_API_KEY is not configured, falls back to returning empty
 * so the rule-based ChatbotMessageParser handles everything.
 */
@Service
@Slf4j
public class ChatbotLlmServiceImpl implements ChatbotLlmService {

    @Value("${application.ai.llm.api-key:}")
    private String apiKey;

    @Value("${application.ai.llm.enabled:true}")
    private boolean enabled;

    @Override
    public Optional<ChatbotMessageParser.ParseResult> parse(String message, List<String> recentUserMessages) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.debug("ChatbotLlmService: LLM disabled or no API key — skipping LLM parse");
            return Optional.empty();
        }

        try {
            return callLlm(message, recentUserMessages);
        } catch (Exception ex) {
            log.warn("ChatbotLlmService: LLM call failed — {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ChatbotMessageParser.ParseResult> callLlm(String message, List<String> recentUserMessages) {
        // Build context from recent messages
        StringBuilder ctx = new StringBuilder();
        if (recentUserMessages != null && !recentUserMessages.isEmpty()) {
            ctx.append("Previous messages: ");
            recentUserMessages.forEach(m -> ctx.append("[").append(m).append("] "));
            ctx.append("\n");
        }
        ctx.append("Current message: ").append(message);

        String prompt = """
                You are a room-booking assistant. Extract booking intent from the Vietnamese or English message.
                Return JSON ONLY, no explanation:
                {
                  "intent": "BOOK_ROOM|CHECK_AVAILABLE_ROOMS_TODAY|CANCEL_RESERVATION|EXTEND_RESERVATION|RETURN_ROOM|SUGGEST_ROOMS_BY_CAPACITY|VIEW_FACILITY_DETAILS|FALLBACK",
                  "roomCode": "string or null",
                  "date": "YYYY-MM-DD or null",
                  "startTime": "HH:mm or null",
                  "endTime": "HH:mm or null",
                  "minCapacity": number or null
                }
                Message context:
                """ + ctx;

        String response = callOpenAI(prompt);
        if (response == null || response.isBlank()) return Optional.empty();

        return parseJsonResponse(response);
    }

    private String callOpenAI(String prompt) {
        try {
            var client = java.net.http.HttpClient.newHttpClient();
            String body = String.format("""
                    {"model":"gpt-4o-mini","messages":[{"role":"user","content":%s}],"temperature":0.1}
                    """, jsonString(prompt));
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            var resp = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String raw = resp.body();
                // Extract content from choices[0].message.content
                int start = raw.indexOf("\"content\":");
                if (start < 0) return null;
                int q1 = raw.indexOf('"', start + 10);
                int q2 = raw.indexOf('"', q1 + 1);
                // Use a more robust extraction
                return extractContent(raw);
            }
        } catch (Exception ex) {
            log.warn("OpenAI call failed: {}", ex.getMessage());
        }
        return null;
    }

    private String extractContent(String json) {
        // Simple extraction of content field from OpenAI response
        int idx = json.indexOf("\"content\":");
        if (idx < 0) return null;
        int start = json.indexOf('"', idx + 10) + 1;
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) { sb.append(c); escape = false; }
            else if (c == '\\') { escape = true; }
            else if (c == '"') break;
            else sb.append(c);
        }
        return sb.toString().replace("\\n", "\n").replace("\\\"", "\"");
    }

    private Optional<ChatbotMessageParser.ParseResult> parseJsonResponse(String json) {
        try {
            // Extract JSON block
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end < 0) return Optional.empty();
            String obj = json.substring(start, end + 1);

            String intentStr = extractJsonStr(obj, "intent");
            ChatbotIntent intent = ChatbotIntent.FALLBACK;
            if (intentStr != null) {
                try { intent = ChatbotIntent.valueOf(intentStr.toUpperCase()); }
                catch (IllegalArgumentException ignored) {}
            }

            String roomCode = extractJsonStr(obj, "roomCode");
            LocalDate date = null;
            String dateStr = extractJsonStr(obj, "date");
            if (dateStr != null && !dateStr.equals("null")) {
                try { date = LocalDate.parse(dateStr); } catch (Exception ignored) {}
            }

            LocalTime startTime = null, endTime = null;
            String stStr = extractJsonStr(obj, "startTime");
            String etStr = extractJsonStr(obj, "endTime");
            if (stStr != null && !stStr.equals("null")) {
                try { startTime = LocalTime.parse(stStr); } catch (Exception ignored) {}
            }
            if (etStr != null && !etStr.equals("null")) {
                try { endTime = LocalTime.parse(etStr); } catch (Exception ignored) {}
            }

            Integer minCapacity = null;
            String capStr = extractJsonNum(obj, "minCapacity");
            if (capStr != null) {
                try { minCapacity = Integer.parseInt(capStr); } catch (Exception ignored) {}
            }

            return Optional.of(new ChatbotMessageParser.ParseResult(
                    intent, json, roomCode, date, startTime, endTime, endTime == null, minCapacity));
        } catch (Exception ex) {
            log.warn("Failed to parse LLM JSON response: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private String extractJsonStr(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) {
            // might be null literal
            String after = json.substring(colon + 1).trim();
            if (after.startsWith("null")) return "null";
            return null;
        }
        int q2 = json.indexOf('"', q1 + 1);
        return q2 > q1 ? json.substring(q1 + 1, q2) : null;
    }

    private String extractJsonNum(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        String after = json.substring(colon + 1).trim();
        StringBuilder sb = new StringBuilder();
        for (char c : after.toCharArray()) {
            if (Character.isDigit(c)) sb.append(c);
            else if (!sb.isEmpty()) break;
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
