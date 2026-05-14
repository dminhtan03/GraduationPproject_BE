package com.finalProject.BookingMeetingRoom.controller.aiplatform;

import com.finalProject.BookingMeetingRoom.model.request.aiplatform.AiPlatformChatRequest;
import com.finalProject.BookingMeetingRoom.model.request.aiplatform.MeetingAnalyzeRequest;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.AiPlatformChatResponse;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.ExtractedTaskItem;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.JobResponse;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.MeetingSummaryResponse;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.MeetingOutput;
import com.finalProject.BookingMeetingRoom.common.config.AiPlatformProperties;
import com.finalProject.BookingMeetingRoom.service.aiplatform.AiMeetingService;
import com.finalProject.BookingMeetingRoom.service.aiplatform.AiPlatformChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AiPlatformController {

    private final AiPlatformChatService chatService;
    private final AiMeetingService meetingService;
    private final AiPlatformProperties aiPlatformProperties;

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", aiPlatformProperties.getAppName(),
                "version", aiPlatformProperties.getAppVersion()
        ));
    }

    @PostMapping("/api/v1/chat")
    public ResponseEntity<AiPlatformChatResponse> chat(
            @RequestBody AiPlatformChatRequest request,
            Authentication authentication
    ) {
        AiPlatformChatResponse response = chatService.handleMessage(
                request.getSessionId(),
                request.getMessage(),
                authentication
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/meeting/analyze")
    public ResponseEntity<List<ExtractedTaskItem>> analyzeMeeting(
            @RequestBody MeetingAnalyzeRequest request
    ) {
        return ResponseEntity.ok(meetingService.analyze(request));
    }

    @PostMapping("/api/v1/meeting/summarize")
    public ResponseEntity<MeetingSummaryResponse> summarizeMeeting(
            @RequestBody MeetingAnalyzeRequest request
    ) {
        return ResponseEntity.ok(meetingService.summarize(request));
    }

    @PostMapping("/api/v1/meeting/process")
    public ResponseEntity<MeetingOutput> processMeeting(
            @RequestBody MeetingAnalyzeRequest request
    ) {
        return ResponseEntity.ok(meetingService.processMeeting(request));
    }

    @GetMapping("/api/v1/jobs/{jobId}")
    public ResponseEntity<JobResponse> getJob(@PathVariable String jobId) {
        JobResponse response = JobResponse.builder()
                .jobId(jobId)
                .status("completed")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .result(Map.of("message", "Mock job result"))
                .progress(1.0)
                .build();
        return ResponseEntity.ok(response);
    }
}
