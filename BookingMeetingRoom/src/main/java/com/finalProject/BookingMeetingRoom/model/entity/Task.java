package com.finalProject.BookingMeetingRoom.model.entity;

import com.finalProject.BookingMeetingRoom.common.enums.*;
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
@Table(name = "tbl_task")
public class Task {

    @Id
    private String id;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "DESCRIPTION", columnDefinition = "TEXT")
    private String description;

    @Column(name = "GOAL", columnDefinition = "TEXT")
    private String goal;

    @Column(name = "EXPECTED_RESULT", columnDefinition = "TEXT")
    private String expectedResult;

    // Assignment brief shown to assignee
    @Column(name = "ASSIGNMENT_BRIEF", columnDefinition = "TEXT")
    private String assignmentBrief;

    // How-to guidance shown to assignee
    @Column(name = "ASSIGNMENT_HOW", columnDefinition = "TEXT")
    private String assignmentHow;

    @Column(name = "PRIORITY", length = 20)
    @Enumerated(EnumType.STRING)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "STATUS", length = 20)
    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.TODO;

    @Column(name = "DUE_AT")
    private LocalDateTime dueAt;

    // Who created this task (can be different from assigner)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CREATED_BY", nullable = false)
    private User createdBy;

    // Who assigned this task (may differ from creator)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ASSIGNED_BY_ID")
    private User assignedBy;

    // Reviewer - person who approves the completed task
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REVIEWER_USER_ID")
    private User reviewer;

    @Column(name = "REVIEWER_STATUS", length = 20)
    @Enumerated(EnumType.STRING)
    private ReviewerStatus reviewerStatus = ReviewerStatus.NOT_SET;

    // Review decision
    @Column(name = "REVIEW_DECISION", length = 20)
    @Enumerated(EnumType.STRING)
    private ReviewStatus reviewDecision;

    @Column(name = "REVIEW_COMMENT", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "REVIEWED_AT")
    private LocalDateTime reviewedAt;

    // Result submitted by assignee when asking for review
    @Column(name = "RESULT_NOTE", columnDefinition = "TEXT")
    private String resultNote;

    @Column(name = "SUBMITTED_AT")
    private LocalDateTime submittedAt;

    // Source meeting
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEETING_ID")
    private Meeting meeting;

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
