package com.finalProject.BookingMeetingRoom.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationSeriesStatus;
import com.finalProject.BookingMeetingRoom.model.entity.ReservationSeries;

@Repository
// start+ chức năng đặt phòng lặp lại (ReservationSeries repository)
public interface ReservationSeriesRepository extends JpaRepository<ReservationSeries, String> {
    List<ReservationSeries> findByStatus(ReservationSeriesStatus status);

    // start+ cross-check recurring series vs academic schedule
    @Query("""
        SELECT rs FROM ReservationSeries rs
        WHERE rs.room.id = :roomId
          AND rs.status = :status
          AND rs.fromDate <= :toDate
          AND (rs.untilDate IS NULL OR rs.untilDate >= :fromDate)
        """)
    List<ReservationSeries> findActiveSeriesForRoomInPeriod(
        @Param("roomId") String roomId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate,
        @Param("status") ReservationSeriesStatus status);
    // end+ cross-check recurring series vs academic schedule
}
// end+ chức năng đặt phòng lặp lại (ReservationSeries repository)
