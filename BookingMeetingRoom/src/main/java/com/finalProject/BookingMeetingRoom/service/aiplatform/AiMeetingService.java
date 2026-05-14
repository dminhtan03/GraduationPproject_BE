package com.finalProject.BookingMeetingRoom.service.aiplatform;

import com.finalProject.BookingMeetingRoom.model.request.aiplatform.MeetingAnalyzeRequest;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.ExtractedTaskItem;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.MeetingSummaryResponse;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.CleanedTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.MeetingOutput;
import com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline.ChunkedTaskExtractor;
import com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline.MeetingPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiMeetingService {

    private final AiLlmService aiLlmService;
    private final AiSttService aiSttService;

    private static final String ANALYZE_SYSTEM = """
            Ban la AI trich xuat nhiem vu tu thong tin cuoc hop.

            Dua vao tieu de, mo ta, nguoi tham gia va ghi chu cuoc hop, tao 2-5 nhiem vu cu the.

            Tra ve CHI mot JSON array, khong giai thich, khong markdown. Moi phan tu co cac truong:
            - title: ten nhiem vu ngan gon (bat buoc)
            - description: mo ta chi tiet
            - goal: muc tieu can dat
            - expected_result: ket qua ky vong
            - priority: \"low\" hoac \"high\" hoac \"urgent\" (khong dung medium)
            - due_at: ISO 8601 hoac null
            - ai_confidence: so tu 0.0 den 1.0
            - ai_raw_text: trich dan tu thong tin cuoc hop lam can cu
            """;

    public List<ExtractedTaskItem> analyze(MeetingAnalyzeRequest request) {
        if (request != null && request.getAudioPath() != null && !request.getAudioPath().isBlank()) {
            MeetingOutput output = runPipeline(request);
            CleanedTranscript transcript = output != null ? output.getTranscript() : null;
            String fullText = transcript != null ? transcript.getFullText() : null;
            ChunkedTaskExtractor extractor = new ChunkedTaskExtractor(aiLlmService);
            return normalizePriorities(extractor.extract(fullText));
        }

        String transcript = resolveTranscript(request);
        String userPrompt = buildContext(request, transcript);
        String raw = aiLlmService.runText(ANALYZE_SYSTEM, userPrompt, 0.2);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        try {
            String json = extractJsonArray(raw);
            if (json == null) {
                return List.of();
            }
            return normalizePriorities(parseExtractedTasks(json));
        } catch (Exception ex) {
            log.warn("Meeting analyze parse failed: {}", ex.getMessage());
            return List.of();
        }
    }

    public MeetingSummaryResponse summarize(MeetingAnalyzeRequest request) {
        if (request != null && request.getAudioPath() != null && !request.getAudioPath().isBlank()) {
            MeetingOutput output = runPipeline(request);
            String transcript = output != null && output.getTranscript() != null
                    ? output.getTranscript().getFullText() : "";
            String summary = output != null && output.getMinutes() != null
                    ? output.getMinutes().getSummary() : null;
            if (summary == null || summary.isBlank()) {
                summary = fallbackSummary(request);
            }
            return MeetingSummaryResponse.builder()
                    .transcript(transcript != null ? transcript : "")
                    .summary(summary)
                    .build();
        }

        String transcript = resolveTranscript(request);
        String userPrompt = buildContext(request, transcript);

        String system = "You are a meeting summarizer. Write 3-6 sentences, no bullet points.";
        String summary = aiLlmService.runText(system, userPrompt, 0.2);
        if (summary == null || summary.isBlank()) {
            summary = fallbackSummary(request);
        }
        return MeetingSummaryResponse.builder()
                .transcript(transcript != null ? transcript : "")
                .summary(summary)
                .build();
    }

    public Map<String, Object> processForDrafts(String audioPath, String meetingTitle) {
        MeetingAnalyzeRequest req = MeetingAnalyzeRequest.builder()
            .meetingTitle(meetingTitle)
            .audioPath(audioPath)
            .build();
        MeetingOutput output = runPipeline(req);
        List<ExtractedTaskItem> tasks = analyze(req);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", output);
        result.put("extracted_items", tasks);
        result.put("review_required", true);
        return result;
    }

    public MeetingOutput processMeeting(MeetingAnalyzeRequest request) {
        return runPipeline(request);
    }

    private String resolveTranscript(MeetingAnalyzeRequest request) {
        if (request == null) {
            return null;
        }
        if (request.getTranscript() != null && !request.getTranscript().isBlank()) {
            return request.getTranscript();
        }
        return null;
    }

    private String buildContext(MeetingAnalyzeRequest request, String transcript) {
        List<String> parts = new ArrayList<>();
        if (request != null) {
            if (request.getMeetingTitle() != null) {
                parts.add("Cuoc hop: " + request.getMeetingTitle());
            }
            if (request.getDescription() != null && !request.getDescription().isBlank()) {
                parts.add("Mo ta: " + request.getDescription());
            }
            if (request.getScheduledStart() != null && !request.getScheduledStart().isBlank()) {
                parts.add("Thoi gian: " + request.getScheduledStart());
            }
            if (request.getParticipants() != null && !request.getParticipants().isEmpty()) {
                parts.add("Nguoi tham gia: " + String.join(", ", request.getParticipants()));
            }
            if (request.getNotes() != null && !request.getNotes().isEmpty()) {
                parts.add("Ghi chu:\n- " + String.join("\n- ", request.getNotes()));
            }
            if (request.getActionItems() != null && !request.getActionItems().isEmpty()) {
                parts.add("Action items:\n- " + String.join("\n- ", request.getActionItems()));
            }
        }
        if (transcript != null && !transcript.isBlank()) {
            String clipped = transcript.length() > 3000 ? transcript.substring(0, 3000) : transcript;
            parts.add("Transcript:\n" + clipped);
        }
        return String.join("\n\n", parts);
    }

    private List<ExtractedTaskItem> normalizePriorities(List<ExtractedTaskItem> items) {
        if (items == null) {
            return List.of();
        }
        for (ExtractedTaskItem item : items) {
            if (item == null) {
                continue;
            }
            String p = item.getPriority();
            if (p == null) {
                item.setPriority("low");
                continue;
            }
            String lower = p.trim().toLowerCase();
            if ("medium".equals(lower)) {
                item.setPriority("low");
            } else if (!List.of("low", "high", "urgent").contains(lower)) {
                item.setPriority("low");
            } else {
                item.setPriority(lower);
            }
        }
        return items;
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start == -1 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private List<ExtractedTaskItem> parseExtractedTasks(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<ExtractedTaskItem>>() {}
            );
        } catch (Exception ex) {
            log.warn("Parse extracted tasks failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private MeetingOutput runPipeline(MeetingAnalyzeRequest request) {
        if (request == null || request.getAudioPath() == null || request.getAudioPath().isBlank()) {
            return null;
        }
        MeetingPipeline pipeline = new MeetingPipeline(aiSttService, aiLlmService);
        return pipeline.run(request.getAudioPath(), request.getMeetingTitle(), "vi");
    }

    private String fallbackSummary(MeetingAnalyzeRequest request) {
        if (request == null || request.getMeetingTitle() == null) {
            return "Khong co du thong tin de tom tat cuoc hop.";
        }
        return "Cuoc hop \"" + request.getMeetingTitle() + "\" da duoc xu ly.";
    }
}
