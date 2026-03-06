package com.finalProject.BookingMeetingRoom.model.request;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class FeedbackRequest {

    @NotBlank(message = "Reservation's id is mandatory")
    private String reservationId;

    @Min(0)
    @Max(5)
    private Integer rating;

    @NotBlank(message = "Description is mandatory")
    private String description;
    private LocalDateTime createdAt;

}

