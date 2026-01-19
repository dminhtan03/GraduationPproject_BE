package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.UserOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserOtpRepository extends JpaRepository<UserOtp, String> {

    Optional<UserOtp> findByOtpCode(String validOtp);

    @Query("""
            SELECT u
            FROM UserOtp u
            WHERE u.user.userInfo.email = :userEmail
             AND u.otpCode = :otpKey
        """)
    Optional<UserOtp> findValidOtp(String userEmail, String otpKey);
}
