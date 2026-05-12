package com.finalProject.BookingMeetingRoom.model.entity;

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
@Table(name = "tbl_meeting")
public class Meeting {

    @Id
    private String id;

    @Column(name = "TITLE", nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESERVATION_ID")
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CREATED_BY", nullable = false)
    private User createdBy;

    @Column(name = "TRANSCRIPT", columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "SUMMARY", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "MINUTES_JSON", columnDefinition = "TEXT")
    private String minutesJson;

    @Column(name = "AUDIO_PATH")
    private String audioPath;

    @Column(name = "JOB_ID")
    private String jobId;

    // pending | running | completed | failed
    @Column(name = "STATUS", length = 20)
    private String status = "pending";

    @Column(name = "DURATION_SECONDS")
    private Double durationSeconds;

    @Column(name = "SPEAKER_COUNT")
    private Integer speakerCount;

    @Column(name = "LANGUAGE", length = 10)
    private String language = "vi";

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
