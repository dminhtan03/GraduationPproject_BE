package com.finalProject.BookingMeetingRoom.model.response;


import java.time.LocalDateTime;

import com.finalProject.BookingMeetingRoom.common.enums.HistoryAction;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ReservationTimelineResponse {
    private String reservationId;
    private ReservationStatus oldStatus;
    private HistoryAction action;
    private LocalDateTime performAt;
}
