package com.finalProject.BookingMeetingRoom.model.dto.response;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RegistrationResponse {
    private String firstName;

    private String lastName;

    private String phoneNumber;

    private String address;

    private String department;

    private String email;

    private String gender;
}
