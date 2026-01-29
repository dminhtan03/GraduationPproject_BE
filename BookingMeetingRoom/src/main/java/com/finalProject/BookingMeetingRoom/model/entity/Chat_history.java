package com.finalProject.BookingMeetingRoom.model.entity;

import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "tbl_chat_history",
        indexes = {
                @Index(name = "idx_chat_user", columnList = "user_id"),
                @Index(name = "idx_chat_session", columnList = "session_id"),
                @Index(name = "idx_chat_created", columnList = "created_at")
        }
)
public class Chat_history {
    @Id
    private String id;

    // Người chat (có thể null nếu guest)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Chatbot xử lý
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private Chatbot chatbot;

    @Column(name = "session_id", nullable = false)
    private String sessionId;
    // dùng để group các message trong 1 phiên chat

    @Enumerated(EnumType.STRING)
    @Column(name = "sender", nullable = false)
    private SenderType sender;
    // USER / BOT

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
