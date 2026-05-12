package com.finalProject.BookingMeetingRoom.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.finalProject.BookingMeetingRoom.model.entity.EventParticipant;

@Repository
public interface EventParticipantRepository extends JpaRepository<EventParticipant, String> {
    // start+ chức năng đặt phòng theo sự kiện (quản lý participants)
    List<EventParticipant> findByEvent_Id(String eventId);
    Optional<EventParticipant> findByEvent_IdAndUser_Id(String eventId, String userId);
    List<EventParticipant> findByUser_Id(String userId);
    // end+ chức năng đặt phòng theo sự kiện (quản lý participants)

    // start+ chức năng đặt phòng theo sự kiện (mã code 6 số)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM EventParticipant p WHERE p.event.reservation.id = :reservationId AND (p.user.userInfo.email = :email OR p.email = :email)")
    Optional<EventParticipant> findByReservationIdAndEmail(String reservationId, String email);
    // end+ chức năng đặt phòng theo sự kiện (mã code 6 số)
}
