package com.finalProject.BookingMeetingRoom.controller.reservation;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.request.CancelReservationRequest;
import com.finalProject.BookingMeetingRoom.service.ReservationService;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PutMapping("/check-in/{reservationId}")
    public ResponseEntity<?> checkIn(@PathVariable String reservationId,
            Authentication authentication) {
        reservationService.checkIn(reservationId, authentication);
        return ResponseEntity.ok(Response.ofSucceeded("Check-in successful for reservation ID: " + reservationId));
    }

    @PutMapping("/cancel/{id}")
    public ResponseEntity<?> cancelReservation(@PathVariable("id") String reservationId,
            @Valid @RequestBody CancelReservationRequest request,
            Authentication connectedUser) {
        reservationService.cancelReservation(reservationId, request.getReason(), connectedUser);
        return ResponseEntity.ok(Response.ofSucceeded("Reservation cancel successfully"));
    }

    @PutMapping("/extend/{id}")
    public ResponseEntity<?> extendReservation(@PathVariable("id") String reservationId,
                                               @RequestParam(name = "hour", defaultValue = "1") double hour,
                                               Authentication connectedUser) {
        reservationService.extendReservation(reservationId, hour, connectedUser);
        return ResponseEntity.ok(Response.ofSucceeded("Reservation extend successfully"));
    }

    @PutMapping("/return-room/{reservationId}")
    public ResponseEntity<?> returnRoom(@PathVariable String reservationId,
                                        Authentication authentication) {
        reservationService.returnRoom(reservationId, authentication);
        return ResponseEntity.ok(Response.ofSucceeded("Room returned successfully for reservation ID: " + reservationId));
    }


    @PostMapping
    public ResponseEntity<?> createReservation(@RequestBody @Valid ReservationRequest request,
                                               Authentication authentication) {
        return ResponseEntity.ok((reservationService.reserveRoom(request, authentication)));
    }

    @GetMapping
    public ResponseEntity<?> getAllReservations(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(Response.ofSucceeded(reservationService.getAllReservations(page, size)));
    }

    @PutMapping("/detail-reservation/{reservationId}")
    public ResponseEntity<?> getReservationDetail(@PathVariable("reservationId") String reservationId,
                                                  Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(reservationService.getReservationDetail(reservationId, authentication)));
    }

    @GetMapping("my-status")
    public ResponseEntity<?> getReservationStatus(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "5") int size,
                                                  Authentication authentication,
                                                  @Nullable @RequestParam(name = "locationCode", required = false) String locationCode,
                                                  @Nullable @RequestParam(name = "address", required = false) String address,
                                                  @Nullable @RequestParam(name = "statuses", required = false) List<String> statuses,
                                                  @Nullable @RequestParam(name = "buildingId", required = false) String buildingId,
                                                  @Nullable @RequestParam(name = "startTime", required = false) String startTime,
                                                  @Nullable @RequestParam(name = "endTime", required = false) String endTime
    ) {
        return ResponseEntity.ok(Response.ofSucceeded(reservationService.getReservationStatus(
                page, size, authentication, locationCode, address, statuses, buildingId, startTime, endTime)
        ));
    }

    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    @PutMapping("/force-cancel/{id}")
    public ResponseEntity<?> forceCancelReservation(@PathVariable("id") String reservationId,
            @Valid @RequestBody CancelReservationRequest request,
            Authentication adminUser) {
        reservationService.forceCancelReservation(reservationId, request.getReason(), adminUser);
        return ResponseEntity.ok(Response.ofSucceeded("Reservation force cancelled successfully and user has been notified"));
    }

}