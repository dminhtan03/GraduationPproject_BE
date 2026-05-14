package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.ExtractedTaskItem;
import com.finalProject.BookingMeetingRoom.service.aiplatform.AiLlmService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChunkedTaskExtractor {

    private static final int CHUNK_WORDS = 2000;
    private static final int OVERLAP_WORDS = 200;
    private static final double MIN_CONFIDENCE = 0.45;

    private final AiLlmService aiLlmService;

    public ChunkedTaskExtractor(AiLlmService aiLlmService) {
        this.aiLlmService = aiLlmService;
    }

    public List<ExtractedTaskItem> extract(String transcript) {
        if (transcript == null || transcript.trim().length() < 20) {
            return List.of();
        }
        List<String> chunks = split(transcript);
        List<ExtractedTaskItem> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < chunks.size(); i++) {
            String context = "[Part " + (i + 1) + "/" + chunks.size() + "]\n\n" + chunks.get(i);
            String prompt = "Extract tasks from meeting transcript. Return JSON array only. " +
                    "Fields: title, description, goal, expected_result, priority, due_at, " +
                    "assigner_user_id, assignee_user_id, ai_confidence, ai_raw_text.\n\n" +
                    "Transcript:\n" + context;
            JsonNode node = aiLlmService.runJson("Return JSON array only.", prompt, 0.2);
            if (node == null || !node.isArray()) {
                continue;
            }
            for (JsonNode item : node) {
                String title = item.path("title").asText("").trim();
                if (title.isBlank()) {
                    continue;
                }
                String norm = normalize(title);
                if (seen.contains(norm)) {
                    continue;
                }
                double conf = item.path("ai_confidence").asDouble(0.5);
                if (conf < MIN_CONFIDENCE) {
                    continue;
                }
                ExtractedTaskItem out = ExtractedTaskItem.builder()
                        .title(title)
                        .description(asText(item, "description"))
                        .goal(asText(item, "goal"))
                        .expectedResult(asText(item, "expected_result"))
                        .priority(asText(item, "priority"))
                        .dueAt(asText(item, "due_at"))
                        .assignerUserId(asText(item, "assigner_user_id"))
                        .assigneeUserId(asText(item, "assignee_user_id"))
                        .aiConfidence(conf)
                        .aiRawText(asText(item, "ai_raw_text"))
                        .build();
                all.add(out);
                seen.add(norm);
            }
        }

        return all;
    }

    private List<String> split(String text) {
        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();
        int step = CHUNK_WORDS - OVERLAP_WORDS;
        int start = 0;
        while (start < words.length) {
            int end = Math.min(start + CHUNK_WORDS, words.length);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) sb.append(' ');
                sb.append(words[i]);
            }
            chunks.add(sb.toString());
            if (end == words.length) {
                break;
            }
            start += step;
        }
        return chunks;
    }

    private String normalize(String title) {
        return title.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private String asText(JsonNode node, String field) {
        String val = node.path(field).asText("").trim();
        return val.isBlank() ? null : val;
    }
}
