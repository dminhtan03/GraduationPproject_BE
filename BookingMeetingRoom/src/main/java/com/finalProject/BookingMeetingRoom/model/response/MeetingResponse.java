package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MeetingResponse {
    private String id;
    private String title;
    private String reservationId;
    private String status;
    private String transcript;
    private String summary;
    private String minutesJson;
    private String audioPath;
    private String jobId;
    private Double durationSeconds;
    private Integer speakerCount;
    private String language;
    private String createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
