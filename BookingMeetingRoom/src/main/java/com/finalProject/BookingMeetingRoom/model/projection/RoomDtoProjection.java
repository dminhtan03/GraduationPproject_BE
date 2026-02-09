package com.finalProject.BookingMeetingRoom.model.projection;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;

public interface RoomDtoProjection {
    String getRoomId();
    String getLocationCode();
    RoomStatus getStatus();
    Double getScore();
}