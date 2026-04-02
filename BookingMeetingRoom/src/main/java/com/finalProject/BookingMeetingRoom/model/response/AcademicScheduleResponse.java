package com.finalProject.BookingMeetingRoom.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicScheduleResponse {
    private String id;
    private String roomId;
    private String roomName;
    private String floorName;
    private String buildingName;
    private LocalTime startTime;
    private LocalTime endTime;
    private String daysOfWeek;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String description;
}
