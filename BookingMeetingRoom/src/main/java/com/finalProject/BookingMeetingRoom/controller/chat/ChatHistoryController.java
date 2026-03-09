package com.finalProject.BookingMeetingRoom.controller.chat;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.entity.Chat_history;
import com.finalProject.BookingMeetingRoom.model.request.SaveChatRequest;
import com.finalProject.BookingMeetingRoom.model.response.ChatHistoryResponse;
import com.finalProject.BookingMeetingRoom.service.ChatHistoryService;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;
    private final UserRepository userRepository;

    @GetMapping("/history/session/{sessionId}")
    public ResponseEntity<?> getBySession(@PathVariable String sessionId) {
        List<Chat_history> list = chatHistoryService.getBySessionId(sessionId);
        List<ChatHistoryResponse> resp = list.stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(Response.ofSucceeded(resp));
    }

    @GetMapping("/history/me")
    public ResponseEntity<?> getMyHistory(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.badRequest().body(Response.ofSucceeded("No authenticated user"));
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<Chat_history> pageResult = chatHistoryService.getByUser(authentication.getName(), pageable);
        List<ChatHistoryResponse> content = pageResult.getContent().stream().map(this::toResponse).collect(Collectors.toList());
        Page<ChatHistoryResponse> out = new PageImpl<>(content, pageable, pageResult.getTotalElements());
        return ResponseEntity.ok(Response.ofSucceeded(out));
    }

    @PostMapping("/history")
    public ResponseEntity<?> saveMessage(@RequestBody SaveChatRequest request, Authentication authentication) {
        Chat_history chat = new Chat_history();
        chat.setSessionId(request.getSessionId() == null || request.getSessionId().isEmpty() ? java.util.UUID.randomUUID().toString() : request.getSessionId());
        chat.setSender(request.getSender());
        chat.setMessage(request.getMessage());
        if (authentication != null && authentication.getName() != null) {
            userRepository.findByEmail(authentication.getName()).ifPresent(chat::setUser);
        }
        Chat_history saved = chatHistoryService.save(chat);
        return ResponseEntity.ok(Response.ofSucceeded(toResponse(saved)));
    }

    private ChatHistoryResponse toResponse(Chat_history e) {
        return ChatHistoryResponse.builder()
                .id(e.getId())
                .sessionId(e.getSessionId())
                .sender(e.getSender())
                .message(e.getMessage())
                .createdAt(e.getCreatedAt())
                .chatbotId(e.getChatbot() != null ? e.getChatbot().getId() : null)
                .chatbotName(e.getChatbot() != null ? e.getChatbot().getBotName() : null)
                .userId(e.getUser() != null ? e.getUser().getId() : null)
                .userEmail(e.getUser() != null && e.getUser().getUserInfo() != null ? e.getUser().getUserInfo().getEmail() : null)
                .build();
    }
}

