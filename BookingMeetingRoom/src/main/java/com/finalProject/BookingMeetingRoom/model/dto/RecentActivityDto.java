package com.finalProject.BookingMeetingRoom.model.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class RecentActivityDto {
    private String userName;
    private String locationCode;
    private String buildingName;
    private String reservationStatus;
    private LocalDateTime reservationTime;
}