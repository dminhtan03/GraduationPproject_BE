package com.finalProject.BookingMeetingRoom.model.request;

import lombok.Data;

@Data
public class ApproveDraftRequest {
    private String assigner_user_id;
    private String assignerUserId;
    private String assignee_user_id;
    private String assigneeUserId;
}
