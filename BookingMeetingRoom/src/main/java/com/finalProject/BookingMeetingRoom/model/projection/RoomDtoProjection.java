package com.finalProject.BookingMeetingRoom.model.projection;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;

public interface RoomDtoProjection {
    String getRoomId();
    String getLocationCode();
    RoomStatus getStatus();
    Double getScore();

    // start add layout projection fields
    Double getXPosition();
    Double getYPosition();
    Double getWidth();
    Double getHeight();
    Boolean getPositioned();
    // end add layout projection fields
}