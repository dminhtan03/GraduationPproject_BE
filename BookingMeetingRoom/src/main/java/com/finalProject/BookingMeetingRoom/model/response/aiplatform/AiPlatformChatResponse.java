package com.finalProject.BookingMeetingRoom.model.response.aiplatform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPlatformChatResponse {
    private String reply;
    private String intent;
    private Map<String, Object> data;
}
