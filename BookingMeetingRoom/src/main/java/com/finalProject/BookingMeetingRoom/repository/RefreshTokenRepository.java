package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    String findToken = "SELECT trt.*\n" +
            "FROM tbl_refresh_token trt\n" +
            "WHERE trt.is_revoked = false\n" +
            "AND trt.refresh_token = :refreshToken;";

    @Query(nativeQuery = true, value = findToken)
    RefreshToken findByTokenAndIsRevoked(String refreshToken);
}
