package com.finalProject.BookingMeetingRoom.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationSeriesStatus;
import com.finalProject.BookingMeetingRoom.model.entity.ReservationSeries;

@Repository
// start+ chức năng đặt phòng lặp lại (ReservationSeries repository)
public interface ReservationSeriesRepository extends JpaRepository<ReservationSeries, String> {
    List<ReservationSeries> findByStatus(ReservationSeriesStatus status);
}
// end+ chức năng đặt phòng lặp lại (ReservationSeries repository)
