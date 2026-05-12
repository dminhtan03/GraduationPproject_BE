package com.finalProject.BookingMeetingRoom.model.entity;

import com.finalProject.BookingMeetingRoom.common.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_task_assignment_draft")
public class TaskAssignmentDraft {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEETING_ID", nullable = false)
    private Meeting meeting;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "DESCRIPTION", columnDefinition = "TEXT")
    private String description;

    @Column(name = "GOAL", columnDefinition = "TEXT")
    private String goal;

    @Column(name = "EXPECTED_RESULT", columnDefinition = "TEXT")
    private String expectedResult;

    @Column(name = "PRIORITY", length = 20)
    private String priority = "MEDIUM";

    @Column(name = "DUE_AT")
    private LocalDateTime dueAt;

    @Column(name = "ASSIGNER_USER_ID")
    private String assignerUserId;

    @Column(name = "ASSIGNEE_USER_ID")
    private String assigneeUserId;

    @Column(name = "AI_CONFIDENCE")
    private Double aiConfidence;

    @Column(name = "AI_RAW_TEXT", columnDefinition = "TEXT")
    private String aiRawText;

    @Column(name = "REVIEW_STATUS", length = 20)
    @Enumerated(EnumType.STRING)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    // Comma-separated: low_confidence, missing_assigner_user_id, missing_assignee_user_id
    @Column(name = "REVIEW_ISSUES")
    private String reviewIssues;

    // Task created after approval
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CREATED_TASK_ID")
    private Task createdTask;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
