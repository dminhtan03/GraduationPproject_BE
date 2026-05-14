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
public class MeetingMinutes {
    private String title;
    private String summary;

    @JsonProperty("key_decisions")
    private List<String> keyDecisions;

    @JsonProperty("discussion_points")
    private List<String> discussionPoints;

    private List<String> risks;

    @JsonProperty("open_questions")
    private List<String> openQuestions;
}
