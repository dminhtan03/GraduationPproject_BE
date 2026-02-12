package com.finalProject.BookingMeetingRoom.model.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class HoursThisWeekDto {
    int totalHoursThisWeek;
    int totalHoursThisMonth;
}