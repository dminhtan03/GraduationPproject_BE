package com.finalProject.BookingMeetingRoom.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordVerifyRequest {

    @NotBlank(message = "Email is mandatory")
    @Email
    private String email;

    private String otp;

}
