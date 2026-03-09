package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.Chat_history;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<Chat_history, String> {
    Page<Chat_history> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    List<Chat_history> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
