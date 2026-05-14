package com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanedTranscript {
    private List<TranscriptSegment> segments;
    private String language;

    @JsonProperty("duration_seconds")
    private Double durationSeconds;

    @JsonProperty("speaker_count")
    private Integer speakerCount;

    @JsonProperty("full_text")
    private String fullText;
}
