package com.finalProject.BookingMeetingRoom.model.projection;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;

public interface RoomMapDashboardProjection {
    String getBuildingId();
    String getBuildingName();
    String getAddress();
    String getFloorId();
    String getFloorName();
    String getRoomId();
    String getLocationCode();
    RoomStatus getStatus();
    Double getScore();
}