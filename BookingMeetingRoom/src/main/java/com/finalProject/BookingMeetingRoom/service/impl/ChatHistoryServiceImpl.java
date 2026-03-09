// Add ChatHistoryServiceImpl to save and query chat history
package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.model.entity.Chat_history;
import com.finalProject.BookingMeetingRoom.model.entity.Chatbot;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.repository.AiRepository;
import com.finalProject.BookingMeetingRoom.repository.ChatHistoryRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final AiRepository aiRepository;
    private final UserRepository userRepository;

    @Override
    public Chat_history save(Chat_history chat) {
        if (chat.getId() == null || chat.getId().isEmpty()) {
            chat.setId(UUID.randomUUID().toString());
        }

        if (chat.getChatbot() == null) {
            List<Chatbot> bots = aiRepository.findAll();
            if (!bots.isEmpty()) {
                chat.setChatbot(bots.get(0));
            }
        }

        return chatHistoryRepository.save(chat);
    }

    @Override
    public Page<Chat_history> getByUser(String userEmail, Pageable pageable) {
        return userRepository.findByEmail(userEmail)
                .map(user -> chatHistoryRepository.findByUserOrderByCreatedAtDesc(user, pageable))
                .orElseGet(() -> Page.empty(pageable));
    }

    @Override
    public List<Chat_history> getBySessionId(String sessionId) {
        return chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }
}

