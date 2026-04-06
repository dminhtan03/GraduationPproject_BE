package com.finalProject.BookingMeetingRoom.service.admin;


import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest_forceReturn {
    @Mock
    private RoomRepository RoomRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomStatusUpdateService RoomStatusUpdateService;

    @InjectMocks
    private AdminServiceImpl adminService;

    @Mock
    private Authentication authentication;

    @Test
    void forceReturn_shouldForceCancelReservationAndFreeRoom() {
        // Given
        String RoomId = "Room123";
        String reservationId = "resv123";
        String email = "test@example.com";
        String userId = "user001";

        List<String> RoomIds = List.of(RoomId);

        Room Room = new Room();
        Room.setId(RoomId);
        Room.setStatus(RoomStatus.UNAVAILABLE);

        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setStatus(ReservationStatus.IN_USE);
        reservation.setRoom(Room);
        User user = new User();
        user.setId(userId);
        reservation.setUser(user);

        when(RoomRepository.findRoomsByRoomIds(RoomIds)).thenReturn(List.of(Room));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(reservationRepository.findByRoomIdAndStatus(RoomId, String.valueOf(ReservationStatus.IN_USE)))
                .thenReturn(Optional.of(reservation));
        when(authentication.getName()).thenReturn(email);

        // When
        adminService.forceReturn(RoomIds, authentication);

        // Then
        assertEquals(RoomStatus.AVAILABLE, Room.getStatus());
        assertEquals(ReservationStatus.FORCE_CANCELLED, reservation.getStatus());
        verify(RoomRepository).save(Room);
        verify(reservationRepository).save(reservation);

        verify(RoomStatusUpdateService).sendRealTimeRoomStatusUpdate(
                argThat(req -> req.getRoomId().equals(RoomId) && req.getNewStatus() == RoomStatus.AVAILABLE)
        );

//        verify(reservationStatusUpdateService).sendRealTimeReservationStatusUpdate(
//                argThat(req -> req.getReservationId().equals(reservationId)
//                        && req.getNewStatus() == ReservationStatus.FORCE_CANCELLED),
//                eq(userId)
//        );
    }

    @Test
    void forceReturn_shouldThrowWhenRoomNotFound() {
        // Given
        List<String> RoomIds = List.of("Room123");
        when(RoomRepository.findRoomsByRoomIds(RoomIds)).thenReturn(List.of());

        // When & Then
        CustomException ex = assertThrows(CustomException.class, () ->
                adminService.forceReturn(RoomIds, authentication));
        assertEquals(ResponseCode.Room_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void forceReturn_shouldThrowWhenUserNotFound() {
        // Given
        String email = "test@example.com";
        when(authentication.getName()).thenReturn(email);

        Room Room = new Room();
        Room.setId("Room123");

        when(RoomRepository.findRoomsByRoomIds(any())).thenReturn(List.of(Room));
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // When & Then
        CustomException ex = assertThrows(CustomException.class, () ->
                adminService.forceReturn(List.of("Room123"), authentication));
        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void forceReturn_shouldThrowWhenReservationNotFound() {
        // Given
        String email = "test@example.com";
        String RoomId = "Room123";

        Room Room = new Room();
        Room.setId(RoomId);

        User user = new User();

        when(RoomRepository.findRoomsByRoomIds(any())).thenReturn(List.of(Room));
        when(authentication.getName()).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(reservationRepository.findByRoomIdAndStatus(eq(RoomId), any())).thenReturn(Optional.empty());

        // When & Then
        CustomException ex = assertThrows(CustomException.class, () ->
                adminService.forceReturn(List.of(RoomId), authentication));
        assertEquals(ResponseCode.RESERVATION_NOT_FOUND, ex.getResponseCode());
    }
}
