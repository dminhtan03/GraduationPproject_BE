package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.request.RoomStatusUpdateRequest;

public interface RoomStatusUpdateService {
    void sendRealTimeRoomStatusUpdate(RoomStatusUpdateRequest roomStatusUpdateRequest);
}
