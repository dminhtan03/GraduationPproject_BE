package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.MyReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationDetailResponse;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ReservationService {

        void checkIn(String reservationId, Authentication authentication);

        void cancelReservation(String reservationId, String reason, Authentication connectedUser);

        ReservationResponse reserveRoom(ReservationRequest request, Authentication connectedUser);

        Page<ReservationResponse> getAllReservations(int page, int size);

        Page<MyReservationResponse> getReservationStatus(int page, int size, Authentication connectedUser,
                        String locationCode, String address, List<String> statuses,
                        String buildingId, String startTime, String endTime);

        void extendReservation(String reservationId, double hour, Authentication connectedUser);

        void returnRoom(String reservationId, Authentication authentication);

        ReservationDetailResponse getReservationDetail(String reservationId, Authentication authentication);
        
        void forceCancelReservation(String reservationId, String reason, Authentication adminUser);
}
