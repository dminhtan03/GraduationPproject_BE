package com.finalProject.BookingMeetingRoom.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// start+ chức năng đặt phòng theo sự kiện (invite participants request)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventParticipantCreateRequest {
    @NotNull
    private String eventId;
    private String userId;
    private String email;
    private String fullName;
}
// end+ chức năng đặt phòng theo sự kiện (invite participants request)
