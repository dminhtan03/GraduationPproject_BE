package com.finalProject.BookingMeetingRoom.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    @NotBlank(message = "Email is mandatory")
    @Email
    private String email;
}
