package com.finalProject.BookingMeetingRoom.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Password is mandatory.")
    @Size(min = 6, message = "Password should be 6 characters long minimum.")
    private String currentPassword;
    @NotBlank(message = "Password is mandatory.")
    @Size(min = 6, message = "Password should be 6 characters long minimum.")
    private String newPassword;
    @NotBlank(message = "Password is mandatory.")
    @Size(min = 6, message = "Password should be 6 characters long minimum.")
    private String confirmPassword;
}
