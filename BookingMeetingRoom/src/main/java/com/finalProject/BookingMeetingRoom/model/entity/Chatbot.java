package com.finalProject.BookingMeetingRoom.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "tbl_chatbot")
public class Chatbot {
    @Id
    private String id;

    @Column(name = "bot_name", nullable = false)
    private String botName;

    @Column(name = "description")
    private String description;

    @Column(name = "model_name", nullable = false)
    private String modelName;
    // ví dụ: gpt-4, gemini, whisper

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
