package com.finalProject.BookingMeetingRoom.model.request;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoomStatusUpdateRequest {
    private String roomId;
    private RoomStatus newStatus;
}
