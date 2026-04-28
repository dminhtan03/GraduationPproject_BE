package com.finalProject.BookingMeetingRoom.model.response;

import java.time.LocalDateTime;

import com.finalProject.BookingMeetingRoom.common.enums.EventVisibility;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminEventResponse {
    private String eventId;
    private String reservationId;
    private String title;
    private String description;
    private EventVisibility visibility;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ReservationStatus status;
    private String roomName;
    private String roomCode;
    private String userName;
    private String userEmail;
    private LocalDateTime createdAt;
}
