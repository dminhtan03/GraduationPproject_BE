package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DashboardSummaryResponse {
    private int totalRooms;
    private int occupiedRooms;
    private int brokenRooms;
    private int totalUsers;
}