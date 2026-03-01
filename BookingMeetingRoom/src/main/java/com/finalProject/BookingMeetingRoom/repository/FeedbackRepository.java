package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, String> {

    @Query("""
            SELECT f FROM Feedback f
            JOIN f.reservation r
            JOIN r.room ro
            WHERE ro.id = :roomId
            ORDER BY f.createdAt DESC
            """)
    List<Feedback> findByRoomIdOrderByCreatedAtDesc(String roomId);
}
