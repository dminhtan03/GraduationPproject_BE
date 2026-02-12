package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.model.dto.ActiveReservationDto;
import com.finalProject.BookingMeetingRoom.model.dto.HoursThisWeekDto;
import com.finalProject.BookingMeetingRoom.model.dto.LastCheckedInDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EmployeeDashboardResponse {

    private ActiveReservationDto activeReservationDto;
    private LastCheckedInDto lastCheckedInDto;
    private HoursThisWeekDto hoursThisWeekDto;
    private int totalReservationsThisMonth;

}