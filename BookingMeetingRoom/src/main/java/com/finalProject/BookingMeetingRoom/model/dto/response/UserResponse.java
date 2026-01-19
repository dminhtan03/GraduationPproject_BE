package com.finalProject.BookingMeetingRoom.model.dto.response;

import lombok.Setter;

import java.util.UUID;

@Setter
public class UserResponse {
    private UUID id;

    private String firstName;

    private String lastName;

    private String phoneNumber;

    private String address;

    private String email;

    private String gender;
}
