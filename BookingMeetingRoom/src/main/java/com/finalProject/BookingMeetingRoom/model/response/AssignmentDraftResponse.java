package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AssignmentDraftResponse {
    private String id;
    private String meetingId;
    private String title;
    private String description;
    private String goal;
    private String expectedResult;
    private String priority;
    private LocalDateTime dueAt;
    private String assignerUserId;
    private String assigneeUserId;
    private Double aiConfidence;
    private String aiRawText;
    private String reviewStatus;
    private List<String> reviewIssues;
    private String createdTaskId;
    private LocalDateTime createdAt;
}
