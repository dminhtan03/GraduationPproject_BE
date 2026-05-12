package com.finalProject.BookingMeetingRoom.model.dto;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationDTO {

    private String id;

    private String title;

    private String content;

    private String userId;

    @JsonProperty("isRead")
    private boolean isRead;

    private LocalDateTime createdAt;

    private String ReservationId;

    private ReservationStatus reservationStatusAtNow;

}


