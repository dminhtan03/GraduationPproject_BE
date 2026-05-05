package com.finalProject.BookingMeetingRoom.model.entity;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_reservation")
public class Reservation {

    @Id
    private String id;

    @Column(name = "START_TIME")
    private LocalDateTime startTime;

    @Column(name = "END_TIME")
    private LocalDateTime endTime;

    @Column(name = "STATUS", length = 20)
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Column(name = "PURPOSE")
    private String purpose; // Mục đích sử dụng

    @Column(name = "NOTE")
    private String note;    // Ghi chú (không bắt buộc)

    @Column(name = "CHECKIN_TIME")
    private LocalDateTime checkinTime;

    @Column(name = "RETURN_TIME")
    private LocalDateTime returnTime;

    @Version
    private Integer version;

    @Column(name = "Reason")
    private String reason;

    @Column(name = "Cancel_By")
    private String cancelBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    // start+ chức năng đặt phòng lặp lại (Reservation Series)
    @Column(name = "SERIES_ID")
    private String seriesId;

    @Column(name = "SERIES_DATE")
    private LocalDate seriesDate;
    // end+ chức năng đặt phòng lặp lại (Reservation Series)

    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToOne(mappedBy = "reservation")
    private Feedback feedback;

}
