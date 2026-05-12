package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.Chat_history;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import com.finalProject.BookingMeetingRoom.common.enums.SenderType;

public interface ChatHistoryRepository extends JpaRepository<Chat_history, String> {
    List<Chat_history> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<Chat_history> findBySessionIdAndUser_IdOrderByCreatedAtAsc(String sessionId, String userId);

    List<Chat_history> findByUser_IdOrderByCreatedAtDesc(String userId);

    List<Chat_history> findTop10BySessionIdAndSenderOrderByCreatedAtDesc(String sessionId, SenderType sender);

    long deleteBySessionId(String sessionId);
}
