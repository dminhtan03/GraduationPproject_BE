package com.finalProject.BookingMeetingRoom.model.entity;

import com.finalProject.BookingMeetingRoom.common.enums.ProjectMemberRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_project_member",
        uniqueConstraints = @UniqueConstraint(columnNames = {"PROJECT_ID", "USER_ID"}))
public class ProjectMember {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROJECT_ID", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Column(name = "ROLE", length = 20)
    @Enumerated(EnumType.STRING)
    private ProjectMemberRole role = ProjectMemberRole.MEMBER;

    @Column(name = "JOINED_AT")
    private LocalDateTime joinedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (joinedAt == null) joinedAt = LocalDateTime.now();
    }
}
