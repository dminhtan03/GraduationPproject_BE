package com.finalProject.BookingMeetingRoom.service.admin;

import com.finalProject.BookingMeetingRoom.service.impl.AdminServiceImpl;
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
import com.finalProject.BookingMeetingRoom.service.NotificationService;
import com.finalProject.BookingMeetingRoom.service.RealTimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RealTimeService realTimeService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AdminServiceImpl adminService;

    @Test
    void forceReturn_shouldThrowRoomNotFound_whenNoRoomsFound() {
        when(roomRepository.findRoomsByRoomIds(List.of("room-1"))).thenReturn(List.of());

        CustomException ex = assertThrows(
                CustomException.class,
                () -> adminService.forceReturn(List.of("room-1"), authentication)
        );

        assertEquals(ResponseCode.ROOM_NOT_FOUND, ex.getResponseCode());
        verify(notificationService, never()).noticeForceCancelReservation(any());
    }

    @Test
    void forceReturn_shouldThrowUserNotFound_whenConnectedUserMissing() {
        Room room = new Room();
        room.setId("room-1");

        when(roomRepository.findRoomsByRoomIds(List.of("room-1"))).thenReturn(List.of(room));
        when(authentication.getName()).thenReturn("admin@mail.com");
        when(userRepository.findByEmail("admin@mail.com")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(
                CustomException.class,
                () -> adminService.forceReturn(List.of("room-1"), authentication)
        );

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void forceReturn_shouldSkipRoomWithoutActiveReservation() {
        Room room = new Room();
        room.setId("room-1");

        User user = new User();
        user.setId("admin-1");

        when(roomRepository.findRoomsByRoomIds(List.of("room-1"))).thenReturn(List.of(room));
        when(authentication.getName()).thenReturn("admin@mail.com");
        when(userRepository.findByEmail("admin@mail.com")).thenReturn(Optional.of(user));
        when(reservationRepository.findByIdAndStatus("room-1", ReservationStatus.IN_USE)).thenReturn(Optional.empty());

        adminService.forceReturn(List.of("room-1"), authentication);

        verify(roomRepository, never()).save(any(Room.class));
        verify(reservationRepository, never()).save(any(Reservation.class));

        ArgumentCaptor<List<Reservation>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).noticeForceCancelReservation(listCaptor.capture());
        assertEquals(0, listCaptor.getValue().size());
    }

    @Test
    void forceReturn_shouldUpdateRoomAndReservation_whenActiveReservationFound() {
        Room room = new Room();
        room.setId("room-1");
        room.setStatus(RoomStatus.UNAVAILABLE);

        User user = new User();
        user.setId("admin-1");

        Reservation reservation = new Reservation();
        reservation.setId("res-1");
        reservation.setStatus(ReservationStatus.IN_USE);

        when(roomRepository.findRoomsByRoomIds(List.of("room-1"))).thenReturn(List.of(room));
        when(authentication.getName()).thenReturn("admin@mail.com");
        when(userRepository.findByEmail("admin@mail.com")).thenReturn(Optional.of(user));
        when(reservationRepository.findByIdAndStatus("room-1", ReservationStatus.IN_USE)).thenReturn(Optional.of(reservation));

        adminService.forceReturn(List.of("room-1"), authentication);

        assertEquals(RoomStatus.AVAILABLE, room.getStatus());
        assertEquals(ReservationStatus.FORCE_CANCELLED, reservation.getStatus());

        verify(roomRepository).save(room);
        verify(reservationRepository).save(reservation);
        verify(realTimeService).sendRoomStatus(room);
        verify(realTimeService).sendReservationStatus(reservation);
        verify(realTimeService).deleteReservation(reservation);

        ArgumentCaptor<List<Reservation>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).noticeForceCancelReservation(listCaptor.capture());
        assertEquals(1, listCaptor.getValue().size());
        assertEquals("res-1", listCaptor.getValue().get(0).getId());
    }

    @Test
    void forceReturn_shouldThrowInternalServerError_whenUnexpectedExceptionOccurs() {
        doThrow(new RuntimeException("boom")).when(roomRepository).findRoomsByRoomIds(any());

        CustomException ex = assertThrows(
                CustomException.class,
                () -> adminService.forceReturn(List.of("room-1"), authentication)
        );

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }
}


