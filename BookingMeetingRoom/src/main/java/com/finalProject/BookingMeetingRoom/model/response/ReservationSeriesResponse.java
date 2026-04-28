package com.finalProject.BookingMeetingRoom.model.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationSeriesStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// start+ chức năng đặt phòng lặp lại (series response)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationSeriesResponse {
    private String id;
    private String roomId;
    private String roomCode;
    private LocalTime startTimeOfDay;
    private LocalTime endTimeOfDay;
    private String daysOfWeek;
    private String purpose;
    private String note;
    private LocalDate fromDate;
    private LocalDate untilDate;
    private Integer rollingWindowWeeks;
    private ReservationSeriesStatus status;
    private LocalDate lastSyncUntil;
    private LocalDateTime createdAt;
    // start+ chức năng admin quản lý recurring series
    private String userEmail;
    // end+ chức năng admin quản lý recurring series
}
// end+ chức năng đặt phòng lặp lại (series response)
