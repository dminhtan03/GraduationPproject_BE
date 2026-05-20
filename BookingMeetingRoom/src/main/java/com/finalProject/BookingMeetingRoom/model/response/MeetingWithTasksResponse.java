package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MeetingWithTasksResponse {
    private String meetingId;
    private String title;
    private String summary;
    private String transcript;
    private String status;
    private LocalDateTime createdAt;
    private List<TaskInfo> tasks;

    @Data
    @Builder
    public static class TaskInfo {
        private String draftId;
        private String title;
        private String description;
        private String goal;
        private String expectedResult;
        private String priority;
        private String dueAt;
        private Double aiConfidence;
        private String createdTaskId;  // null = chưa tạo task, non-null = đã thêm vào task
    }
}
