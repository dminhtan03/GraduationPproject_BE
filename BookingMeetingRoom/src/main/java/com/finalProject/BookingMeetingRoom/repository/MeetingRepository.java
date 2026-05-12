package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.Meeting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, String> {
    Page<Meeting> findByCreatedBy_IdOrderByCreatedAtDesc(String userId, Pageable pageable);
    List<Meeting> findByCreatedBy_IdOrderByCreatedAtDesc(String userId);
    List<Meeting> findByReservation_Id(String reservationId);
}
