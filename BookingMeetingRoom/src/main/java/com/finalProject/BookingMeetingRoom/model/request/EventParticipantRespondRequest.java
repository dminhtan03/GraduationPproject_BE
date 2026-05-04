package com.finalProject.BookingMeetingRoom.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// start+ chức năng đặt phòng theo sự kiện (người tham gia chấp nhận / từ chối)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventParticipantRespondRequest {
    @NotBlank
    private String response;
}
// end+ chức năng đặt phòng theo sự kiện (người tham gia chấp nhận / từ chối)

