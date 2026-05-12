package com.finalProject.BookingMeetingRoom.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CancelReservationRequest {
    
    @NotBlank(message = "Reason is required")
    @Size(min = 2, max = 500, message = "Reason must be between 2 and 500 characters")
    private String reason;
}
