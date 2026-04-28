package com.finalProject.BookingMeetingRoom.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationSeriesStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// start+ chức năng đặt phòng lặp lại (ReservationSeries entity)
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_reservation_series")
public class ReservationSeries {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ROOM_ID", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Column(name = "START_TIME_OF_DAY", nullable = false)
    private LocalTime startTimeOfDay;

    @Column(name = "END_TIME_OF_DAY", nullable = false)
    private LocalTime endTimeOfDay;

    @Column(name = "DAYS_OF_WEEK", nullable = false)
    private String daysOfWeek;

    // start+ chức năng đặt phòng lặp lại (lưu thông tin mục đích/ghi chú cho các lần sync)
    @Column(name = "PURPOSE", nullable = false)
    private String purpose;

    @Column(name = "NOTE")
    private String note;
    // end+ chức năng đặt phòng lặp lại (lưu thông tin mục đích/ghi chú cho các lần sync)

    @Column(name = "FROM_DATE", nullable = false)
    private LocalDate fromDate;

    @Column(name = "UNTIL_DATE")
    private LocalDate untilDate;

    @Column(name = "ROLLING_WINDOW_WEEKS")
    private Integer rollingWindowWeeks;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    private ReservationSeriesStatus status;

    @Column(name = "LAST_SYNC_UNTIL")
    private LocalDate lastSyncUntil;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
// end+ chức năng đặt phòng lặp lại (ReservationSeries entity)
