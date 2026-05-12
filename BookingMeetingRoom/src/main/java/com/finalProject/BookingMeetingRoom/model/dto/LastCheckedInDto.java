package com.finalProject.BookingMeetingRoom.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LastCheckedInDto {
    private String roomId;
    private String buildingName;
    private String floorName;
    private LocalDateTime lastCheckedInTime;
}