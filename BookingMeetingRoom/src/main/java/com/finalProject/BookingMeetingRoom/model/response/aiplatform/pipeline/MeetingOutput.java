package com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingOutput {
    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("audio_path")
    private String audioPath;

    private String status;

    @JsonProperty("processed_at")
    private Instant processedAt;

    private CleanedTranscript transcript;
    private MeetingMinutes minutes;

    @JsonProperty("action_items")
    private List<ActionItem> actionItems;

    @JsonProperty("duration_seconds")
    private Double durationSeconds;

    @JsonProperty("speaker_count")
    private Integer speakerCount;

    private String language;
    private String error;
}
