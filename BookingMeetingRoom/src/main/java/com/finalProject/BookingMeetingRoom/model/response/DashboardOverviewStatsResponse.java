package com.finalProject.BookingMeetingRoom.model.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardOverviewStatsResponse {

    // ── Stat cards ──────────────────────────────────────────────────────────
    private StatItem totalBookings;      // Booking tạo trong tháng
    private StatItem activeUsers;        // User có hoạt động booking tháng này
    private StatItem completedBookings;  // Booking hoàn thành tháng này (thay utilizationRate)
    private StatItem cancellationRate;   // Tỷ lệ huỷ % tháng này
    private StatItem todaysBookings;     // Booking tạo hôm nay
    private StatItem noShowBookings;     // Booking no-show tháng này

    // ── Chart data ───────────────────────────────────────────────────────────
    /** Số booking tạo theo từng ngày — 7 ngày gần nhất, dùng cho bar chart */
    private List<DailyTrend> dailyTrend;

    /** Phân bố trạng thái booking tháng này, dùng cho pie chart */
    private List<StatusCount> statusDistribution;

    // ── Inner types ──────────────────────────────────────────────────────────
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StatItem {
        private long value;
        private double change;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DailyTrend {
        private String date;   // "YYYY-MM-DD"
        private long count;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StatusCount {
        private String status;
        private long count;
        private double percentage;
    }
}
