package com.finalProject.BookingMeetingRoom.model.request.aiplatform;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingAnalyzeRequest {
    @JsonAlias("meeting_title")
    private String meetingTitle;

    private String description;

    private List<String> participants;

    private List<String> notes;

    @JsonAlias("action_items")
    private List<String> actionItems;

    private String transcript;

    @JsonAlias("scheduled_start")
    private String scheduledStart;

    @JsonAlias("audio_path")
    private String audioPath;
}
