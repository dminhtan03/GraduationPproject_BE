package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TaskResponse {
    private String id;
    private String title;
    private String description;
    private String goal;
    private String expectedResult;
    private String assignmentBrief;
    private String assignmentHow;
    private String priority;
    private String status;
    private LocalDateTime dueAt;
    // Creator
    private String createdById;
    private String createdByName;
    // Assigned by
    private String assignedById;
    private String assignedByName;
    // Reviewer
    private String reviewerUserId;
    private String reviewerName;
    private String reviewerStatus;
    private String reviewDecision;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    // Submit
    private String resultNote;
    private LocalDateTime submittedAt;
    // Source
    private String meetingId;
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // Related
    private String sprintId;
    private String sprintName;
    private String projectId;
    private String parentTaskId;
    private String parentTaskTitle;
    private List<TaskResponse> subtasks;
    private List<AssignmentInfo> assignments;
    private List<SupporterInfo> supporters;

    @Data
    @Builder
    public static class AssignmentInfo {
        private String id;
        private String assigneeId;
        private String assigneeName;
        private String assignerId;
        private String assignerName;
        private String status;
        private String approvalStatus;
        private boolean primary;
        private String brief;
        private String how;
        private String rejectionReason;
    }

    @Data
    @Builder
    public static class SupporterInfo {
        private String id;
        private String userId;
        private String userName;
        private String status;
    }
}
