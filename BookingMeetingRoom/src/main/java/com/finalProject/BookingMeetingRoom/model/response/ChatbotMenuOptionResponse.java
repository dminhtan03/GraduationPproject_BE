package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.common.enums.ChatbotIntent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotMenuOptionResponse {
    private String code;
    private String label;
    private ChatbotIntent intent;
}
