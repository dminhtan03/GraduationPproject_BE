package com.finalProject.BookingMeetingRoom.model.request.aiplatform;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPlatformChatRequest {
    @JsonAlias("session_id")
    private String sessionId;

    private String message;

    @JsonAlias("user_id")
    private String userId;

    @JsonAlias("organization_id")
    private String organizationId;
}
