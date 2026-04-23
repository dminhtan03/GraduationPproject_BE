package com.finalProject.BookingMeetingRoom.controller.checkin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/reservation/{reservationId}")
    public ResponseEntity<?> generateReservationQr(@PathVariable String reservationId, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(checkInQrService.generateReservationQr(reservationId, authentication)));
    }

    @PostMapping("/participant/{participantId}")
    public ResponseEntity<?> generateParticipantQr(@PathVariable String participantId, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(checkInQrService.generateParticipantQr(participantId, authentication)));
    }

    @PostMapping("/consume")
    public ResponseEntity<?> consume(@Valid @RequestBody ConsumeQrTokenRequest request, Authentication authentication) {
        checkInQrService.consumeQr(request, authentication);
        return ResponseEntity.ok(Response.ofSucceeded("QR check-in successful"));
    }
}
// end+ chức năng check-in bằng QR (controller APIs)
