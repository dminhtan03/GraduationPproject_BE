package com.finalProject.BookingMeetingRoom.model.entity;

import com.finalProject.BookingMeetingRoom.common.enums.ApprovalStatus;
import com.finalProject.BookingMeetingRoom.common.enums.AssignmentStatus;
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
@Table(name = "tbl_task_assignment")
public class TaskAssignment {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TASK_ID", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ASSIGNER_ID")
    private User assigner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ASSIGNEE_ID", nullable = false)
    private User assignee;

    // accept/reject status from assignee
    @Column(name = "STATUS", length = 20)
    @Enumerated(EnumType.STRING)
    private AssignmentStatus status = AssignmentStatus.PENDING;

    // manager approval status
    @Column(name = "APPROVAL_STATUS", length = 20)
    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus = ApprovalStatus.NOT_REQUIRED;

    @Column(name = "IS_PRIMARY")
    private boolean primary = true;

    // Brief note explaining why this person was chosen
    @Column(name = "BRIEF", columnDefinition = "TEXT")
    private String brief;

    // How-to guidance
    @Column(name = "HOW", columnDefinition = "TEXT")
    private String how;

    @Column(name = "REJECTION_REASON", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "RESPONDED_AT")
    private LocalDateTime respondedAt;

    @Column(name = "AI_CONFIDENCE")
    private Double aiConfidence;

    @Column(name = "AI_RAW_TEXT", columnDefinition = "TEXT")
    private String aiRawText;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
