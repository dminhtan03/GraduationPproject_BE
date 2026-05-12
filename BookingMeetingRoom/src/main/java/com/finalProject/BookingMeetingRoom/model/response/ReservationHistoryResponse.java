package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.common.enums.HistoryAction;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationHistoryResponse {
    private String id;
    private ReservationStatus oldStatus;
    private HistoryAction action;
    private LocalDateTime oldStartTime;
    private LocalDateTime oldEndTime;
    private String performBy;
    private LocalDateTime performAt;
}