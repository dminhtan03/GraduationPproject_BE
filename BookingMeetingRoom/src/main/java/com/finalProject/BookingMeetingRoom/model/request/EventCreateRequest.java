package com.finalProject.BookingMeetingRoom.model.request;

import com.finalProject.BookingMeetingRoom.common.enums.EventVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// start+ chức năng đặt phòng theo sự kiện (create event request)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCreateRequest {
    @NotNull
    private String reservationId;
    @NotBlank
    private String title;
    private String description;
    @NotNull
    private EventVisibility visibility;
}
// end+ chức năng đặt phòng theo sự kiện (create event request)
