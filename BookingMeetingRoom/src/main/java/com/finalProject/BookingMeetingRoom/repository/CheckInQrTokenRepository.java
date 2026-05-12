package com.finalProject.BookingMeetingRoom.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.finalProject.BookingMeetingRoom.model.entity.CheckInQrToken;

@Repository
// start+ chức năng check-in bằng QR (repository)
public interface CheckInQrTokenRepository extends JpaRepository<CheckInQrToken, String> {
}
// end+ chức năng check-in bằng QR (repository)
