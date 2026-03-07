package com.finalProject.BookingMeetingRoom.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDashboardResponse {
    private String id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String department;
    private boolean enabled;
    private boolean isLocked;
}
