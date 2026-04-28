package com.finalProject.BookingMeetingRoom.controller.reservation;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.ReservationSeriesCreateRequest;
import com.finalProject.BookingMeetingRoom.service.ReservationSeriesService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// start+ chức năng đặt phòng lặp lại (controller APIs)
@RestController
@RequestMapping("/api/v1/reservation-series")
@RequiredArgsConstructor
public class ReservationSeriesController {

    private final ReservationSeriesService reservationSeriesService;

    @PostMapping
    public ResponseEntity<?> createSeries(@Valid @RequestBody ReservationSeriesCreateRequest request, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(reservationSeriesService.createSeries(request, authentication)));
    }

    // start+ chức năng xem trước lịch đặt định kỳ
    @PostMapping("/preview")
    public ResponseEntity<?> previewSeries(@Valid @RequestBody ReservationSeriesCreateRequest request, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(reservationSeriesService.previewSeries(request, authentication)));
    }
    // end+ chức năng xem trước lịch đặt định kỳ

    @GetMapping("/my")
    public ResponseEntity<?> getMySeries(Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(reservationSeriesService.getMySeries(authentication)));
    }

    @PutMapping("/{seriesId}/sync")
    public ResponseEntity<?> syncNow(@PathVariable String seriesId, Authentication authentication) {
        reservationSeriesService.syncSeriesNow(seriesId, authentication);
        return ResponseEntity.ok(Response.ofSucceeded("Series synced successfully"));
    }

    @DeleteMapping("/{seriesId}")
    public ResponseEntity<?> cancelSeries(@PathVariable String seriesId, Authentication authentication) {
        reservationSeriesService.cancelSeries(seriesId, authentication);
        return ResponseEntity.ok(Response.ofSucceeded("Series cancelled successfully"));
    }
}
// end+ chức năng đặt phòng lặp lại (controller APIs)
