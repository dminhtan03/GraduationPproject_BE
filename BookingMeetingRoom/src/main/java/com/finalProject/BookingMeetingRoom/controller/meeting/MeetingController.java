package com.finalProject.BookingMeetingRoom.controller.meeting;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.ApproveDraftRequest;
import com.finalProject.BookingMeetingRoom.model.request.MeetingRequest;
import com.finalProject.BookingMeetingRoom.service.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Meeting endpoints.
 * Base URL: /api/v1/meetings
 * Also handles /api/v1/assignment-drafts/{draftId}/...
 */
@RestController
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    /** POST /api/v1/meetings */
    @PostMapping("/api/v1/meetings")
    public ResponseEntity<?> createMeeting(@RequestBody MeetingRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ofSucceeded(meetingService.createMeeting(request, auth)));
    }

    /** GET /api/v1/meetings */
    @GetMapping("/api/v1/meetings")
    public ResponseEntity<?> listMeetings(Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(meetingService.listMeetings(auth)));
    }

    /** GET /api/v1/meetings/{meetingId} */
    @GetMapping("/api/v1/meetings/{meetingId}")
    public ResponseEntity<?> getMeeting(@PathVariable String meetingId, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(meetingService.getMeeting(meetingId, auth)));
    }

    /** POST /api/v1/meetings/{meetingId}/recording — upload recording path */
    @PostMapping("/api/v1/meetings/{meetingId}/recording")
    public ResponseEntity<?> uploadRecording(@PathVariable String meetingId,
                                              @RequestBody Map<String, String> body, Authentication auth) {
        String filePath = body.getOrDefault("file_path", body.get("filePath"));
        return ResponseEntity.ok(Response.ofSucceeded(meetingService.uploadRecording(meetingId, filePath, auth)));
    }

    /** POST /api/v1/meetings/{meetingId}/ai-extract-assignments — trigger AI extraction */
    @PostMapping("/api/v1/meetings/{meetingId}/ai-extract-assignments")
    public ResponseEntity<?> aiExtractAssignments(@PathVariable String meetingId,
                                                   @RequestBody(required = false) Map<String, String> body,
                                                   Authentication auth) {
        String audioPath = body != null ? body.getOrDefault("audio_path", body.get("audioPath")) : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ofSucceeded(
                        Map.of("drafts", meetingService.aiExtractAssignments(meetingId, audioPath, auth))
                ));
    }

    /** GET /api/v1/meetings/{meetingId}/assignment-drafts */
    @GetMapping("/api/v1/meetings/{meetingId}/assignment-drafts")
    public ResponseEntity<?> listDrafts(@PathVariable String meetingId, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(meetingService.listDrafts(meetingId, auth)));
    }

    /** PATCH /api/v1/assignment-drafts/{draftId} — fill missing fields */
    @PatchMapping("/api/v1/assignment-drafts/{draftId}")
    public ResponseEntity<?> updateDraft(@PathVariable String draftId,
                                          @RequestBody ApproveDraftRequest request, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(meetingService.updateDraft(draftId, request, auth)));
    }

    /** POST /api/v1/assignment-drafts/{draftId}/approve
     *  Body (optional): { "taskId": "..." }
     *  If taskId provided → just link draft to existing task (no new task created).
     *  If taskId absent  → create a new task from draft data (original behaviour).
     */
    @PostMapping("/api/v1/assignment-drafts/{draftId}/approve")
    public ResponseEntity<?> approveDraft(@PathVariable String draftId,
                                           @RequestBody(required = false) java.util.Map<String, String> body,
                                           Authentication auth) {
        String taskId = body != null ? body.get("taskId") : null;
        return ResponseEntity.ok(Response.ofSucceeded(meetingService.approveDraft(draftId, taskId, auth)));
    }

    /** GET /api/v1/meetings/by-reservation/{reservationId} — lấy meeting + tasks theo đơn đặt phòng */
    @GetMapping("/api/v1/meetings/by-reservation/{reservationId}")
    public ResponseEntity<?> getMeetingByReservation(@PathVariable String reservationId) {
        var result = meetingService.getMeetingByReservation(reservationId);
        if (result == null) return ResponseEntity.ok(Response.ofSucceeded(null));
        return ResponseEntity.ok(Response.ofSucceeded(result));
    }

    /**
     * POST /api/v1/meetings/process-recording
     * Multipart: audio file + reservationId + title
     * Saves audio, creates Meeting, calls ai-platform, returns summary + tasks.
     */
    @PostMapping(value = "/api/v1/meetings/process-recording", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> processRecording(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(required = false) String reservationId,
            @RequestParam(required = false) String title,
            Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(
                meetingService.processRecording(audioFile, reservationId, title, auth)));
    }
}
