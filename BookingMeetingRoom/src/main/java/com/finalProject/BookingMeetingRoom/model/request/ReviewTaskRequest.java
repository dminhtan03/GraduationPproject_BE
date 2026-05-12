package com.finalProject.BookingMeetingRoom.model.request;

import lombok.Data;

@Data
public class ReviewTaskRequest {
    private String decision;  // APPROVED | REJECTED
    private String comment;
}
