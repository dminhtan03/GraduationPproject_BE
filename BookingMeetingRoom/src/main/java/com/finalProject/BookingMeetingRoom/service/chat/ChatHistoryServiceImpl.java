package com.finalProject.BookingMeetingRoom.service.chat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

import lombok.RequiredArgsConstructor;

@Service
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
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(message)) {
            return;
        }

        Chatbot chatbot = getOrCreateDefaultChatbot();
        if (chatbot == null) {
            return;
        }

        Chat_history row = new Chat_history();
        row.setId(UUID.randomUUID().toString());
        row.setUser(user);
        row.setChatbot(chatbot);
        row.setSessionId(sessionId.trim());
        row.setSender(sender);
        row.setMessage(message.trim());
        row.setCreatedAt(LocalDateTime.now());

        chatHistoryRepository.save(row);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getRecentMessages(String sessionId, SenderType sender, int limit) {
        if (!StringUtils.hasText(sessionId) || sender == null || limit <= 0) {
            return List.of();
        }

        List<Chat_history> rows = chatHistoryRepository.findTop10BySessionIdAndSenderOrderByCreatedAtDesc(
                sessionId.trim(), sender);

        return rows.stream()
                .map(Chat_history::getMessage)
                .map(msg -> msg == null ? "" : msg.trim())
                .filter(StringUtils::hasText)
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public long deleteSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED);
        }
        return chatHistoryRepository.deleteBySessionId(sessionId.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatbotHistoryResponse> getAllSessionsOfCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

        List<Chat_history> rows = chatHistoryRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<String, SessionAggregate> sessions = new LinkedHashMap<>();
        for (Chat_history row : rows) {
            String sessionId = row.getSessionId();
            SessionAggregate agg = sessions.computeIfAbsent(sessionId, id -> new SessionAggregate());
            agg.sessionId = sessionId;
            agg.messageCount++;
            agg.startedAt = agg.startedAt == null ? row.getCreatedAt() : min(agg.startedAt, row.getCreatedAt());
            if (agg.lastMessageAt == null || row.getCreatedAt().isAfter(agg.lastMessageAt)) {
                agg.lastMessageAt = row.getCreatedAt();
                agg.lastMessage = row.getMessage();
                agg.lastSender = row.getSender();
            }
        }

        return sessions.values().stream()
                .sorted(Comparator.comparing(SessionAggregate::getLastMessageAt).reversed())
                .map(SessionAggregate::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ChatbotDetailResponse getSessionDetailOfCurrentUser(String sessionId, Authentication authentication) {
        if (!StringUtils.hasText(sessionId)) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED);
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

        List<Chat_history> rows = chatHistoryRepository.findBySessionIdAndUser_IdOrderByCreatedAtAsc(
                sessionId.trim(), user.getId());
        if (rows.isEmpty()) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED);
        }

        Chat_history first = rows.get(0);
        Chat_history last = rows.get(rows.size() - 1);

        List<ChatbotDetailResponse.ChatMessageItem> items = rows.stream()
                .map(row -> ChatbotDetailResponse.ChatMessageItem.builder()
                        .id(row.getId())
                        .sender(row.getSender())
                        .message(row.getMessage())
                        .createdAt(row.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ChatbotDetailResponse.builder()
                .sessionId(sessionId.trim())
                .messageCount((long) rows.size())
                .startedAt(first.getCreatedAt())
                .lastMessageAt(last.getCreatedAt())
                .lastSender(last.getSender())
                .lastMessage(last.getMessage())
                .messages(items)
                .build();
    }

    private Chatbot getOrCreateDefaultChatbot() {
        return aiRepository.findFirstByActiveTrueOrderByCreatedAtAsc()
                .or(() -> aiRepository.findById(DEFAULT_CHATBOT_ID))
                .orElseGet(() -> {
                    try {
                        Chatbot bot = new Chatbot();
                        bot.setId(DEFAULT_CHATBOT_ID);
                        bot.setBotName("Default Chatbot");
                        bot.setDescription("Default chatbot");
                        bot.setModelName("unknown");
                        bot.setActive(true);
                        return aiRepository.save(bot);
                    } catch (Exception e) {
                        return null;
                    }
                });
    }

    private LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static class SessionAggregate {
        private String sessionId;
        private long messageCount;
        private LocalDateTime startedAt;
        private LocalDateTime lastMessageAt;
        private SenderType lastSender;
        private String lastMessage;

        private LocalDateTime getLastMessageAt() {
            return lastMessageAt;
        }

        private ChatbotHistoryResponse toResponse() {
            return ChatbotHistoryResponse.builder()
                    .sessionId(sessionId)
                    .messageCount(messageCount)
                    .startedAt(startedAt)
                    .lastMessageAt(lastMessageAt)
                    .lastSender(lastSender)
                    .lastMessage(lastMessage)
                    .build();
        }
    }
}
