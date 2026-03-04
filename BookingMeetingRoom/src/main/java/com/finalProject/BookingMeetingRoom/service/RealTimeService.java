package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.ReservationStatusUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomReserveStatusUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomStatusUpdateRequest;

public interface RealTimeService {
    void sendRealTimeReservationStatusUpdate(ReservationStatusUpdateRequest reservationStatusUpdateRequest, String userId);

    void sendRealTimeRoomStatusUpdate(RoomStatusUpdateRequest roomStatusUpdateRequest, String floorId);

    void sendRealTimeRoomReserveStatusUpdate(RoomReserveStatusUpdateRequest roomReserveStatusUpdateRequest, String floorId, String date);

    void addReservation(Reservation reservation);

    void deleteReservation(Reservation reservation);

    void sendReservationStatus(Reservation reservation);

    void sendRoomStatus(Room room);
}
