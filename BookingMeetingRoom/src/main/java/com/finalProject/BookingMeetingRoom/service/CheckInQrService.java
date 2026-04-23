package com.finalProject.BookingMeetingRoom.service;

import org.springframework.security.core.Authentication;

import com.finalProject.BookingMeetingRoom.model.request.ConsumeQrTokenRequest;
import com.finalProject.BookingMeetingRoom.model.response.CheckInQrTokenResponse;

// start+ chức năng check-in bằng QR (service)
public interface CheckInQrService {
    CheckInQrTokenResponse generateReservationQr(String reservationId, Authentication authentication);
    CheckInQrTokenResponse generateParticipantQr(String participantId, Authentication authentication);
    void consumeQr(ConsumeQrTokenRequest request, Authentication authentication);
}
// end+ chức năng check-in bằng QR (service)
