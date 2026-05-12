package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TodaySummaryResponse {
    private int totalTasks;
    private int todoCount;
    private int doingCount;
    private int waitingReviewCount;
    private int doneCount;
    private int overdueCount;
    private List<TaskResponse> todayTasks;
}
