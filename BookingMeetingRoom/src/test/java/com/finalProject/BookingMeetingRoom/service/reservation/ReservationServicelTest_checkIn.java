package com.finalProject.BookingMeetingRoom.service.reservation;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapper;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapperFacade;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.request.RoomStatusUpdateRequest;
import com.finalProject.BookingMeetingRoom.repository.ReservationHistoryRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.RealTimeService;
import com.finalProject.BookingMeetingRoom.service.ReservationHistoryService;
import com.finalProject.BookingMeetingRoom.service.RoomStatusUpdateService;
import com.finalProject.BookingMeetingRoom.service.impl.ReservationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServicelTest_checkIn {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ReservationMapperFacade reservationMapperFacade;

    @Mock
    private ReservationHistoryRepository reservationHistoryRepository;

    @Mock
    private RoomStatusUpdateService roomStatusUpdateService;

    @Mock
    private ReservationHistoryService reservationHistoryService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    @Mock
    private RealTimeService realTimeService;

    private String reservationId;
    private Reservation reservation;
    private Room room;
    private User user;
    private UserInfo userInfo;
    private LocalDateTime currentTime;
    private LocalDateTime startTime;

    @BeforeEach
    void setUp() {
        reservationId = "d5f2a2ae-711f-47f0-908c-da0f55411c69";
        currentTime = LocalDateTime.now();
        startTime = currentTime.plusMinutes(30);

        // Create UserInfo first
        userInfo = new UserInfo();
        userInfo.setId(UUID.randomUUID().toString());
        userInfo.setEmail("haha@mail.com");
        userInfo.setFirstName("John");
        userInfo.setLastName("Doe");
        userInfo.setPhoneNumber("123-456-7890");

        // Create User with UserInfo
        user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUserInfo(userInfo);
        user.setEnabled(true);
        user.setLocked(false);

        room = new Room();
        room.setId("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
        room.setStatus(RoomStatus.AVAILABLE);

        reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setStartTime(startTime);
        reservation.setRoom(room);
        reservation.setUser(user);
    }

    // Helper method to mock validateReservationContext
    private void mockValidateReservationContext() {
        // Mock the authentication to return the user's email
        when(authentication.getName()).thenReturn(userInfo.getEmail());

        // Mock repository calls that validateReservationContext likely uses
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail(userInfo.getEmail())).thenReturn(Optional.of(user));

        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        // Also try alternative repository method names that might be used
        when(userRepository.findByEmail(userInfo.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.findByEmail(userInfo.getEmail())).thenReturn(Optional.of(user));
    }

    // CHECK-IN TESTS
    @Test
    void checkIn_ValidReservation_ShouldSucceed() {
        // Given
        reservation.setStartTime(currentTime.minusMinutes(1)); // Start time is in the past
        mockValidateReservationContext();

        // When
        reservationService.checkIn(reservationId, authentication);

        // Then
        assertEquals(ReservationStatus.IN_USE, reservation.getStatus());
        assertNotNull(reservation.getCheckinTime());
        assertNotNull(reservation.getUpdatedAt());
        assertEquals(RoomStatus.UNAVAILABLE, room.getStatus());

        verify(reservationHistoryService).saveHistory(eq(reservation), eq(user.getId()),
                eq(ReservationStatus.RESERVED), isNull(), reservation.getUpdatedAt());
        verify(reservationRepository).save(reservation);
        verify(roomRepository).save(room);
        verify(roomStatusUpdateService).sendRealTimeRoomStatusUpdate(any(RoomStatusUpdateRequest.class));
        verify(authentication).getName(); // Verify authentication was used
    }



    @Test
    void checkIn_InvalidAuthentication_ShouldThrowUserNotFound() {
        // Given
        when(authentication.getName()).thenReturn("nonexistent@email.com");
        when(userRepository.findByEmail("nonexistent@email.com")).thenReturn(Optional.empty());

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.checkIn(reservationId, authentication));

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
    }


    @Test
    void checkIn_ReservationNotReserved_ShouldThrowException() {
        // Given
        reservation.setStatus(ReservationStatus.COMPLETED);
        mockValidateReservationContext();

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.checkIn(reservationId, authentication));

        assertEquals(ResponseCode.RESERVATION_NOT_RESERVED, exception.getResponseCode());
        verify(reservationRepository, never()).save(any());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void checkIn_RoomNotAvailable_ShouldThrowException() {
        // Given
        room.setStatus(RoomStatus.UNAVAILABLE);
        mockValidateReservationContext();

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.checkIn(reservationId, authentication));

        assertEquals(ResponseCode.ROOM_NOT_AVAILABLE, exception.getResponseCode());
        verify(reservationRepository, never()).save(any());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void checkIn_TooEarlyForCheckIn_ShouldThrowException() {
        // Given
        reservation.setStartTime(currentTime.plusMinutes(30)); // Start time is in the future
        mockValidateReservationContext();

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.checkIn(reservationId, authentication));

        assertEquals(ResponseCode.RESERVATION_NOT_TIME_CHECK_IN, exception.getResponseCode());
        verify(reservationRepository, never()).save(any());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void checkIn_ExactStartTime_ShouldSucceed() {
        // Given
        reservation.setStartTime(currentTime); // Exactly at start time
        mockValidateReservationContext();

        // When
        reservationService.checkIn(reservationId, authentication);

        // Then
        assertEquals(ReservationStatus.IN_USE, reservation.getStatus());
        verify(reservationRepository).save(reservation);
        verify(roomRepository).save(room);
    }

    @Test
    void checkIn_ReservationNotBelongToUser_ShouldThrowException() {
        // Given: user trong reservation khác user hiện tại
        User anotherUser = new User();
        anotherUser.setId(UUID.randomUUID().toString()); // khác ID với reservation.getUser().getId()

        when(authentication.getName()).thenReturn("fake@email.com");
        when(userRepository.findByEmail("fake@email.com")).thenReturn(Optional.of(anotherUser));

        // Reservation vẫn gán với `user` ở @BeforeEach
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.checkIn(reservationId, authentication));

        assertEquals(ResponseCode.RESERVATION_USER_NOT_FOUND, exception.getResponseCode());

        // Ensure không gọi save hay update gì cả
        verify(reservationRepository, never()).save(any());
        verify(roomRepository, never()).save(any());
        verify(roomStatusUpdateService, never()).sendRealTimeRoomStatusUpdate(any());
    }


    @Test
    void checkIn_UnexpectedException_ShouldThrowInternalServerError() {
        // Given
        reservation.setStartTime(LocalDateTime.now().minusMinutes(1)); // Make sure it's valid for check-in
        mockValidateReservationContext();

        doThrow(new RuntimeException("Database error"))
                .when(reservationRepository).save(any());

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.checkIn(reservationId, authentication));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
    }



    @Test
    void checkIn_ShouldSendCorrectRoomStatusUpdate() {
        // Given
        reservation.setStartTime(currentTime.minusMinutes(1));
        mockValidateReservationContext();

        // When
        reservationService.checkIn(reservationId, authentication);

        // Then
        verify(roomStatusUpdateService).sendRealTimeRoomStatusUpdate(
                argThat(request ->
                        request.getRoomId().equals(room.getId()) &&
                                request.getNewStatus().equals(RoomStatus.UNAVAILABLE)
                )
        );
    }

    // RETURN ROOM TESTS
    @Test
    void returnRoom_ValidReservation_ShouldSucceed() {
        // Given
        reservation.setStatus(ReservationStatus.IN_USE);
        room.setStatus(RoomStatus.UNAVAILABLE);
        mockValidateReservationContext();

        // When
        reservationService.returnRoom(reservationId, authentication);

        // Then
        assertEquals(ReservationStatus.COMPLETED, reservation.getStatus());
        assertNotNull(reservation.getReturnTime());
        assertNotNull(reservation.getUpdatedAt());
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());

        verify(reservationRepository).save(reservation);
        verify(roomRepository).save(room);
        verify(roomStatusUpdateService).sendRealTimeRoomStatusUpdate(any(RoomStatusUpdateRequest.class));
    }

    @Test
    void returnRoom_ReservationNotInUse_ShouldThrowException() {
        // Given
        reservation.setStatus(ReservationStatus.RESERVED);
        room.setStatus(RoomStatus.UNAVAILABLE);
        mockValidateReservationContext();

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.returnRoom(reservationId, authentication));

        assertEquals(ResponseCode.RESERVATION_NOT_IN_USE, exception.getResponseCode());
        verify(reservationRepository, never()).save(any());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void returnRoom_RoomNotUnavailable_ShouldThrowException() {
        // Given
        reservation.setStatus(ReservationStatus.IN_USE);
        room.setStatus(RoomStatus.AVAILABLE);
        mockValidateReservationContext();

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.returnRoom(reservationId, authentication));

        assertEquals(ResponseCode.ROOM_NOT_UNAVAILABLE, exception.getResponseCode());
        verify(reservationRepository, never()).save(any());
        verify(roomRepository, never()).save(any());
    }

    @Test
    void returnRoom_UnexpectedException_ShouldThrowInternalServerError() {
        // Given
        reservation.setStatus(ReservationStatus.IN_USE);
        room.setStatus(RoomStatus.UNAVAILABLE);
        mockValidateReservationContext();
        doThrow(new RuntimeException("Database error")).when(reservationRepository).save(any());

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.returnRoom(reservationId, authentication));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
    }

    @Test
    void returnRoom_ShouldSendCorrectRoomStatusUpdate() {
        // Given
        reservation.setStatus(ReservationStatus.IN_USE);
        room.setStatus(RoomStatus.UNAVAILABLE);
        mockValidateReservationContext();

        // When
        reservationService.returnRoom(reservationId, authentication);

        // Then
        verify(roomStatusUpdateService).sendRealTimeRoomStatusUpdate(
                argThat(request ->
                        request.getRoomId().equals(room.getId()) &&
                                request.getNewStatus().equals(RoomStatus.AVAILABLE)
                )
        );
    }

    @Test
    void returnRoom_ShouldUpdateTimestamps() {
        // Given
        reservation.setStatus(ReservationStatus.IN_USE);
        room.setStatus(RoomStatus.UNAVAILABLE);
        mockValidateReservationContext();
        LocalDateTime beforeReturn = LocalDateTime.now().minusSeconds(1);

        // When
        reservationService.returnRoom(reservationId, authentication);

        // Then
        assertTrue(reservation.getReturnTime().isAfter(beforeReturn));
        assertTrue(reservation.getUpdatedAt().isAfter(beforeReturn));
    }

    @Test
    void checkIn_ShouldUpdateTimestamps() {
        // Given
        reservation.setStartTime(currentTime.minusMinutes(1));
        mockValidateReservationContext();
        LocalDateTime beforeCheckIn = LocalDateTime.now().minusSeconds(1);

        // When
        reservationService.checkIn(reservationId, authentication);

        // Then
        assertTrue(reservation.getCheckinTime().isAfter(beforeCheckIn));
        assertTrue(reservation.getUpdatedAt().isAfter(beforeCheckIn));
    }

    @Test
    void checkIn_CustomExceptionFromValidation_ShouldRethrow() {
        // Given
        CustomException validationException = new CustomException(ResponseCode.RESERVATION_NOT_FOUND);

        // Giả lập user tồn tại
        when(authentication.getName()).thenReturn("user@email.com");
        when(userRepository.findByEmail("user@email.com"))
                .thenReturn(Optional.of(mock(User.class)));

        when(reservationRepository.findById(reservationId)).thenThrow(validationException);

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.checkIn(reservationId, authentication));

        assertEquals(ResponseCode.RESERVATION_NOT_FOUND, exception.getResponseCode());
    }



    @Test
    void returnRoom_CustomExceptionFromValidation_ShouldRethrow() {
        // Given
        CustomException validationException = new CustomException(ResponseCode.RESERVATION_NOT_FOUND);

        // Giả lập user tồn tại
        when(authentication.getName()).thenReturn("user@email.com");
        when(userRepository.findByEmail("user@email.com"))
                .thenReturn(Optional.of(mock(User.class)));

        // Giả lập lỗi khi tìm reservation
        when(reservationRepository.findById(reservationId)).thenThrow(validationException);

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.returnRoom(reservationId, authentication));

        assertEquals(ResponseCode.RESERVATION_NOT_FOUND, exception.getResponseCode());
    }

}