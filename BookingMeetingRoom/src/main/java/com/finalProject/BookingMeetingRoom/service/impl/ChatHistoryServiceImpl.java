package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Chat_history;
import com.finalProject.BookingMeetingRoom.model.entity.Chatbot;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotDetailResponse;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotHistoryResponse;
import com.finalProject.BookingMeetingRoom.repository.AiRepository;
import com.finalProject.BookingMeetingRoom.repository.ChatHistoryRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private static final String DEFAULT_CHATBOT_ID = "DEFAULT_CHATBOT";

    private final ChatHistoryRepository chatHistoryRepository;
    private final AiRepository aiRepository;
    private final UserRepository userRepository;

    @Override
    public String createSession() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String ensureSessionId(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) return sessionId;
        return UUID.randomUUID().toString();
    }

    @Override
    @Transactional
    public void log(User user, String sessionId, SenderType sender, String message) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Skipping chat history log: missing sessionId");
            return;
        }
        if (message == null || message.isBlank()) return;

        Chatbot chatbot = getOrCreateDefaultChatbot();
        if (chatbot == null) {
            log.warn("Skipping chat history log: missing chatbot record");
            return;
        }

        Chat_history h = new Chat_history();
        h.setId(UUID.randomUUID().toString());
        h.setUser(user);
        h.setChatbot(chatbot);
        h.setSessionId(sessionId);
        h.setSender(sender);
        h.setMessage(message);

        chatHistoryRepository.save(h);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getRecentMessages(String sessionId, SenderType sender, int limit) {
        if (sessionId == null || sessionId.isBlank()) return List.of();
        if (sender == null) return List.of();
        if (limit <= 0) return List.of();

        // Repository method is fixed to top 10; cap by caller's limit.
        List<Chat_history> rows = chatHistoryRepository.findTop10BySessionIdAndSenderOrderByCreatedAtDesc(sessionId, sender);
        return rows.stream()
                .map(Chat_history::getMessage)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(limit)
                .toList();
    }

    @Override
    @Transactional
    public long deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "sessionId is required");
        }
        return chatHistoryRepository.deleteBySessionId(sessionId.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatbotHistoryResponse> getAllSessionsOfCurrentUser(Authentication authentication) {
        User user = requireCurrentUser(authentication);

        List<Chat_history> rows = chatHistoryRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
        if (rows.isEmpty()) return List.of();

        Map<String, ChatbotHistoryResponse.ChatbotHistoryResponseBuilder> grouped = new LinkedHashMap<>();

        for (Chat_history row : rows) {
            if (row == null || row.getSessionId() == null || row.getSessionId().isBlank()) continue;

            ChatbotHistoryResponse.ChatbotHistoryResponseBuilder b = grouped.get(row.getSessionId());
            if (b == null) {
                grouped.put(row.getSessionId(), ChatbotHistoryResponse.builder()
                        .sessionId(row.getSessionId())
                        .messageCount(1L)
                        .startedAt(row.getCreatedAt())
                        .lastMessageAt(row.getCreatedAt())
                        .lastSender(row.getSender())
                        .lastMessage(row.getMessage()));
            } else {
                ChatbotHistoryResponse current = b.build();
                long currentCount = current.getMessageCount() == null ? 0L : current.getMessageCount();

                LocalDateTime startedAt = current.getStartedAt();
                LocalDateTime rowCreatedAt = row.getCreatedAt();
                if (startedAt == null || (rowCreatedAt != null && rowCreatedAt.isBefore(startedAt))) {
                    startedAt = rowCreatedAt;
                }

                grouped.put(row.getSessionId(), ChatbotHistoryResponse.builder()
                        .sessionId(current.getSessionId())
                        .messageCount(currentCount + 1)
                        .startedAt(startedAt)
                        .lastMessageAt(current.getLastMessageAt())
                        .lastSender(current.getLastSender())
                        .lastMessage(current.getLastMessage()));
            }
        }

        return grouped.values().stream().map(ChatbotHistoryResponse.ChatbotHistoryResponseBuilder::build).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatbotDetailResponse getSessionDetailOfCurrentUser(String sessionId, Authentication authentication) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "sessionId is required");
        }

        User user = requireCurrentUser(authentication);
        List<Chat_history> rows = chatHistoryRepository.findBySessionIdAndUser_IdOrderByCreatedAtAsc(sessionId.trim(), user.getId());
        if (rows.isEmpty()) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Chat session not found");
        }

        LocalDateTime startedAt = rows.get(0).getCreatedAt();
        LocalDateTime lastMessageAt = rows.get(rows.size() - 1).getCreatedAt();

        List<ChatbotDetailResponse.ChatMessageItem> messages = rows.stream()
            .map(r -> ChatbotDetailResponse.ChatMessageItem.builder()
                        .id(r.getId())
                        .sender(r.getSender())
                        .message(r.getMessage())
                        .createdAt(r.getCreatedAt())
                        .build())
                .toList();

        Chat_history last = rows.get(rows.size() - 1);
        return ChatbotDetailResponse.builder()
                .sessionId(sessionId.trim())
                .messageCount((long) rows.size())
                .startedAt(startedAt)
                .lastMessageAt(lastMessageAt)
                .lastSender(last.getSender())
                .lastMessage(last.getMessage())
                .messages(messages)
                .build();
    }

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
    }

    private Chatbot getOrCreateDefaultChatbot() {
        return aiRepository.findFirstByActiveTrueOrderByCreatedAtAsc()
                .orElseGet(() -> {
                    try {
                        Chatbot bot = aiRepository.findById(DEFAULT_CHATBOT_ID).orElse(null);
                        if (bot != null) return bot;

                        Chatbot created = new Chatbot();
                        created.setId(DEFAULT_CHATBOT_ID);
                        created.setBotName("BookingMeetingRoom Bot");
                        created.setDescription("Default chatbot record (auto-created)");
                        created.setModelName("rule-based");
                        created.setActive(true);
                        return aiRepository.save(created);
                    } catch (Exception e) {
                        log.error("Failed to create default chatbot record", e);
                        return null;
                    }
                });
    }
}
