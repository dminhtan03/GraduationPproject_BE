package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.ActionItem;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.CleanedTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.TranscriptSegment;
import com.finalProject.BookingMeetingRoom.service.aiplatform.AiLlmService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionItemExtractor {

    private final AiLlmService aiLlmService;

    public ActionItemExtractor(AiLlmService aiLlmService) {
        this.aiLlmService = aiLlmService;
    }

    public List<ActionItem> extract(CleanedTranscript transcript) {
        if (transcript == null || transcript.getSegments() == null || transcript.getSegments().isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> segmentData = new ArrayList<>();
        for (TranscriptSegment seg : transcript.getSegments()) {
            Map<String, Object> row = new HashMap<>();
            row.put("speaker", seg.getSpeaker());
            row.put("text", seg.getText());
            row.put("segment_id", seg.getSegmentId());
            segmentData.add(row);
        }

        String prompt = "Extract action items from transcript segments. Return JSON: {\"items\":[...]}" +
                " Use fields title, description, assignee, due_date, source_segment_ids.\n\nSegments:\n" +
                segmentData.toString();

        JsonNode node = aiLlmService.runJson("Return JSON only.", prompt, 0.1);
        if (node == null || !node.isObject()) {
            return List.of();
        }

        JsonNode items = node.get("items");
        if (items == null || !items.isArray()) {
            return List.of();
        }

        List<ActionItem> results = new ArrayList<>();
        for (JsonNode item : items) {
            String title = item.path("title").asText("").trim();
            if (title.length() < 5) {
                continue;
            }
            ActionItem out = ActionItem.builder()
                    .title(title)
                    .description(asText(item, "description"))
                    .assignee(asText(item, "assignee"))
                    .dueDate(asText(item, "due_date"))
                    .priority("medium")
                    .confidence(inferConfidence(title))
                    .sourceSpeaker(null)
                    .sourceSegmentIds(toStringList(item.get("source_segment_ids")))
                    .needsReview(true)
                    .build();
            results.add(out);
        }
        return results;
    }

    private String asText(JsonNode node, String field) {
        String val = node.path(field).asText("").trim();
        return val.isBlank() ? null : val;
    }

    private Double inferConfidence(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("deadline") || lower.contains("must")) {
            return 0.85;
        }
        return 0.7;
    }

    private List<String> toStringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode n : node) {
            if (n.isTextual() && !n.asText().isBlank()) {
                out.add(n.asText());
            }
        }
        return out;
    }
}
