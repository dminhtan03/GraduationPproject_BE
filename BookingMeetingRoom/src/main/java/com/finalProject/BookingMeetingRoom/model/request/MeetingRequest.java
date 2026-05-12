package com.finalProject.BookingMeetingRoom.model.request;

import lombok.Data;

@Data
public class MeetingRequest {
    private String title;
    private String reservationId;
    private String reservation_id;  // ai-platform snake_case
    private String language;
    private String file_path;        // for recording upload
}
