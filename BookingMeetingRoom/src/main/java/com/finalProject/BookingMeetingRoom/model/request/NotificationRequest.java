package com.finalProject.BookingMeetingRoom.model.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationRequest {

    private String content;

    private String title;

    private LocalDateTime createdAt;

    private String userId;

    public boolean isRead;

    public String reservationId;

    public boolean sendEmail;
}
