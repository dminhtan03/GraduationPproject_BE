package com.finalProject.BookingMeetingRoom.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    // start add bookingLockedUntil
    private LocalDateTime bookingLockedUntil;
    // end add bookingLockedUntil
    private Integer cancellationCount;
}

