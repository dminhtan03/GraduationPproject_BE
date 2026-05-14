package com.finalProject.BookingMeetingRoom.model.response.aiplatform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponse {
    private String jobId;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, Object> result;
    private String error;
    private Double progress;
}
