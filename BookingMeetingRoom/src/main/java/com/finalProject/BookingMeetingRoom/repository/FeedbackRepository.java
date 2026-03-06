package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.Feedback;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    String findAllFeedbackOfARoom =
            "SELECT fb.* FROM tbl_feedback fb " +
                    " JOIN tbl_reservation r on r.id = fb.reservation_id " +
                    " JOIN tbl_room ro on ro.id = r.room_id " +
                    " WHERE ro.id = :roomId";

    @Query(nativeQuery = true, value = findAllFeedbackOfARoom)
    Page<Feedback> findAllFeedbackOfARoom(String roomId, Pageable pageable);


}
