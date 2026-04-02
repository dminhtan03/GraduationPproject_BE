package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import com.finalProject.BookingMeetingRoom.model.entity.Chat_history;
import com.finalProject.BookingMeetingRoom.model.entity.Chatbot;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.repository.AiRepository;
import com.finalProject.BookingMeetingRoom.repository.ChatHistoryRepository;
import com.finalProject.BookingMeetingRoom.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private static final String DEFAULT_CHATBOT_ID = "DEFAULT_CHATBOT";

    private final ChatHistoryRepository chatHistoryRepository;
    private final AiRepository aiRepository;

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
