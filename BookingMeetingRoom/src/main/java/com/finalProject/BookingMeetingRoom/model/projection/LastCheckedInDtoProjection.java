package com.finalProject.BookingMeetingRoom.model.projection;

import java.time.LocalDateTime;

public interface LastCheckedInDtoProjection {
    String getRoomId();
    String getBuildingName();
    String getFloorName();
    LocalDateTime getLastCheckedInTime();
}