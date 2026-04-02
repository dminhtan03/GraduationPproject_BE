package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import com.finalProject.BookingMeetingRoom.model.entity.User;

import java.util.List;

public interface ChatHistoryService {
    String ensureSessionId(String sessionId);

    void log(User user, String sessionId, SenderType sender, String message);

    /**
     * Returns recent messages for a session (newest first). Used for lightweight context recall.
     */
    List<String> getRecentMessages(String sessionId, SenderType sender, int limit);
}
