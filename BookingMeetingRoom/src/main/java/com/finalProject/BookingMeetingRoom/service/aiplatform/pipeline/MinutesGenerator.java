package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.CleanedTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.MeetingMinutes;
import com.finalProject.BookingMeetingRoom.service.aiplatform.AiLlmService;

import java.util.ArrayList;
import java.util.List;

public class MinutesGenerator {

    private final AiLlmService aiLlmService;

    public MinutesGenerator(AiLlmService aiLlmService) {
        this.aiLlmService = aiLlmService;
    }

    public MeetingMinutes generate(CleanedTranscript transcript, String meetingTitle) {
        if (transcript == null || transcript.getFullText() == null || transcript.getFullText().isBlank()) {
            return emptyMinutes(meetingTitle);
        }

        String prompt = "You are a meeting minutes generator. Use only transcript data. " +
                "Return JSON with title, summary, key_decisions, discussion_points, risks, open_questions.\n\n" +
                "Transcript:\n" + transcript.getFullText();

        JsonNode node = aiLlmService.runJson("Return JSON only.", prompt, 0.2);
        if (node == null || !node.isObject()) {
            return emptyMinutes(meetingTitle);
        }

        String title = node.path("title").asText(meetingTitle != null ? meetingTitle : "Meeting");
        String summary = node.path("summary").asText("").trim();
        List<String> keyDecisions = toList(node.get("key_decisions"));
        List<String> discussionPoints = toList(node.get("discussion_points"));
        List<String> risks = toList(node.get("risks"));
        List<String> openQuestions = toList(node.get("open_questions"));

        if (summary.isBlank()) {
            summary = fallbackSummary(transcript.getFullText());
        }

        return MeetingMinutes.builder()
                .title(title)
                .summary(summary)
                .keyDecisions(keyDecisions)
                .discussionPoints(discussionPoints)
                .risks(risks)
                .openQuestions(openQuestions)
                .build();
    }

    private MeetingMinutes emptyMinutes(String meetingTitle) {
        return MeetingMinutes.builder()
                .title(meetingTitle != null ? meetingTitle : "Meeting")
                .summary("Not enough data to generate minutes.")
                .keyDecisions(List.of())
                .discussionPoints(List.of())
                .risks(List.of())
                .openQuestions(List.of())
                .build();
    }

    private List<String> toList(JsonNode node) {
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

    private String fallbackSummary(String text) {
        String trimmed = text.trim();
        if (trimmed.length() <= 200) {
            return trimmed;
        }
        return trimmed.substring(0, 200);
    }
}
