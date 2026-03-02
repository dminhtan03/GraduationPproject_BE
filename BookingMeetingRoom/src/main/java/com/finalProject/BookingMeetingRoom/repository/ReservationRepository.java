package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {



    @Query(value = """
            SELECT * FROM tbl_reservation r
            WHERE r.room_id = :roomId
            AND NOT (:endTime <= r.start_time OR :startTime >= r.end_time)
            """, nativeQuery = true)
    List<Reservation> checkOverlappingReservationsByRoom(@Param("roomId") String roomId,
                                                         @Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime);

    @Query(value = "SELECT * FROM tbl_reservation r " +
            " WHERE r.user_id = :userId " +
            " AND r.status in :status " +
            "AND NOT (:endTime <= r.start_time OR :startTime >= r.end_time)",
            nativeQuery = true)
    List<Reservation> checkOverlapByUser(@Param("userId") String userId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime,
                                         @Param("status") List<String> status);

    @Query(value = """
                SELECT * FROM tbl_reservation r
                WHERE r.room_id = :roomId
                  AND DATE(r.start_time ) = DATE(:startTime)
                  AND (
                       r.start_time < :endTime AND r.end_time > :startTime
            
                  )
            """, nativeQuery = true)
    List<Reservation> findOverlappingReservations(@Param("roomId") String roomId,
                                                  @Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);

}