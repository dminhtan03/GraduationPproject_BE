package com.finalProject.BookingMeetingRoom.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardOverviewStatsResponse {

    private StatItem totalBookings;
    private StatItem activeUsers;
    private StatItem utilizationRate;
    private StatItem todaysBookings;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StatItem {
        private long value;
        private double change;
    }
}
