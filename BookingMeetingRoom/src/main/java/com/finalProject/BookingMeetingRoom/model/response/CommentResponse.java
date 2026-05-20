package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommentResponse {
    private String id;
    private String taskId;
    private String authorId;
    private String authorName;
    private String content;
    private String parentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
