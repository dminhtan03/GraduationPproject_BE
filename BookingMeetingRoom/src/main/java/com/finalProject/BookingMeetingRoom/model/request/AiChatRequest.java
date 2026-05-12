package com.finalProject.BookingMeetingRoom.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {
    private String message;

    // Optional: group messages into a chat session
    private String sessionId;

    // optional: if user already picked a timeslot
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
}

