package com.finalProject.BookingMeetingRoom.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MyReservationResponse {
    private String reservationId;
    private String locationCode;
    private String address;
    private String floorName;
    private String buildingName;
    private String reservationStatus;
    private LocalDate selectedDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Double duration;
    private Boolean isFeedback;
}

