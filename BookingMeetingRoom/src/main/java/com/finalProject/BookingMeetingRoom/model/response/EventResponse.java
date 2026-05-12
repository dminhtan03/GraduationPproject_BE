package com.finalProject.BookingMeetingRoom.model.response;

import java.time.LocalDateTime;
import java.util.List;

import com.finalProject.BookingMeetingRoom.common.enums.EventVisibility;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// start+ chức năng đặt phòng theo sự kiện (event response)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventResponse {
    private String id;
    private String reservationId;
    private String title;
    private String description;
    private EventVisibility visibility;
    private LocalDateTime createdAt;
    private List<EventParticipantResponse> participants;
}
// end+ chức năng đặt phòng theo sự kiện (event response)
