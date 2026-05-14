package com.finalProject.BookingMeetingRoom.model.response.aiplatform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingSummaryResponse {
    private String transcript;
    private String summary;
}
