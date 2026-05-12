package com.finalProject.BookingMeetingRoom.model.request;

import lombok.Data;

@Data
public class AssignTaskRequest {
    private String assignee_id;   // ai-platform snake_case
    private String assigneeId;
    private String assigner_id;
    private String assignerId;
    private String brief;         // Ghi chú - lý do chọn người này
    private String how;           // Cách làm
    private String expectedResult;
    private boolean primary = true;
    private boolean requireApproval = false;
}
