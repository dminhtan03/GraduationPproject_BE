package com.finalProject.BookingMeetingRoom.model.entity;

import com.finalProject.BookingMeetingRoom.common.enums.HistoryAction;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "tbl_reservation_history")
public class ReservationHistory {

    @Id
    private String id;

    @Column(name = "OLD_STATUS")
    @Enumerated(EnumType.STRING)
    private ReservationStatus oldStatus;

    @Column(name = "ACTION")
    @Enumerated(EnumType.STRING)
    private HistoryAction action;

    @Column(name = "OLD_START_TIME")
    private LocalDateTime oldStartTime;

    @Column(name = "OLD_END_TIME")
    private LocalDateTime oldEndTime;

    @Column(name = "PERFORM_BY")
    private String performBy;

    @Column(name = "PERFORM_AT")
    private LocalDateTime performAt;

    @ManyToOne
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

}
