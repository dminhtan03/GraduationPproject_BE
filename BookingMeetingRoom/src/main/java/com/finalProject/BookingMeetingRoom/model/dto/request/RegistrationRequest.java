package com.finalProject.BookingMeetingRoom.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistrationRequest {
    @NotBlank(message = "First Name is mandatory")
    private String firstName;

    @NotBlank(message = "Last Name is mandatory")
    private String lastName;

    @NotBlank(message = "Phone Number is mandatory")
    private String phoneNumber;

    @NotBlank(message = "Address is mandatory")
    private String address;

    @NotBlank(message = "Department is mandatory")
    private String department;

    @NotBlank(message = "Email is mandatory")
    @Email
    private String email;

    @NotBlank(message = "Gender is mandatory")
    private String gender;

    @NotBlank(message = "Password is mandatory")
    @Size(min = 6, message = "Password should be 6 characters long minimum")
    private String password;

    @NotBlank(message = "Role is mandatory")
    private String role;
}
