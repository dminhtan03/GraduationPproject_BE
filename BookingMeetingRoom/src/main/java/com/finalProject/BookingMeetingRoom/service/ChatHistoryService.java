package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotDetailResponse;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotHistoryResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ChatHistoryService {
    String createSession();

    String ensureSessionId(String sessionId);

    void log(User user, String sessionId, SenderType sender, String message);

    /**
     * Returns recent messages for a session (newest first). Used for lightweight context recall.
     */
    List<String> getRecentMessages(String sessionId, SenderType sender, int limit);

    long deleteSession(String sessionId);

    List<ChatbotHistoryResponse> getAllSessionsOfCurrentUser(Authentication authentication);

    ChatbotDetailResponse getSessionDetailOfCurrentUser(String sessionId, Authentication authentication);
}
