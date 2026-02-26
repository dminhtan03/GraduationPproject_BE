package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

public interface ReservationService {


    ReservationResponse reserveRoom(ReservationRequest request, Authentication connectedUser);
    Page<ReservationResponse> getAllReservations(int page, int size);
}
