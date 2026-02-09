package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.common.enums.HistoryAction;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationHistoryService {

    void saveHistory(Reservation reservation, String userId, ReservationStatus oldStatus, HistoryAction action
            , LocalDateTime performAt);

    void saveAllHistories(List<Reservation> reservations, String userId, HistoryAction action);

}