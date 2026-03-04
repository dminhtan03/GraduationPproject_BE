package com.finalProject.BookingMeetingRoom.model.request;

import lombok.Builder;
import lombok.Data;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;

@Data
@Builder
public class RoomReserveStatusUpdateRequest {
    private String roomId;
    private LocalDateTime leftTime;
    private LocalDateTime rightTime;
    private String type;
}
