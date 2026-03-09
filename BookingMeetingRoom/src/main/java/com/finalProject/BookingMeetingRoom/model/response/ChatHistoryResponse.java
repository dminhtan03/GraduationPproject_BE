// ...existing code...
package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryResponse {
    private String id;
    private String sessionId;
    private SenderType sender;
    private String message;
    private LocalDateTime createdAt;
    private String chatbotId;
    private String chatbotName;
    private String userId;
    private String userEmail;
}

