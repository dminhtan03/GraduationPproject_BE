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
    // optional: if user already picked a timeslot
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    // optional session id to group messages (if not provided, server will generate one)
    private String sessionId;
}
