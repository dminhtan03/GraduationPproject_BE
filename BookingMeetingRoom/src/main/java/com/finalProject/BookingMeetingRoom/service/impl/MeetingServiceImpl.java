package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.ReviewStatus;
import com.finalProject.BookingMeetingRoom.common.enums.TaskPriority;
import com.finalProject.BookingMeetingRoom.common.enums.AssignmentStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.*;
import com.finalProject.BookingMeetingRoom.model.request.ApproveDraftRequest;
import com.finalProject.BookingMeetingRoom.model.request.MeetingRequest;
import com.finalProject.BookingMeetingRoom.model.request.TaskRequest;
import com.finalProject.BookingMeetingRoom.model.request.AssignTaskRequest;
import com.finalProject.BookingMeetingRoom.model.response.AssignmentDraftResponse;
import com.finalProject.BookingMeetingRoom.model.response.MeetingResponse;
import com.finalProject.BookingMeetingRoom.model.response.ProcessRecordingResponse;
import com.finalProject.BookingMeetingRoom.repository.*;
import com.finalProject.BookingMeetingRoom.service.MeetingService;
import com.finalProject.BookingMeetingRoom.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class MeetingServiceImpl implements MeetingService {

    private final MeetingRepository meetingRepository;
    private final TaskAssignmentDraftRepository draftRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final TaskService taskService;
    private final TaskAssignmentRepository assignmentRepository;

    @Value("${ai.platform.url:http://localhost:8001}")
    private String aiPlatformUrl;

    @Value("${audio.dir:./audio}")
    private String audioDir;

    private User resolveUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
    }

    private MeetingResponse toResponse(Meeting m) {
        String creatorName = m.getCreatedBy().getUserInfo() != null
                ? (m.getCreatedBy().getUserInfo().getFirstName() + " " + m.getCreatedBy().getUserInfo().getLastName()).trim()
                : m.getCreatedBy().getUsername();
        return MeetingResponse.builder()
                .id(m.getId())
                .title(m.getTitle())
                .reservationId(m.getReservation() != null ? m.getReservation().getId() : null)
                .status(m.getStatus())
                .transcript(m.getTranscript())
                .summary(m.getSummary())
                .minutesJson(m.getMinutesJson())
                .audioPath(m.getAudioPath())
                .jobId(m.getJobId())
                .durationSeconds(m.getDurationSeconds())
                .speakerCount(m.getSpeakerCount())
                .language(m.getLanguage())
                .createdById(m.getCreatedBy().getId())
                .createdByName(creatorName)
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

    private AssignmentDraftResponse draftToResponse(TaskAssignmentDraft d) {
        List<String> issues = d.getReviewIssues() != null && !d.getReviewIssues().isBlank()
                ? Arrays.asList(d.getReviewIssues().split(","))
                : List.of();
        return AssignmentDraftResponse.builder()
                .id(d.getId())
                .meetingId(d.getMeeting().getId())
                .title(d.getTitle())
                .description(d.getDescription())
                .goal(d.getGoal())
                .expectedResult(d.getExpectedResult())
                .priority(d.getPriority())
                .dueAt(d.getDueAt())
                .assignerUserId(d.getAssignerUserId())
                .assigneeUserId(d.getAssigneeUserId())
                .aiConfidence(d.getAiConfidence())
                .aiRawText(d.getAiRawText())
                .reviewStatus(d.getReviewStatus().name())
                .reviewIssues(issues)
                .createdTaskId(d.getCreatedTask() != null ? d.getCreatedTask().getId() : null)
                .createdAt(d.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public MeetingResponse createMeeting(MeetingRequest request, Authentication auth) {
        User creator = resolveUser(auth);
        Meeting meeting = new Meeting();
        meeting.setTitle(request.getTitle() != null ? request.getTitle() : "Meeting");
        meeting.setCreatedBy(creator);
        meeting.setLanguage(request.getLanguage() != null ? request.getLanguage() : "vi");
        meeting.setStatus("pending");

        String resId = request.getReservation_id() != null ? request.getReservation_id() : request.getReservationId();
        if (resId != null) {
            reservationRepository.findById(resId).ifPresent(meeting::setReservation);
        }
        meetingRepository.save(meeting);
        return toResponse(meeting);
    }

    @Override
    public List<MeetingResponse> listMeetings(Authentication auth) {
        User user = resolveUser(auth);
        return meetingRepository.findByCreatedBy_IdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public MeetingResponse getMeeting(String meetingId, Authentication auth) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ResponseCode.MEETING_NOT_FOUND));
        return toResponse(meeting);
    }

    @Override
    @Transactional
    public MeetingResponse uploadRecording(String meetingId, String filePath, Authentication auth) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ResponseCode.MEETING_NOT_FOUND));
        meeting.setAudioPath(filePath);
        meeting.setStatus("pending");
        meetingRepository.save(meeting);
        return toResponse(meeting);
    }

    @Override
    @Transactional
    public List<AssignmentDraftResponse> aiExtractAssignments(String meetingId, String audioPath, Authentication auth) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ResponseCode.MEETING_NOT_FOUND));

        // Call ai-platform pipeline
        String effectiveAudioPath = audioPath != null ? audioPath : meeting.getAudioPath();
        if (effectiveAudioPath == null || effectiveAudioPath.isBlank()) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "audio_path is required");
        }

        meeting.setStatus("running");
        meetingRepository.save(meeting);

        try {
            RestTemplate rt = new RestTemplate();
            Map<String, Object> body = new HashMap<>();
            body.put("audio_path", effectiveAudioPath);
            body.put("meeting_title", meeting.getTitle());
            body.put("language", meeting.getLanguage());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> resp = rt.exchange(
                    aiPlatformUrl + "/process-meeting",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            Map<String, Object> result = resp.getBody();
            if (result != null) {
                persistPipelineResult(meeting, result);
            }
        } catch (Exception ex) {
            log.error("AI platform call failed: {}", ex.getMessage());
            meeting.setStatus("failed");
            meetingRepository.save(meeting);
        }

        return draftRepository.findByMeeting_IdOrderByCreatedAtAsc(meetingId)
                .stream().map(this::draftToResponse).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private void persistPipelineResult(Meeting meeting, Map<String, Object> result) {
        // Save pipeline output to meeting
        Map<String, Object> pipeline = (Map<String, Object>) result.getOrDefault("pipeline", result);
        String status = (String) pipeline.getOrDefault("status", "completed");
        meeting.setStatus(status);
        meeting.setJobId((String) pipeline.get("job_id"));

        Map<String, Object> transcript = (Map<String, Object>) pipeline.get("transcript");
        if (transcript != null) {
            meeting.setTranscript((String) transcript.getOrDefault("full_text", null));
            Object dur = transcript.get("duration_seconds");
            if (dur instanceof Number) meeting.setDurationSeconds(((Number) dur).doubleValue());
            Object spk = transcript.get("speaker_count");
            if (spk instanceof Number) meeting.setSpeakerCount(((Number) spk).intValue());
        }

        Map<String, Object> minutes = (Map<String, Object>) pipeline.get("minutes");
        if (minutes != null) {
            meeting.setSummary((String) minutes.getOrDefault("summary", null));
            meeting.setMinutesJson(minutes.toString());
        }

        meetingRepository.save(meeting);

        // Save draft items
        List<Map<String, Object>> extractedItems = (List<Map<String, Object>>) result.getOrDefault("extracted_items", List.of());
        for (Map<String, Object> item : extractedItems) {
            TaskAssignmentDraft draft = new TaskAssignmentDraft();
            draft.setMeeting(meeting);
            draft.setTitle((String) item.getOrDefault("title", "Untitled task"));
            draft.setDescription((String) item.get("description"));
            draft.setGoal((String) item.get("goal"));
            draft.setExpectedResult((String) item.get("expected_result"));
            draft.setPriority(((String) item.getOrDefault("priority", "medium")).toUpperCase());
            draft.setAiConfidence(item.get("ai_confidence") instanceof Number
                    ? ((Number) item.get("ai_confidence")).doubleValue() : null);
            draft.setAiRawText((String) item.get("ai_raw_text"));
            draft.setAssignerUserId((String) item.get("assigner_user_id"));
            draft.setAssigneeUserId((String) item.get("assignee_user_id"));

            Object dueAt = item.get("due_at");
            if (dueAt instanceof String s && !s.isBlank()) {
                try {
                    String normalized = s.endsWith("Z") ? s.replace("Z", "") : s;
                    draft.setDueAt(LocalDateTime.parse(normalized.length() == 10
                            ? normalized + "T00:00:00" : normalized.substring(0, 19)));
                } catch (DateTimeParseException ex) { log.warn("Cannot parse due_at: {}", dueAt); }
            }

            // Review status
            Double conf = draft.getAiConfidence();
            List<String> issues = new ArrayList<>();
            if (conf != null && conf < 0.85) issues.add("low_confidence");
            if (draft.getAssignerUserId() == null) issues.add("missing_assigner_user_id");
            if (draft.getAssigneeUserId() == null) issues.add("missing_assignee_user_id");
            draft.setReviewStatus(issues.isEmpty() ? ReviewStatus.APPROVED : ReviewStatus.PENDING);
            draft.setReviewIssues(issues.isEmpty() ? null : String.join(",", issues));
            draftRepository.save(draft);
        }
    }

    @Override
    public List<AssignmentDraftResponse> listDrafts(String meetingId, Authentication auth) {
        return draftRepository.findByMeeting_IdOrderByCreatedAtAsc(meetingId)
                .stream().map(this::draftToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AssignmentDraftResponse updateDraft(String draftId, ApproveDraftRequest request, Authentication auth) {
        TaskAssignmentDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Draft not found"));

        String assignerId = request.getAssigner_user_id() != null ? request.getAssigner_user_id() : request.getAssignerUserId();
        String assigneeId = request.getAssignee_user_id() != null ? request.getAssignee_user_id() : request.getAssigneeUserId();

        if (assignerId != null) draft.setAssignerUserId(assignerId);
        if (assigneeId != null) draft.setAssigneeUserId(assigneeId);

        // Re-evaluate issues
        List<String> issues = new ArrayList<>();
        if (draft.getAiConfidence() != null && draft.getAiConfidence() < 0.85) issues.add("low_confidence");
        if (draft.getAssignerUserId() == null) issues.add("missing_assigner_user_id");
        if (draft.getAssigneeUserId() == null) issues.add("missing_assignee_user_id");
        draft.setReviewIssues(issues.isEmpty() ? null : String.join(",", issues));
        if (draft.getReviewStatus() == ReviewStatus.PENDING && issues.stream()
                .noneMatch(i -> i.contains("missing_assigner") || i.contains("missing_assignee"))) {
            draft.setReviewStatus(ReviewStatus.APPROVED);
        }

        draftRepository.save(draft);
        return draftToResponse(draft);
    }

    @Override
    @Transactional
    public Map<String, String> approveDraft(String draftId, Authentication auth) {
        User approver = resolveUser(auth);
        TaskAssignmentDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Draft not found"));

        // Create task
        TaskRequest taskReq = new TaskRequest();
        taskReq.setTitle(draft.getTitle());
        taskReq.setDescription(draft.getDescription());
        taskReq.setGoal(draft.getGoal());
        taskReq.setExpectedResult(draft.getExpectedResult());
        taskReq.setPriority(draft.getPriority());
        taskReq.setMeetingId(draft.getMeeting().getId());
        if (draft.getDueAt() != null) taskReq.setDueAt(draft.getDueAt().toString());
        com.finalProject.BookingMeetingRoom.model.response.TaskResponse createdTask = taskService.createTask(taskReq, auth);

        // Assign task
        if (draft.getAssigneeUserId() != null) {
            AssignTaskRequest assignReq = new AssignTaskRequest();
            assignReq.setAssigneeId(draft.getAssigneeUserId());
            assignReq.setAssignerId(draft.getAssignerUserId() != null ? draft.getAssignerUserId() : approver.getId());
            taskService.assignTask(createdTask.getId(), assignReq, auth);
        }

        // Update draft with created task reference
        com.finalProject.BookingMeetingRoom.model.entity.Task taskEntity =
                new com.finalProject.BookingMeetingRoom.model.entity.Task();
        taskEntity.setId(createdTask.getId());
        draft.setCreatedTask(taskEntity);
        draft.setReviewStatus(ReviewStatus.APPROVED);
        draftRepository.save(draft);

        Map<String, String> result = new HashMap<>();
        result.put("created_task_id", createdTask.getId());
        return result;
    }

    // ── Process recording (upload + AI analyze + AI summarize) ────────────────

    @Override
    public ProcessRecordingResponse processRecording(MultipartFile audioFile,
                                                      String reservationId,
                                                      String title,
                                                      Authentication auth) {
        User creator = resolveUser(auth);

        // 1. Save audio file to disk first (outside transaction)
        String savedPath = saveAudioFile(audioFile);
        log.info("Audio saved to: {}", savedPath);

        // 2. Persist Meeting entity in its own transaction
        Meeting meeting = createMeetingEntity(creator, reservationId, title, savedPath);
        log.info("Meeting created: {}", meeting.getId());

        // 3. Call ai-platform (no transaction open)
        String summary = "";
        String transcript = "";
        List<ProcessRecordingResponse.ExtractedTask> tasks = new ArrayList<>();

        RestTemplate rt = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("meeting_title", meeting.getTitle());
        body.put("audio_path", savedPath);
        body.put("language", "vi");

        try {
            ResponseEntity<Map<String, Object>> sumResp = rt.exchange(
                    aiPlatformUrl + "/api/v1/meeting/summarize",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {}
            );
            Map<String, Object> sumBody = sumResp.getBody();
            if (sumBody != null) {
                summary = String.valueOf(sumBody.getOrDefault("summary", ""));
                transcript = String.valueOf(sumBody.getOrDefault("transcript", ""));
            }
            log.info("AI summarize done, summary length={}", summary.length());
        } catch (Exception ex) {
            log.warn("AI summarize call failed (non-fatal): {}", ex.getMessage());
        }

        try {
            ResponseEntity<List<Map<String, Object>>> analyzeResp = rt.exchange(
                    aiPlatformUrl + "/api/v1/meeting/analyze",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {}
            );
            List<Map<String, Object>> items = analyzeResp.getBody();
            if (items != null) {
                for (Map<String, Object> item : items) {
                    Object titleObj = item.get("title");
                    if (titleObj == null) continue;
                    String t = titleObj.toString().trim();
                    if (t.isBlank()) continue;
                    Object priObj = item.getOrDefault("priority", "MEDIUM");
                    tasks.add(ProcessRecordingResponse.ExtractedTask.builder()
                            .title(t)
                            .description(item.get("description") != null ? item.get("description").toString() : null)
                            .goal(item.get("goal") != null ? item.get("goal").toString() : null)
                            .expectedResult(item.get("expected_result") != null ? item.get("expected_result").toString() : null)
                            .priority(priObj.toString().toUpperCase())
                            .dueAt(item.get("due_at") != null ? item.get("due_at").toString() : null)
                            .aiConfidence(item.get("ai_confidence") instanceof Number n ? n.doubleValue() : null)
                            .build());
                }
            }
            log.info("AI analyze done, {} tasks extracted", tasks.size());
        } catch (Exception ex) {
            log.warn("AI analyze call failed (non-fatal): {}", ex.getMessage());
        }

        // 4. Persist results in its own transaction
        updateMeetingResult(meeting.getId(), summary, transcript);

        return ProcessRecordingResponse.builder()
                .meetingId(meeting.getId())
                .summary(summary)
                .transcript(transcript)
                .tasks(tasks)
                .build();
    }

    @Transactional
    protected Meeting createMeetingEntity(User creator, String reservationId, String title, String audioPath) {
        Meeting meeting = new Meeting();
        meeting.setTitle(title != null && !title.isBlank() ? title : "Meeting");
        meeting.setCreatedBy(creator);
        meeting.setAudioPath(audioPath);
        meeting.setStatus("processing");
        meeting.setLanguage("vi");
        if (reservationId != null && !reservationId.isBlank()) {
            reservationRepository.findById(reservationId).ifPresent(meeting::setReservation);
        }
        return meetingRepository.save(meeting);
    }

    @Transactional
    protected void updateMeetingResult(String meetingId, String summary, String transcript) {
        meetingRepository.findById(meetingId).ifPresent(m -> {
            m.setSummary(summary.isBlank() ? null : summary);
            m.setTranscript(transcript.isBlank() ? null : transcript);
            m.setStatus("completed");
            meetingRepository.save(m);
        });
    }

    private String saveAudioFile(MultipartFile file) {
        try {
            Path dir = Paths.get(audioDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String original = file.getOriginalFilename();
            String ext = ".webm";
            if (original != null && original.lastIndexOf('.') > 0) {
                ext = original.substring(original.lastIndexOf('.'));
            } else if (file.getContentType() != null) {
                String ct = file.getContentType();
                if (ct.contains("ogg")) ext = ".ogg";
                else if (ct.contains("mp4") || ct.contains("mpeg")) ext = ".mp4";
                else if (ct.contains("wav")) ext = ".wav";
            }
            String filename = UUID.randomUUID() + ext;
            Path dest = dir.resolve(filename);
            // Use stream copy — reliable on all platforms / all Spring versions
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Saved audio: {} ({} bytes)", dest, Files.size(dest));
            return dest.toAbsolutePath().toString();
        } catch (Exception e) {
            log.error("Failed to save audio file: {}", e.getMessage(), e);
            throw new CustomException(ResponseCode.SYSTEM, "Failed to save audio: " + e.getMessage());
        }
    }
}
