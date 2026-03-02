package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.MyReservationResponse;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ReservationService {


    ReservationResponse reserveRoom(ReservationRequest request, Authentication connectedUser);
    Page<ReservationResponse> getAllReservations(int page, int size);
    Page<MyReservationResponse> getReservationStatus(int page, int size, Authentication connectedUser, String locationCode, String address, List<String> statuses,
                                                     String buildingId, String startTime, String endTime);
}
