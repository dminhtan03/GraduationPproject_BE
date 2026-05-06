package com.finalProject.BookingMeetingRoom.controller.ai;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.AiChatRequest;
import com.finalProject.BookingMeetingRoom.model.request.ChatbotMessageRequest;
import com.finalProject.BookingMeetingRoom.service.AiService;
import com.finalProject.BookingMeetingRoom.service.chat.ChatHistoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final ChatHistoryService chatHistoryService;

    @PostMapping("/message")
    public ResponseEntity<?> chat(@RequestBody @Valid ChatbotMessageRequest request, Authentication authentication) {
        AiChatRequest aiRequest = AiChatRequest.builder()
                .message(request.getMessage())
                .sessionId(request.getSessionId())
                .build();
        return ResponseEntity.ok(Response.ofSucceeded(aiService.chat(aiRequest, authentication)));
    }

    @PostMapping("/session")
    public ResponseEntity<?> createSession() {
        String sessionId = chatHistoryService.createSession();
        return ResponseEntity.ok(Response.ofSucceeded(Map.of("sessionId", sessionId)));
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(chatHistoryService.getAllSessionsOfCurrentUser(authentication)));
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<?> getSessionDetail(@PathVariable String sessionId, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(chatHistoryService.getSessionDetailOfCurrentUser(sessionId, authentication)));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<?> deleteSession(@PathVariable String sessionId) {
        long deleted = chatHistoryService.deleteSession(sessionId);
        return ResponseEntity.ok(Response.ofSucceeded(deleted));
    }
}
