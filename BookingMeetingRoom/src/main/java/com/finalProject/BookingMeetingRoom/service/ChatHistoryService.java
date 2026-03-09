package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.entity.Chat_history;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatHistoryService {
    Chat_history save(Chat_history chat);
    Page<Chat_history> getByUser(String userEmail, Pageable pageable);
    List<Chat_history> getBySessionId(String sessionId);
}
