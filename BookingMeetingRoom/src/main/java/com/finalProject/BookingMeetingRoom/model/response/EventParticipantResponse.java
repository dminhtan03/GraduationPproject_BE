package com.finalProject.BookingMeetingRoom.model.response;

import java.time.LocalDateTime;

import com.finalProject.BookingMeetingRoom.common.enums.EventParticipantCheckInStatus;
import com.finalProject.BookingMeetingRoom.common.enums.EventParticipantInviteStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// start+ chức năng đặt phòng theo sự kiện (participant response)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventParticipantResponse {
    private String id;
    private String userId;
    private String email;
    private String fullName;
    private EventParticipantInviteStatus inviteStatus;
    private EventParticipantCheckInStatus checkInStatus;
    private LocalDateTime checkInTime;
}
// end+ chức năng đặt phòng theo sự kiện (participant response)
