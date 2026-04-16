package com.finalProject.BookingMeetingRoom.service.batch;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.RoomStatusUpdateRequest;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.NotificationService;
import com.finalProject.BookingMeetingRoom.service.RoomStatusUpdateService;
import com.finalProject.BookingMeetingRoom.service.impl.BatchServiceImpl;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest_processReservation {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RoomStatusUpdateService roomStatusUpdateService;

    @InjectMocks
    private BatchServiceImpl batchService;

    private Reservation reservation;
    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room();
        room.setId("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
        room.setStatus(RoomStatus.AVAILABLE);

        reservation = new Reservation();
        reservation.setId("d5f2a2ae-711f-47f0-908c-da0f55411c69");
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setRoom(room);
    }

    @Test
    void updateUserStatuses_ShouldExecuteAllStepsSuccessfully() {
        LocalDateTime current = LocalDateTime.now();
        // Given
        when(reservationRepository.findReservationsOverStartTime(current)).thenReturn(Collections.emptyList());
        when(reservationRepository.findReservationsOverEndTime(current)).thenReturn(Collections.emptyList());

        // When
        batchService.updateUserStatuses();

        // Then
        verify(reservationRepository).findReservationsOverStartTime(current);
        verify(reservationRepository).findReservationsOverEndTime(current);
        verify(notificationService).remindCheckIn();
    }

    @Test
    void updateUserStatuses_ShouldThrowCustomExceptionWhenError() {
        LocalDateTime current = LocalDateTime.now();
        // Given
        when(reservationRepository.findReservationsOverStartTime(current))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> batchService.updateUserStatuses());

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
    }

    @Test
    void processReservation_NoShowStatus_ShouldUpdateReservationAndRoom() {
        LocalDateTime current = LocalDateTime.now();
        // Given
        List<Reservation> reservations = Arrays.asList(reservation);
        when(reservationRepository.findReservationsOverStartTime(current)).thenReturn(reservations);

        // When
        batchService.processReservation(ReservationStatus.NO_SHOW);

        // Then
        assertEquals(ReservationStatus.NO_SHOW, reservation.getStatus());
        assertNotNull(reservation.getUpdatedAt());
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());

        verify(notificationService).noticeNoCheckInReservation(reservations);
        verify(roomRepository).save(room);
        verify(reservationRepository).saveAll(reservations);
        verify(roomStatusUpdateService).sendRealTimeRoomStatusUpdate(any(RoomStatusUpdateRequest.class));
    }

    @Test
    void processReservation_CompletedStatus_ShouldUpdateReservationAndRoom() {
        LocalDateTime current = LocalDateTime.now();
        // Given
        List<Reservation> reservations = Arrays.asList(reservation);
        when(reservationRepository.findReservationsOverEndTime(current)).thenReturn(reservations);

        // When
        batchService.processReservation(ReservationStatus.COMPLETED);

        // Then
        assertEquals(ReservationStatus.COMPLETED, reservation.getStatus());
        assertNotNull(reservation.getUpdatedAt());
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());

        verify(notificationService).noticeOverTimeReservation(reservations);
        verify(roomRepository).save(room);
        verify(reservationRepository).saveAll(reservations);
        verify(roomStatusUpdateService).sendRealTimeRoomStatusUpdate(any(RoomStatusUpdateRequest.class));
    }

    @Test
    void processReservation_NoShowStatus_WithEmptyReservations_ShouldNotSendNotification() {
        LocalDateTime current = LocalDateTime.now();
        // Given
        when(reservationRepository.findReservationsOverStartTime(current)).thenReturn(Collections.emptyList());

        // When
        batchService.processReservation(ReservationStatus.NO_SHOW);

        // Then
        verify(notificationService, never()).noticeNoCheckInReservation(any());
        verify(roomRepository, never()).save(any());
        verify(reservationRepository).saveAll(Collections.emptyList());
    }

    @Test
    void processReservation_CompletedStatus_WithEmptyReservations_ShouldNotSendNotification() {
        LocalDateTime current = LocalDateTime.now();
        // Given
        when(reservationRepository.findReservationsOverEndTime(current)).thenReturn(Collections.emptyList());

        // When
        batchService.processReservation(ReservationStatus.COMPLETED);

        // Then
        verify(notificationService, never()).noticeOverTimeReservation(any());
        verify(roomRepository, never()).save(any());
        verify(reservationRepository).saveAll(Collections.emptyList());
    }

    @Test
    void processReservation_WithNullRoom_ShouldSkipRoomUpdate() {
        LocalDateTime current = LocalDateTime.now();
        // Given
        reservation.setRoom(null);
        List<Reservation> reservations = Arrays.asList(reservation);
        when(reservationRepository.findReservationsOverStartTime(current)).thenReturn(reservations);

        // When
        batchService.processReservation(ReservationStatus.NO_SHOW);

        // Then
        assertEquals(ReservationStatus.NO_SHOW, reservation.getStatus());
        verify(roomRepository, never()).save(any());
        verify(roomStatusUpdateService, never()).sendRealTimeRoomStatusUpdate(any());
        verify(reservationRepository).saveAll(reservations);
    }

    @Test
    void processReservation_WithMultipleReservations_ShouldProcessAll() {
        LocalDateTime current = LocalDateTime.now();
        // Given
        Room room2 = new Room();
        room2.setId("b2c3d4e5-f6g7-8h9i-j0k1-l2m3n4o5p6q7");
        room2.setStatus(RoomStatus.AVAILABLE);

        Reservation reservation2 = new Reservation();
        reservation2.setId("e6f7g8h9-i0j1-k2l3-m4n5-o6p7q8r9s0t1");
        reservation2.setStatus(ReservationStatus.RESERVED);
        reservation2.setRoom(room2);

        List<Reservation> reservations = Arrays.asList(reservation, reservation2);
        when(reservationRepository.findReservationsOverStartTime(current)).thenReturn(reservations);

        // When
        batchService.processReservation(ReservationStatus.NO_SHOW);

        // Then
        assertEquals(ReservationStatus.NO_SHOW, reservation.getStatus());
        assertEquals(ReservationStatus.NO_SHOW, reservation2.getStatus());
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());
        assertEquals(RoomStatus.AVAILABLE, room2.getStatus());

        verify(roomRepository, times(2)).save(any(Room.class));
        verify(roomStatusUpdateService, times(2)).sendRealTimeRoomStatusUpdate(any(RoomStatusUpdateRequest.class));
        verify(reservationRepository).saveAll(reservations);
    }

    @Test
    void processReservation_ShouldSetCorrectRoomStatusUpdateRequest() {
        LocalDateTime current = LocalDateTime.now();
        // Given
        List<Reservation> reservations = Arrays.asList(reservation);
        when(reservationRepository.findReservationsOverStartTime(current)).thenReturn(reservations);

        // When
        batchService.processReservation(ReservationStatus.NO_SHOW);

        // Then
        verify(roomStatusUpdateService).sendRealTimeRoomStatusUpdate(
                argThat(request ->
                        request.getRoomId().equals(room.getId()) &&
                                request.getNewStatus().equals(RoomStatus.AVAILABLE)
                )
        );
    }

    @Test
    void processReservation_ShouldUpdateReservationTimestamp() {
        LocalDateTime current = LocalDateTime.now();
        // Given
        LocalDateTime beforeUpdate = LocalDateTime.now().minusSeconds(1);
        List<Reservation> reservations = Arrays.asList(reservation);
        when(reservationRepository.findReservationsOverStartTime(current)).thenReturn(reservations);

        // When
        batchService.processReservation(ReservationStatus.NO_SHOW);

        // Then
        assertTrue(reservation.getUpdatedAt().isAfter(beforeUpdate));
    }
}