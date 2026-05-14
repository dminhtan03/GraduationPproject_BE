package com.finalProject.BookingMeetingRoom.model.response.aiplatform;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedTaskItem {
    private String title;
    private String description;
    private String goal;

    @JsonAlias("expected_result")
    private String expectedResult;

    private String priority;

    @JsonAlias("due_at")
    private String dueAt;

    @JsonAlias("assigner_user_id")
    private String assignerUserId;

    @JsonAlias("assignee_user_id")
    private String assigneeUserId;

    @JsonAlias("ai_confidence")
    private Double aiConfidence;

    @JsonAlias("ai_raw_text")
    private String aiRawText;
}
