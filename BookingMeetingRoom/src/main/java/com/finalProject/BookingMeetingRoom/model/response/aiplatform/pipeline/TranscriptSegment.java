package com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegment {
    @JsonProperty("segment_id")
    private String segmentId;

    private String speaker;
    private Double start;
    private Double end;
    private String text;
    private Double confidence;

    @JsonProperty("stt_confidence")
    private Double sttConfidence;

    @JsonProperty("speaker_confidence")
    private Double speakerConfidence;

    @JsonProperty("needs_review")
    private Boolean needsReview;
}
