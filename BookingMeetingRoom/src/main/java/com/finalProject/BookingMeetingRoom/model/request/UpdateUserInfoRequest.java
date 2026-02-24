package com.finalProject.BookingMeetingRoom.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserInfoRequest {
    @NotBlank(message = "Phone Number is mandatory")
    @Pattern(regexp = "^0\\d{0,9}$", message = "Phone must start with 0 and contain up to 10 digits")
    private String phoneNumber;

    @NotBlank(message = "Address is mandatory")
    private String address;
}