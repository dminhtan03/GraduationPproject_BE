package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Setter
@Getter
public class UserResponse {
    private String id;

    private String firstName;

    private String lastName;

    private String phoneNumber;

    private String address;

    private String department;

    private String email;

    private String gender;

    private boolean isReset;
}

