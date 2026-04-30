package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class AdminFeedbackResponse {
    private String id;
    private String roomId;
    private String roomName;
    private String userId;
    private String userName;
    private Integer rating;
    private String description;
    private LocalDateTime createdAt;
    private String buildingName;
    private String floorName;
    private String userEmail;
}
