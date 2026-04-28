package com.finalProject.BookingMeetingRoom.service;

import org.springframework.security.core.Authentication;

import com.finalProject.BookingMeetingRoom.model.request.ConsumeQrTokenRequest;
import com.finalProject.BookingMeetingRoom.model.response.CheckInQrTokenResponse;

// start+ chức năng check-in bằng QR (service)
public interface CheckInQrService {
    CheckInQrTokenResponse generateReservationQr(String reservationId, Authentication authentication);
    CheckInQrTokenResponse generateParticipantQr(String participantId, Authentication authentication);
    // start+ chức năng đặt phòng theo sự kiện (OTP check-in cho người tham gia)
    CheckInQrTokenResponse generateParticipantOtp(String participantId, Authentication authentication);
    // end+ chức năng đặt phòng theo sự kiện (OTP check-in cho người tham gia)
    void consumeQr(ConsumeQrTokenRequest request, Authentication authentication);

    // start+ chức năng đặt phòng theo sự kiện (mã code 6 số thay đổi mỗi phút)
    CheckInQrTokenResponse getLiveEventCode(String reservationId, Authentication authentication);
    void checkInWithCode(String reservationId, String code, Authentication authentication);
    // end+ chức năng đặt phòng theo sự kiện (mã code 6 số thay đổi mỗi phút)
}
// end+ chức năng check-in bằng QR (service)
