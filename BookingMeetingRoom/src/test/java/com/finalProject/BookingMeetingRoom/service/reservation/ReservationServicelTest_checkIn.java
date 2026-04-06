package com.finalProject.BookingMeetingRoom.service.reservation;

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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private SeatRepository seatRepository;

    @Mock
    private ReservationMapperFacade reservationMapperFacade;

    @Mock
    private ReservationHistoryRepository reservationHistoryRepository;

    @Mock
    private SeatStatusUpdateService seatStatusUpdateService;

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
    private Seat seat;
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

        seat = new Seat();
        seat.setId("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
        seat.setStatus(SeatStatus.AVAILABLE);

        reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setStartTime(startTime);
        reservation.setSeat(seat);
        reservation.setUser(user);
    }

    // Helper method to mock validateReservationContext
    private void mockValidateReservationContext() {
        // Mock the authentication to return the user's email
        when(authentication.getName()).thenReturn(userInfo.getEmail());

        // Mock repository calls that validateReservationContext likely uses
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(userRepository.findByEmail(userInfo.getEmail())).thenReturn(Optional.of(user));

        when(seatRepository.findById(seat.getId())).thenReturn(Optional.of(seat));

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
        assertEquals(SeatStatus.UNAVAILABLE, seat.getStatus());

        verify(reservationHistoryService).saveHistory(eq(reservation), eq(user.getId()),
                eq(ReservationStatus.RESERVED), isNull(), reservation.getUpdatedAt());
        verify(reservationRepository).save(reservation);
        verify(seatRepository).save(seat);
        verify(seatStatusUpdateService).sendRealTimeSeatStatusUpdate(any(SeatStatusUpdateRequest.class));
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
        verify(seatRepository, never()).save(any());
    }

    @Test
    void checkIn_SeatNotAvailable_ShouldThrowException() {
        // Given
        seat.setStatus(SeatStatus.UNAVAILABLE);
        mockValidateReservationContext();

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.checkIn(reservationId, authentication));

        assertEquals(ResponseCode.SEAT_NOT_AVAILABLE, exception.getResponseCode());
        verify(reservationRepository, never()).save(any());
        verify(seatRepository, never()).save(any());
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
        verify(seatRepository, never()).save(any());
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
        verify(seatRepository).save(seat);
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
        when(seatRepository.findById(seat.getId())).thenReturn(Optional.of(seat));

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.checkIn(reservationId, authentication));

        assertEquals(ResponseCode.RESERVATION_USER_NOT_FOUND, exception.getResponseCode());

        // Ensure không gọi save hay update gì cả
        verify(reservationRepository, never()).save(any());
        verify(seatRepository, never()).save(any());
        verify(seatStatusUpdateService, never()).sendRealTimeSeatStatusUpdate(any());
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
    void checkIn_ShouldSendCorrectSeatStatusUpdate() {
        // Given
        reservation.setStartTime(currentTime.minusMinutes(1));
        mockValidateReservationContext();

        // When
        reservationService.checkIn(reservationId, authentication);

        // Then
        verify(seatStatusUpdateService).sendRealTimeSeatStatusUpdate(
                argThat(request ->
                        request.getSeatId().equals(seat.getId()) &&
                                request.getNewStatus().equals(SeatStatus.UNAVAILABLE)
                )
        );
    }

    // RETURN SEAT TESTS
    @Test
    void returnSeat_ValidReservation_ShouldSucceed() {
        // Given
        reservation.setStatus(ReservationStatus.IN_USE);
        seat.setStatus(SeatStatus.UNAVAILABLE);
        mockValidateReservationContext();

        // When
        reservationService.returnSeat(reservationId, authentication);

        // Then
        assertEquals(ReservationStatus.COMPLETED, reservation.getStatus());
        assertNotNull(reservation.getReturnTime());
        assertNotNull(reservation.getUpdatedAt());
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());

        verify(reservationRepository).save(reservation);
        verify(seatRepository).save(seat);
        verify(seatStatusUpdateService).sendRealTimeSeatStatusUpdate(any(SeatStatusUpdateRequest.class));
    }

    @Test
    void returnSeat_ReservationNotInUse_ShouldThrowException() {
        // Given
        reservation.setStatus(ReservationStatus.RESERVED);
        seat.setStatus(SeatStatus.UNAVAILABLE);
        mockValidateReservationContext();

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.returnSeat(reservationId, authentication));

        assertEquals(ResponseCode.RESERVATION_NOT_IN_USE, exception.getResponseCode());
        verify(reservationRepository, never()).save(any());
        verify(seatRepository, never()).save(any());
    }

    @Test
    void returnSeat_SeatNotUnavailable_ShouldThrowException() {
        // Given
        reservation.setStatus(ReservationStatus.IN_USE);
        seat.setStatus(SeatStatus.AVAILABLE);
        mockValidateReservationContext();

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.returnSeat(reservationId, authentication));

        assertEquals(ResponseCode.SEAT_NOT_UNAVAILABLE, exception.getResponseCode());
        verify(reservationRepository, never()).save(any());
        verify(seatRepository, never()).save(any());
    }

    @Test
    void returnSeat_UnexpectedException_ShouldThrowInternalServerError() {
        // Given
        reservation.setStatus(ReservationStatus.IN_USE);
        seat.setStatus(SeatStatus.UNAVAILABLE);
        mockValidateReservationContext();
        doThrow(new RuntimeException("Database error")).when(reservationRepository).save(any());

        // When & Then
        CustomException exception = assertThrows(CustomException.class,
                () -> reservationService.returnSeat(reservationId, authentication));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
    }

    @Test
    void returnSeat_ShouldSendCorrectSeatStatusUpdate() {
        // Given
        reservation.setStatus(ReservationStatus.IN_USE);
        seat.setStatus(SeatStatus.UNAVAILABLE);
        mockValidateReservationContext();

        // When
        reservationService.returnSeat(reservationId, authentication);

        // Then
        verify(seatStatusUpdateService).sendRealTimeSeatStatusUpdate(
                argThat(request ->
                        request.getSeatId().equals(seat.getId()) &&
                                request.getNewStatus().equals(SeatStatus.AVAILABLE)
                )
        );
    }

    @Test
    void returnSeat_ShouldUpdateTimestamps() {
        // Given
        reservation.setStatus(ReservationStatus.IN_USE);
        seat.setStatus(SeatStatus.UNAVAILABLE);
        mockValidateReservationContext();
        LocalDateTime beforeReturn = LocalDateTime.now().minusSeconds(1);

        // When
        reservationService.returnSeat(reservationId, authentication);

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
    void returnSeat_CustomExceptionFromValidation_ShouldRethrow() {
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
                () -> reservationService.returnSeat(reservationId, authentication));

        assertEquals(ResponseCode.RESERVATION_NOT_FOUND, exception.getResponseCode());
    }

}