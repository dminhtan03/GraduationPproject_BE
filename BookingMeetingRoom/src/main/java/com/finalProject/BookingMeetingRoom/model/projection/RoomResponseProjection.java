package com.finalProject.BookingMeetingRoom.model.projection;

import java.time.LocalDateTime;

public interface RoomResponseProjection {
    String getUserId();
    String getUserName();
    LocalDateTime getCheckInTime();
}