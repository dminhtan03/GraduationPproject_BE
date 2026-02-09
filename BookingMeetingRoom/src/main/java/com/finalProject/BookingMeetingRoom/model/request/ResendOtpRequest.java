package com.finalProject.BookingMeetingRoom.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendOtpRequest {

    @Email
    @NotBlank
    private String email;

}
