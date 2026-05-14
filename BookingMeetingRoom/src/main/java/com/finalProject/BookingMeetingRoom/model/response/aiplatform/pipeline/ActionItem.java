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
public class ActionItem {
    private String title;
    private String description;
    private String assignee;

    @JsonProperty("due_date")
    private String dueDate;

    private String priority;
    private Double confidence;

    @JsonProperty("source_speaker")
    private String sourceSpeaker;

    @JsonProperty("source_segment_ids")
    private List<String> sourceSegmentIds;

    @JsonProperty("needs_review")
    private Boolean needsReview;
}
