// ...existing code...
package com.finalProject.BookingMeetingRoom.model.request;

import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveChatRequest {
    private String sessionId;
    private SenderType sender;
    private String message;
}

