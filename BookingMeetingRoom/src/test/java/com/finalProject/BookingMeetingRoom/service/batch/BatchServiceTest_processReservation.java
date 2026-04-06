package com.finalProject.BookingMeetingRoom.service.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest_processReservation {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SeatStatusUpdateService seatStatusUpdateService;

    @InjectMocks
    private BatchServiceImpl batchService;

    private Reservation reservation;
    private Seat seat;

    @BeforeEach
    void setUp() {
        seat = new Seat();
        seat.setId("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
        seat.setStatus(SeatStatus.AVAILABLE);

        reservation = new Reservation();
        reservation.setId("d5f2a2ae-711f-47f0-908c-da0f55411c69");
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setSeat(seat);
    }

    @Test
    void updateUserStatuses_ShouldExecuteAllStepsSuccessfully() {
        // Given
        when(reservationRepository.findReservationsOverStartTime()).thenReturn(Collections.emptyList());
        when(reservationRepository.findReservationsOverEndTime()).thenReturn(Collections.emptyList());

        // When
        batchService.updateUserStatuses();

        // Then
        verify(reservationRepository).findReservationsOverStartTime();
        verify(reservationRepository).findReservationsOverEndTime();
        verify(notificationService).remindCheckIn();
    }

    @Test
    void updateUserStatuses_ShouldThrowCustomExceptionWhenError() {
        // Given
        when(reservationRepository.findReservationsOverStartTime())
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> batchService.updateUserStatuses());

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
    }

    @Test
    void processReservation_NoShowStatus_ShouldUpdateReservationAndSeat() {
        // Given
        List<Reservation> reservations = Arrays.asList(reservation);
        when(reservationRepository.findReservationsOverStartTime()).thenReturn(reservations);

        // When
        batchService.processReservation(ReservationStatus.NO_SHOW);

        // Then
        assertEquals(ReservationStatus.NO_SHOW, reservation.getStatus());
        assertNotNull(reservation.getUpdatedAt());
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());

        verify(notificationService).noticeNoCheckInReservation(reservations);
        verify(seatRepository).save(seat);
        verify(reservationRepository).saveAll(reservations);
        verify(seatStatusUpdateService).sendRealTimeSeatStatusUpdate(any(SeatStatusUpdateRequest.class));
    }

    @Test
    void processReservation_CompletedStatus_ShouldUpdateReservationAndSeat() {
        // Given
        List<Reservation> reservations = Arrays.asList(reservation);
        when(reservationRepository.findReservationsOverEndTime()).thenReturn(reservations);

        // When
        batchService.processReservation(ReservationStatus.COMPLETED);

        // Then
        assertEquals(ReservationStatus.COMPLETED, reservation.getStatus());
        assertNotNull(reservation.getUpdatedAt());
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());

        verify(notificationService).noticeOverTimeReservation(reservations);
        verify(seatRepository).save(seat);
        verify(reservationRepository).saveAll(reservations);
        verify(seatStatusUpdateService).sendRealTimeSeatStatusUpdate(any(SeatStatusUpdateRequest.class));
    }

    @Test
    void processReservation_NoShowStatus_WithEmptyReservations_ShouldNotSendNotification() {
        // Given
        when(reservationRepository.findReservationsOverStartTime()).thenReturn(Collections.emptyList());

        // When
        batchService.processReservation(ReservationStatus.NO_SHOW);

        // Then
        verify(notificationService, never()).noticeNoCheckInReservation(any());
        verify(seatRepository, never()).save(any());
        verify(reservationRepository).saveAll(Collections.emptyList());
    }

    @Test
    void processReservation_CompletedStatus_WithEmptyReservations_ShouldNotSendNotification() {
        // Given
        when(reservationRepository.findReservationsOverEndTime()).thenReturn(Collections.emptyList());

        // When
        batchService.processReservation(ReservationStatus.COMPLETED);

        // Then
        verify(notificationService, never()).noticeOverTimeReservation(any());
        verify(seatRepository, never()).save(any());
        verify(reservationRepository).saveAll(Collections.emptyList());
    }

    @Test
    void processReservation_WithNullSeat_ShouldSkipSeatUpdate() {
        // Given
        reservation.setSeat(null);
        List<Reservation> reservations = Arrays.asList(reservation);
        when(reservationRepository.findReservationsOverStartTime()).thenReturn(reservations);

        // When
        batchService.processReservation(ReservationStatus.NO_SHOW);

        // Then
        assertEquals(ReservationStatus.NO_SHOW, reservation.getStatus());
        verify(seatRepository, never()).save(any());
        verify(seatStatusUpdateService, never()).sendRealTimeSeatStatusUpdate(any());
        verify(reservationRepository).saveAll(reservations);
    }

    @Test
    void processReservation_WithMultipleReservations_ShouldProcessAll() {
        // Given
        Seat seat2 = new Seat();
        seat2.setId("b2c3d4e5-f6g7-8h9i-j0k1-l2m3n4o5p6q7");
        seat2.setStatus(SeatStatus.AVAILABLE);

        Reservation reservation2 = new Reservation();
        reservation2.setId("e6f7g8h9-i0j1-k2l3-m4n5-o6p7q8r9s0t1");
        reservation2.setStatus(ReservationStatus.RESERVED);
        reservation2.setSeat(seat2);

        List<Reservation> reservations = Arrays.asList(reservation, reservation2);
        when(reservationRepository.findReservationsOverStartTime()).thenReturn(reservations);

        // When
        batchService.processReservation(ReservationStatus.NO_SHOW);

        // Then
        assertEquals(ReservationStatus.NO_SHOW, reservation.getStatus());
        assertEquals(ReservationStatus.NO_SHOW, reservation2.getStatus());
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
        assertEquals(SeatStatus.AVAILABLE, seat2.getStatus());

        verify(seatRepository, times(2)).save(any(Seat.class));
        verify(seatStatusUpdateService, times(2)).sendRealTimeSeatStatusUpdate(any(SeatStatusUpdateRequest.class));
        verify(reservationRepository).saveAll(reservations);
    }

    @Test
    void processReservation_ShouldSetCorrectSeatStatusUpdateRequest() {
        // Given
        List<Reservation> reservations = Arrays.asList(reservation);
        when(reservationRepository.findReservationsOverStartTime()).thenReturn(reservations);

        // When
        batchService.processReservation(ReservationStatus.NO_SHOW);

        // Then
        verify(seatStatusUpdateService).sendRealTimeSeatStatusUpdate(
                argThat(request ->
                        request.getSeatId().equals(seat.getId()) &&
                                request.getNewStatus().equals(SeatStatus.AVAILABLE)
                )
        );
    }

    @Test
    void processReservation_ShouldUpdateReservationTimestamp() {
        // Given
        LocalDateTime beforeUpdate = LocalDateTime.now().minusSeconds(1);
        List<Reservation> reservations = Arrays.asList(reservation);
        when(reservationRepository.findReservationsOverStartTime()).thenReturn(reservations);

        // When
        batchService.processReservation(ReservationStatus.NO_SHOW);

        // Then
        assertTrue(reservation.getUpdatedAt().isAfter(beforeUpdate));
    }
}