package com.finalProject.BookingMeetingRoom.service.aiplatform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finalProject.BookingMeetingRoom.model.request.aiplatform.MeetingAnalyzeRequest;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.ExtractedTaskItem;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.MeetingSummaryResponse;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.CleanedTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.MeetingMinutes;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.MeetingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Simplified AI meeting analysis service.
 * Flow: STT (Whisper) → 1 LLM call for tasks + 1 LLM call for summary.
 * No diarization, no speaker labels, no pipeline overhead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiMeetingService {

    private final AiLlmService aiLlmService;
    private final AiSttService aiSttService;
    private final ObjectMapper objectMapper;

    // ── System prompts ────────────────────────────────────────────────────────

    private static final String TASK_SYSTEM = """
            Bạn là AI chuyên gia phân tích cuộc họp và trích xuất nhiệm vụ công việc.

            Từ transcript cuộc họp, trích xuất TẤT CẢ nhiệm vụ, hành động, yêu cầu công việc được đề cập.

            QUAN TRỌNG:
            - Chỉ lấy nhiệm vụ LIÊN QUAN ĐẾN CÔNG VIỆC (kỹ thuật, tính năng, thiết kế, kiểm thử, tài liệu, v.v.)
            - BỎ QUA: câu chuyện phiếm, ví dụ minh họa không liên quan, chuyện cá nhân, mua sắm
            - Mỗi nhiệm vụ phải CỤ THỂ và CÓ THỂ THỰC HIỆN ĐƯỢC
            - Trích xuất càng nhiều nhiệm vụ công việc càng tốt (mục tiêu 10-20 nhiệm vụ)
            - Trả lời bằng CÙNG ngôn ngữ với transcript

            Chỉ trả về JSON array, không giải thích, không markdown:
            [{"title":"...","description":"...","goal":"...","expected_result":"...","priority":"low|high|urgent","due_at":null,"ai_confidence":0.85,"ai_raw_text":"..."}]
            """;

    private static final String SUMMARY_SYSTEM = """
            Bạn là AI tóm tắt cuộc họp.
            Chỉ tóm tắt đúng và đủ những gì thực sự được nói trong cuộc họp. Không thêm thắt, không phóng đại, không suy diễn.
            Viết 3-5 câu ngắn gọn, chỉ nêu: chủ đề cuộc họp, các quyết định hoặc kết luận chính, và hành động tiếp theo được nhắc đến.
            Nếu nội dung không rõ hoặc mờ nhạt, chỉ tóm tắt những gì nghe được rõ ràng.
            Không gán nhãn người nói. Viết văn xuôi.
            Trả lời bằng CÙNG ngôn ngữ với transcript.
            """;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Main entry point: STT + 2 LLM calls.
     * Returns summary + plain transcript + tasks.
     */
    public MeetingAnalysisResult processAudio(String audioPath, String meetingTitle) {
        // 1. STT — not LLM, just speech recognition
        String transcript = transcribe(audioPath);
        if (transcript == null || transcript.isBlank()) {
            log.warn("STT returned empty transcript for: {}", audioPath);
            return MeetingAnalysisResult.empty();
        }
        log.info("STT done: {} chars", transcript.length());

        String context = buildContext(meetingTitle, transcript);

        // 2. One LLM call: extract tasks
        List<ExtractedTaskItem> tasks = extractTasks(context);
        log.info("Task extraction done: {} tasks", tasks.size());

        // 3. One LLM call: generate summary
        String summary = generateSummary(context);
        log.info("Summary done: {} chars", summary.length());

        return new MeetingAnalysisResult(summary, transcript, tasks);
    }

    /** Used when no audio — analyze from metadata/transcript text only (1 LLM call). */
    public List<ExtractedTaskItem> analyze(MeetingAnalyzeRequest request) {
        if (request != null && request.getAudioPath() != null && !request.getAudioPath().isBlank()) {
            return processAudio(request.getAudioPath(), request.getMeetingTitle()).tasks();
        }
        String context = buildContext(request);
        return extractTasks(context);
    }

    /** Used when no audio — summarize from metadata/transcript text only (1 LLM call). */
    public MeetingSummaryResponse summarize(MeetingAnalyzeRequest request) {
        if (request != null && request.getAudioPath() != null && !request.getAudioPath().isBlank()) {
            MeetingAnalysisResult r = processAudio(request.getAudioPath(), request.getMeetingTitle());
            return MeetingSummaryResponse.builder()
                    .transcript(r.transcript())
                    .summary(r.summary())
                    .build();
        }
        String context = buildContext(request);
        String summary = generateSummary(context);
        String transcript = request != null && request.getTranscript() != null ? request.getTranscript() : "";
        return MeetingSummaryResponse.builder().transcript(transcript).summary(summary).build();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private String transcribe(String audioPath) {
        try {
            return aiSttService.transcribe(Path.of(audioPath), "vi");
        } catch (Exception e) {
            log.warn("STT failed: {}", e.getMessage());
            return null;
        }
    }

    private List<ExtractedTaskItem> extractTasks(String context) {
        if (!aiLlmService.isAvailable()) return List.of();
        // For very long transcripts, split into 2 overlapping halves
        if (context.length() > 24000) {
            return extractTasksChunked(context);
        }
        return extractTasksSingleCall(context);
    }

    private List<ExtractedTaskItem> extractTasksSingleCall(String context) {
        String raw = aiLlmService.runText(TASK_SYSTEM, context, 0.1);
        return parseTaskList(raw);
    }

    private List<ExtractedTaskItem> extractTasksChunked(String context) {
        int half = context.length() / 2;
        int overlap = Math.min(2000, context.length() / 10);
        String part1 = context.substring(0, Math.min(half + overlap, context.length()));
        String part2 = context.substring(Math.max(0, half - overlap));

        List<ExtractedTaskItem> all = new ArrayList<>(extractTasksSingleCall(part1));
        List<ExtractedTaskItem> part2Tasks = extractTasksSingleCall(part2);

        // Deduplicate by title similarity
        for (ExtractedTaskItem t : part2Tasks) {
            if (t.getTitle() == null) continue;
            boolean dup = all.stream().anyMatch(a ->
                    a.getTitle() != null && similar(a.getTitle(), t.getTitle()));
            if (!dup) all.add(t);
        }
        return all;
    }

    private String generateSummary(String context) {
        if (!aiLlmService.isAvailable()) return "";
        String result = aiLlmService.runText(SUMMARY_SYSTEM, context, 0.3);
        return result != null ? result.trim() : "";
    }

    private String buildContext(String title, String transcript) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) sb.append("Cuộc họp: ").append(title).append("\n\n");
        sb.append("Transcript:\n").append(transcript);
        return sb.toString();
    }

    private String buildContext(MeetingAnalyzeRequest req) {
        if (req == null) return "";
        StringBuilder sb = new StringBuilder();
        if (req.getMeetingTitle() != null) sb.append("Cuộc họp: ").append(req.getMeetingTitle()).append("\n\n");
        if (req.getTranscript() != null && !req.getTranscript().isBlank())
            sb.append("Transcript:\n").append(req.getTranscript());
        else if (req.getNotes() != null && !req.getNotes().isEmpty())
            sb.append("Ghi chú:\n- ").append(String.join("\n- ", req.getNotes()));
        return sb.toString();
    }

    private List<ExtractedTaskItem> parseTaskList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            int start = raw.indexOf('[');
            int end = raw.lastIndexOf(']');
            if (start == -1 || end <= start) return List.of();
            List<ExtractedTaskItem> items = objectMapper.readValue(
                    raw.substring(start, end + 1),
                    new TypeReference<>() {});
            return normalizePriorities(items.stream()
                    .filter(t -> t.getTitle() != null && !t.getTitle().isBlank())
                    .toList());
        } catch (Exception ex) {
            log.warn("Task parse failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<ExtractedTaskItem> normalizePriorities(List<ExtractedTaskItem> items) {
        for (ExtractedTaskItem item : items) {
            String p = item.getPriority();
            if (p == null) { item.setPriority("low"); continue; }
            String lower = p.trim().toLowerCase();
            if (!List.of("low", "high", "urgent").contains(lower)) item.setPriority("low");
            else item.setPriority(lower);
        }
        return items;
    }

    private boolean similar(String a, String b) {
        String na = a.toLowerCase().replaceAll("\\s+", " ").trim();
        String nb = b.toLowerCase().replaceAll("\\s+", " ").trim();
        return na.equals(nb) || na.contains(nb) || nb.contains(na)
                || levenshteinRatio(na, nb) > 0.8;
    }

    private double levenshteinRatio(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) levenshtein(a, b) / maxLen;
    }

    private int levenshtein(String a, String b) {
        int[] dp = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) dp[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int prev = dp[0]++;
            for (int j = 1; j <= b.length(); j++) {
                int tmp = dp[j];
                dp[j] = a.charAt(i - 1) == b.charAt(j - 1) ? prev
                        : 1 + Math.min(prev, Math.min(dp[j], dp[j - 1]));
                prev = tmp;
            }
        }
        return dp[b.length()];
    }

    // ── Compatibility methods ─────────────────────────────────────────────────

    /** Used by MeetingServiceImpl.aiExtractAssignments (draft flow). */
    public Map<String, Object> processForDrafts(String audioPath, String meetingTitle) {
        MeetingAnalysisResult result = processAudio(audioPath, meetingTitle);
        Map<String, Object> out = new LinkedHashMap<>();
        // Build a minimal pipeline wrapper so persistPipelineResult can parse it
        Map<String, Object> pipelineNode = new LinkedHashMap<>();
        pipelineNode.put("status", "completed");
        pipelineNode.put("job_id", UUID.randomUUID().toString());
        Map<String, Object> transcriptNode = new LinkedHashMap<>();
        transcriptNode.put("full_text", result.transcript());
        pipelineNode.put("transcript", transcriptNode);
        Map<String, Object> minutesNode = new LinkedHashMap<>();
        minutesNode.put("summary", result.summary());
        pipelineNode.put("minutes", minutesNode);
        out.put("pipeline", pipelineNode);
        // Convert ExtractedTaskItem list to raw maps for persistPipelineResult
        List<Map<String, Object>> taskMaps = new ArrayList<>();
        for (ExtractedTaskItem t : result.tasks()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", t.getTitle());
            m.put("description", t.getDescription());
            m.put("goal", t.getGoal());
            m.put("expected_result", t.getExpectedResult());
            m.put("priority", t.getPriority() != null ? t.getPriority() : "low");
            m.put("due_at", t.getDueAt());
            m.put("ai_confidence", t.getAiConfidence());
            m.put("ai_raw_text", t.getAiRawText());
            m.put("assigner_user_id", t.getAssignerUserId());
            m.put("assignee_user_id", t.getAssigneeUserId());
            taskMaps.add(m);
        }
        out.put("extracted_items", taskMaps);
        out.put("review_required", true);
        return out;
    }

    /** Used by AiPlatformController /process endpoint. */
    public MeetingOutput processMeeting(MeetingAnalyzeRequest request) {
        if (request == null) return MeetingOutput.builder().status("failed").build();
        MeetingAnalysisResult result = processAudio(
                request.getAudioPath(), request.getMeetingTitle());
        CleanedTranscript ct = CleanedTranscript.builder()
                .fullText(result.transcript())
                .build();
        MeetingMinutes minutes = MeetingMinutes.builder()
                .summary(result.summary())
                .build();
        return MeetingOutput.builder()
                .jobId(UUID.randomUUID().toString())
                .audioPath(request.getAudioPath())
                .status("completed")
                .processedAt(Instant.now())
                .transcript(ct)
                .minutes(minutes)
                .actionItems(List.of())
                .language("vi")
                .build();
    }

    // ── Result record ─────────────────────────────────────────────────────────

    public record MeetingAnalysisResult(String summary, String transcript, List<ExtractedTaskItem> tasks) {
        public static MeetingAnalysisResult empty() {
            return new MeetingAnalysisResult("", "", List.of());
        }
    }
}
