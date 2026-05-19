package com.finalProject.BookingMeetingRoom.model.request;

import lombok.Data;

@Data
public class TaskRequest {
    private String title;
    private String description;
    private String goal;
    private String expectedResult;
    private String assignmentBrief;
    private String assignmentHow;
    private String priority;   // LOW | MEDIUM | HIGH | URGENT
    private String status;     // TODO | DOING | WAITING_REVIEW | DONE | CANCELLED | REWORK
    private String due_at;     // ISO string — ai-platform uses snake_case
    private String dueAt;      // also accept camelCase
    private String meetingId;
    // Reviewer fields
    private String reviewerUserId;
    // Assignee fields (for create-and-assign in one call)
    private String assigneeId;
    private String brief;
    private String how;
    private String sprintId;
    private String parentTaskId;
}
