package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProcessRecordingResponse {
    private String meetingId;
    private String summary;
    private String transcript;
    private List<ExtractedTask> tasks;

    @Data
    @Builder
    public static class ExtractedTask {
        private String title;
        private String description;
        private String goal;
        private String expectedResult;
        private String priority;
        private String dueAt;
        private Double aiConfidence;
    }
}
