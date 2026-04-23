package com.finalProject.BookingMeetingRoom.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.finalProject.BookingMeetingRoom.model.entity.Event;

@Repository
public interface EventRepository extends JpaRepository<Event, String> {
    // start+ chức năng đặt phòng theo sự kiện (truy vấn event theo reservation)
    Optional<Event> findByReservation_Id(String reservationId);
    // end+ chức năng đặt phòng theo sự kiện (truy vấn event theo reservation)
}
