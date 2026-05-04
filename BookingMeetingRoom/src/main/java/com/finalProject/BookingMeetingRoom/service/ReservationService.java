package com.finalProject.BookingMeetingRoom.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.request.ReservationServiceItemsUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.response.AdminReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.MyReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationDetailResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationServiceItemResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationTimelineResponse;

public interface ReservationService {

        // [ADDED] Get all reservations for admin with filtering
        Page<AdminReservationResponse> getAllReservationsForAdmin(int page, int size, ReservationStatus status, String userName, String userEmail, String roomName, String floorName, String buildingName, LocalDateTime startDate, LocalDateTime endDate);

        void checkIn(String reservationId, Authentication authentication);

        void cancelReservation(String reservationId, String reason, Authentication connectedUser);

        ReservationResponse reserveRoom(ReservationRequest request, Authentication connectedUser);

        Page<ReservationResponse> getAllReservations(int page, int size);

        List<ReservationTimelineResponse> getReservationTimeline(String reservationId);

        Page<ReservationResponse> getReservationHistory(LocalDate startDate, LocalDate endDate, int page, int size,
                                                    Authentication connectedUser);

        Page<MyReservationResponse> getReservationStatus(int page, int size, Authentication connectedUser,
                        String locationCode, String address, List<String> statuses,
                        String buildingId, String startTime, String endTime);

        void extendReservation(String reservationId, double hour, Authentication connectedUser);

        void returnRoom(String reservationId, Authentication authentication);

        ReservationDetailResponse getReservationDetail(String reservationId, Authentication authentication);
        
        void forceCancelReservation(String reservationId, String reason, Authentication adminUser);

        // start+ chức năng sự kiện (gọi thêm dịch vụ/tiện ích trong lúc diễn ra)
        List<ReservationServiceItemResponse> getReservationServiceItems(String reservationId, Authentication authentication);
        void updateReservationServiceItems(String reservationId, ReservationServiceItemsUpdateRequest request, Authentication authentication);
        // start+ chức năng service item status
        void updateServiceItemStatus(String reservationId, String itemId, String status, String reason, Authentication authentication);
        // end+ chức năng service item status
        // end+ chức năng sự kiện (gọi thêm dịch vụ/tiện ích trong lúc diễn ra)
}
