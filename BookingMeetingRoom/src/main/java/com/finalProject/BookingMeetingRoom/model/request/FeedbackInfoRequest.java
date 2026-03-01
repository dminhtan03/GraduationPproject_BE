package com.finalProject.BookingMeetingRoom.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class FeedbackInfoRequest {

    private String id;
    private Integer rating;
    private String description;
    private LocalDateTime createdAt;

}
