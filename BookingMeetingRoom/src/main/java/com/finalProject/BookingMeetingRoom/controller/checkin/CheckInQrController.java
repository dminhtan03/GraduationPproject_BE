package com.finalProject.BookingMeetingRoom.controller.checkin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.ConsumeQrTokenRequest;
import com.finalProject.BookingMeetingRoom.service.CheckInQrService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// start+ chức năng check-in bằng QR (controller APIs)
@RestController
@RequestMapping("/api/v1/checkin-qr")
@RequiredArgsConstructor
public class CheckInQrController {

    private final CheckInQrService checkInQrService;

    // start+ chức năng đặt phòng theo sự kiện (mã code 6 số thay đổi mỗi phút)
    @GetMapping("/live-code/{reservationId}")
    public ResponseEntity<?> getLiveEventCode(@PathVariable String reservationId, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(checkInQrService.getLiveEventCode(reservationId, authentication)));
    }

    @PostMapping("/check-in-code/{reservationId}")
    public ResponseEntity<?> checkInWithCode(@PathVariable String reservationId, @RequestParam String code, Authentication authentication) {
        checkInQrService.checkInWithCode(reservationId, code, authentication);
        return ResponseEntity.ok(Response.ofSucceeded("Check-in with code successful"));
    }
    // end+ chức năng đặt phòng theo sự kiện (mã code 6 số thay đổi mỗi phút)

    @PostMapping("/reservation/{reservationId}")
    public ResponseEntity<?> generateReservationQr(@PathVariable String reservationId, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(checkInQrService.generateReservationQr(reservationId, authentication)));
    }

    @PostMapping("/participant/{participantId}")
    public ResponseEntity<?> generateParticipantQr(@PathVariable String participantId, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(checkInQrService.generateParticipantQr(participantId, authentication)));
    }

    // start+ chức năng đặt phòng theo sự kiện (tạo OTP check-in cho người tham gia)
    @PostMapping("/participant/{participantId}/otp")
    public ResponseEntity<?> generateParticipantOtp(@PathVariable String participantId, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(checkInQrService.generateParticipantOtp(participantId, authentication)));
    }
    // end+ chức năng đặt phòng theo sự kiện (tạo OTP check-in cho người tham gia)

    @PostMapping("/consume")
    public ResponseEntity<?> consume(@Valid @RequestBody ConsumeQrTokenRequest request, Authentication authentication) {
        checkInQrService.consumeQr(request, authentication);
        return ResponseEntity.ok(Response.ofSucceeded("QR check-in successful"));
    }
}
// end+ chức năng check-in bằng QR (controller APIs)
