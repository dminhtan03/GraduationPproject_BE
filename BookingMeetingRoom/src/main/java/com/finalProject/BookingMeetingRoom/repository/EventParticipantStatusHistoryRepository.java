package com.finalProject.BookingMeetingRoom.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.finalProject.BookingMeetingRoom.model.entity.EventParticipantStatusHistory;

@Repository
public interface EventParticipantStatusHistoryRepository extends JpaRepository<EventParticipantStatusHistory, String> {
    // start+ chức năng đặt phòng theo sự kiện (lịch sử thay đổi trạng thái người tham gia)
    List<EventParticipantStatusHistory> findByParticipant_IdOrderByChangedAtDesc(String participantId);
    // end+ chức năng đặt phòng theo sự kiện (lịch sử thay đổi trạng thái người tham gia)
}

