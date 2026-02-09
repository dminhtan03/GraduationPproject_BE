package com.finalProject.BookingMeetingRoom.model.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForgotPasswordResponse {

    @NotBlank
    @Size(min = 6, message = "Password should be 6 characters long minimum")
    private String password;
}
