package com.finalProject.BookingMeetingRoom.controller.ai;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.ChatbotMessageRequest;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotMessageResponse;
import com.finalProject.BookingMeetingRoom.service.ChatHistoryService;
import com.finalProject.BookingMeetingRoom.service.ChatbotService;
import com.finalProject.BookingMeetingRoom.service.SpeechToTextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final SpeechToTextService speechToTextService;
    private final ChatHistoryService chatHistoryService;

    @PostMapping("/session")
    public ResponseEntity<?> createSession() {
        String sessionId = chatHistoryService.createSession();
        return ResponseEntity.ok(Response.ofSucceeded(Map.of("sessionId", sessionId)));
    }

    @PostMapping("/message")
    public ResponseEntity<?> message(@RequestBody @Valid ChatbotMessageRequest request, Authentication authentication) {
        ChatbotMessageResponse result = chatbotService.handleMessage(request, authentication);
        return ResponseEntity.ok()
                .header("X-Chat-Session-Id", result != null ? result.getSessionId() : "")
                .body(Response.ofSucceeded(result));
    }

    /**
     * Voice booking endpoint.
     * - If `transcript` is provided, it will be used directly.
     * - Otherwise, `audio` will be transcribed by {@link SpeechToTextService}.
     */
    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> voice(
            @RequestPart(value = "audio", required = false) MultipartFile audio,
            @RequestPart(value = "transcript", required = false) String transcript,
            @RequestPart(value = "sessionId", required = false) String sessionId,
            @RequestPart(value = "language", required = false) String language,
            Authentication authentication
    ) {
        String finalTranscript = (transcript != null && !transcript.isBlank())
                ? transcript
                : (audio != null ? speechToTextService.transcribe(audio, language) : null);

        ChatbotMessageRequest req = ChatbotMessageRequest.builder()
                .message(finalTranscript)
                .sessionId(sessionId)
                .build();

        ChatbotMessageResponse result = chatbotService.handleMessage(req, authentication);
        return ResponseEntity.ok()
            .header("X-Chat-Session-Id", result != null ? result.getSessionId() : "")
            .body(Response.ofSucceeded(result));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<?> deleteSession(@PathVariable String sessionId) {
        long deleted = chatHistoryService.deleteSession(sessionId);
        return ResponseEntity.ok(Response.ofSucceeded(Map.of(
                "sessionId", sessionId,
                "deletedMessages", deleted
        )));
    }

    @GetMapping("/history")
    public ResponseEntity<?> getMyChatHistories(Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(chatHistoryService.getAllSessionsOfCurrentUser(authentication)));
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<?> getChatHistoryDetail(@PathVariable String sessionId, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(chatHistoryService.getSessionDetailOfCurrentUser(sessionId, authentication)));
    }
}