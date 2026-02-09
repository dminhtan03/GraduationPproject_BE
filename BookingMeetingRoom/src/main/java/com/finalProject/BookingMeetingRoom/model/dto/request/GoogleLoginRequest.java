package com.finalProject.BookingMeetingRoom.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID token is mandatory")
    private String idToken;
}
