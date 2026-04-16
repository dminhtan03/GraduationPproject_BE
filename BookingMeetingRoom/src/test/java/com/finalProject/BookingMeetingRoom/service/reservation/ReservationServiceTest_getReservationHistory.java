package com.finalProject.BookingMeetingRoom.service.reservation;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapperFacade;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.impl.ReservationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest_getReservationHistory {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationMapperFacade reservationMapperFacade;

    @Mock
    private Authentication connectedUser;

    @Mock
    private Logger log;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    private User testUser;
    private Reservation testReservation;
    private ReservationResponse testReservationResponse;
    private Page<Reservation> testPage;

    @BeforeEach
    void setUp() {
        UserInfo userInfo = new UserInfo();
        userInfo.setEmail("test@example.com");

        testUser = new User();
        testUser.setId("user-123");
        testUser.setUserInfo(userInfo);

        testReservation = new Reservation();
        testReservation.setId("reservation-123");
        testReservation.setUser(testUser);
        testReservation.setStatus(ReservationStatus.COMPLETED);
        testReservation.setStartTime(LocalDateTime.now().minusDays(1));
        testReservation.setUpdatedAt(LocalDateTime.now());

        testReservationResponse = new ReservationResponse();
        testReservationResponse.setId("reservation-123");
        testReservationResponse.setStatus(ReservationStatus.COMPLETED);

        List<Reservation> reservations = Arrays.asList(testReservation);
        testPage = new PageImpl<>(reservations, PageRequest.of(0, 10), 1);
    }

    /**
     * Test successful reservation history retrieval with all parameters provided
     */
    @Test
    void testGetReservationHistory_Success_WithAllParameters() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        Pageable expectedPageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        when(reservationRepository.findByUserAndStatusInAndStartTimeBetween(
                eq(testUser),
                eq(List.of(ReservationStatus.NO_SHOW, ReservationStatus.FAILED,
                        ReservationStatus.CANCELLED, ReservationStatus.COMPLETED,
                        ReservationStatus.FORCE_CANCELLED)),
                eq(startDateTime),
                eq(endDateTime),
                eq(expectedPageable)
        )).thenReturn(testPage);

        when(reservationMapperFacade.toResponse(testReservation)).thenReturn(testReservationResponse);

        // Act
        Page<ReservationResponse> result = reservationService.getReservationHistory(
                startDate, endDate, page, size, connectedUser);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals("reservation-123", result.getContent().get(0).getId());
        assertEquals(ReservationStatus.COMPLETED, result.getContent().get(0).getStatus());

        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(reservationRepository, times(1)).findByUserAndStatusInAndStartTimeBetween(
                eq(testUser),
                eq(List.of(ReservationStatus.NO_SHOW, ReservationStatus.FAILED,
                        ReservationStatus.CANCELLED, ReservationStatus.COMPLETED,
                        ReservationStatus.FORCE_CANCELLED)),
                eq(startDateTime),
                eq(endDateTime),
                eq(expectedPageable)
        );
        verify(reservationMapperFacade, times(1)).toResponse(testReservation);
    }

    /**
     * Test successful reservation history with null start and end dates (default to last month)
     */
    @Test
    void testGetReservationHistory_Success_WithNullDates() {
        // Arrange
        LocalDate startDate = null;
        LocalDate endDate = null;
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        // Expected dates: last month
        LocalDate now = LocalDate.now();
        LocalDate expectedEndDate = now;
        LocalDate expectedStartDate = now.minusMonths(1);

        LocalDateTime startDateTime = expectedStartDate.atStartOfDay();
        LocalDateTime endDateTime = expectedEndDate.atTime(LocalTime.MAX);
        Pageable expectedPageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        when(reservationRepository.findByUserAndStatusInAndStartTimeBetween(
                eq(testUser),
                any(List.class),
                eq(startDateTime),
                eq(endDateTime),
                eq(expectedPageable)
        )).thenReturn(testPage);

        when(reservationMapperFacade.toResponse(testReservation)).thenReturn(testReservationResponse);

        // Act
        Page<ReservationResponse> result = reservationService.getReservationHistory(
                startDate, endDate, page, size, connectedUser);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());

        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(reservationRepository, times(1)).findByUserAndStatusInAndStartTimeBetween(
                eq(testUser),
                any(List.class),
                eq(startDateTime),
                eq(endDateTime),
                eq(expectedPageable)
        );
    }

    /**
     * Test successful reservation history with null start date (default to 1 month before end date)
     */
    @Test
    void testGetReservationHistory_Success_WithNullStartDate() {
        // Arrange
        LocalDate startDate = null;
        LocalDate endDate = LocalDate.of(2025, 1, 31);
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        LocalDate expectedStartDate = endDate.minusMonths(1);
        LocalDateTime startDateTime = expectedStartDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        Pageable expectedPageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        when(reservationRepository.findByUserAndStatusInAndStartTimeBetween(
                eq(testUser),
                any(List.class),
                eq(startDateTime),
                eq(endDateTime),
                eq(expectedPageable)
        )).thenReturn(testPage);

        when(reservationMapperFacade.toResponse(testReservation)).thenReturn(testReservationResponse);

        // Act
        Page<ReservationResponse> result = reservationService.getReservationHistory(
                startDate, endDate, page, size, connectedUser);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());

        verify(userRepository, times(1)).findByEmail("test@example.com");
    }

    /**
     * Test successful reservation history with null end date (default to 1 month after start date)
     */
    @Test
    void testGetReservationHistory_Success_WithNullEndDate() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = null;
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        LocalDate expectedEndDate = startDate.plusMonths(1);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = expectedEndDate.atTime(LocalTime.MAX);
        Pageable expectedPageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        when(reservationRepository.findByUserAndStatusInAndStartTimeBetween(
                eq(testUser),
                any(List.class),
                eq(startDateTime),
                eq(endDateTime),
                eq(expectedPageable)
        )).thenReturn(testPage);

        when(reservationMapperFacade.toResponse(testReservation)).thenReturn(testReservationResponse);

        // Act
        Page<ReservationResponse> result = reservationService.getReservationHistory(
                startDate, endDate, page, size, connectedUser);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());

        verify(userRepository, times(1)).findByEmail("test@example.com");
    }

    /**
     * Test successful reservation history with empty results
     */
    @Test
    void testGetReservationHistory_Success_WithEmptyResults() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        Page<Reservation> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(reservationRepository.findByUserAndStatusInAndStartTimeBetween(
                any(User.class),
                any(List.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(emptyPage);

        // Act
        Page<ReservationResponse> result = reservationService.getReservationHistory(
                startDate, endDate, page, size, connectedUser);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getContent().size());

        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(reservationRepository, times(1)).findByUserAndStatusInAndStartTimeBetween(
                any(User.class),
                any(List.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        );
        verify(reservationMapperFacade, never()).toResponse(any());
    }

    /**
     * Test when user is not found - should throw CustomException
     */
    @Test
    void testGetReservationHistory_UserNotFound_ThrowsCustomException() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn("nonexistent@example.com");
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.getReservationHistory(startDate, endDate, page, size, connectedUser);
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());

        verify(userRepository, times(1)).findByEmail("nonexistent@example.com");
        verify(reservationRepository, never()).findByUserAndStatusInAndStartTimeBetween(
                any(), any(), any(), any(), any());
        verify(reservationMapperFacade, never()).toResponse(any());
    }

    /**
     * Test when userRepository throws RuntimeException
     */
    @Test
    void testGetReservationHistory_UserRepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com"))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.getReservationHistory(startDate, endDate, page, size, connectedUser);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(reservationRepository, never()).findByUserAndStatusInAndStartTimeBetween(
                any(), any(), any(), any(), any());
    }

    /**
     * Test when reservationRepository throws RuntimeException
     */
    @Test
    void testGetReservationHistory_ReservationRepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findByUserAndStatusInAndStartTimeBetween(
                any(User.class),
                any(List.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.getReservationHistory(startDate, endDate, page, size, connectedUser);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(reservationRepository, times(1)).findByUserAndStatusInAndStartTimeBetween(
                any(User.class),
                any(List.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        );
    }

    /**
     * Test when reservationMapperFacade throws RuntimeException
     */
    @Test
    void testGetReservationHistory_MapperThrowsException_ThrowsInternalServerError() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findByUserAndStatusInAndStartTimeBetween(
                any(User.class),
                any(List.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(testPage);
        when(reservationMapperFacade.toResponse(testReservation))
                .thenThrow(new RuntimeException("Mapping error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.getReservationHistory(startDate, endDate, page, size, connectedUser);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(reservationMapperFacade, times(1)).toResponse(testReservation);
    }

    /**
     * Test when CustomException is thrown by repository - should be re-thrown
     */
    @Test
    void testGetReservationHistory_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        CustomException customException = new CustomException(ResponseCode.USER_NOT_FOUND);
        when(reservationRepository.findByUserAndStatusInAndStartTimeBetween(
                any(User.class),
                any(List.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.getReservationHistory(startDate, endDate, page, size, connectedUser);
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception); // Should be the exact same exception

        verify(log, never()).error(anyString(), any(Exception.class)); // Logger not called for CustomException
    }

    /**
     * Test with different page parameters
     */
    @Test
    void testGetReservationHistory_Success_WithDifferentPageParameters() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);
        int page = 2;
        int size = 20;

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        Pageable expectedPageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        when(reservationRepository.findByUserAndStatusInAndStartTimeBetween(
                any(User.class),
                any(List.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(expectedPageable)
        )).thenReturn(testPage);

        when(reservationMapperFacade.toResponse(testReservation)).thenReturn(testReservationResponse);

        // Act
        Page<ReservationResponse> result = reservationService.getReservationHistory(
                startDate, endDate, page, size, connectedUser);

        // Assert
        assertNotNull(result);

        verify(reservationRepository, times(1)).findByUserAndStatusInAndStartTimeBetween(
                eq(testUser),
                any(List.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(expectedPageable)
        );
    }

    /**
     * Test with multiple reservations and different statuses
     */
    @Test
    void testGetReservationHistory_Success_WithMultipleReservations() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);
        int page = 0;
        int size = 10;

        // Create multiple reservations with different statuses
        Reservation reservation1 = new Reservation();
        reservation1.setId("res1");
        reservation1.setStatus(ReservationStatus.COMPLETED);

        Reservation reservation2 = new Reservation();
        reservation2.setId("res2");
        reservation2.setStatus(ReservationStatus.FAILED);

        Reservation reservation3 = new Reservation();
        reservation3.setId("res3");
        reservation3.setStatus(ReservationStatus.CANCELLED);

        List<Reservation> reservations = Arrays.asList(reservation1, reservation2, reservation3);
        Page<Reservation> multiPage = new PageImpl<>(reservations, PageRequest.of(0, 10), 3);

        ReservationResponse response1 = new ReservationResponse();
        response1.setId("res1");
        response1.setStatus(ReservationStatus.COMPLETED);

        ReservationResponse response2 = new ReservationResponse();
        response2.setId("res2");
        response2.setStatus(ReservationStatus.FAILED);

        ReservationResponse response3 = new ReservationResponse();
        response3.setId("res3");
        response3.setStatus(ReservationStatus.CANCELLED);

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findByUserAndStatusInAndStartTimeBetween(
                any(User.class),
                any(List.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(multiPage);

        when(reservationMapperFacade.toResponse(reservation1)).thenReturn(response1);
        when(reservationMapperFacade.toResponse(reservation2)).thenReturn(response2);
        when(reservationMapperFacade.toResponse(reservation3)).thenReturn(response3);

        // Act
        Page<ReservationResponse> result = reservationService.getReservationHistory(
                startDate, endDate, page, size, connectedUser);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.getTotalElements());
        assertEquals(3, result.getContent().size());

        assertEquals("res1", result.getContent().get(0).getId());
        assertEquals("res2", result.getContent().get(1).getId());
        assertEquals("res3", result.getContent().get(2).getId());

        verify(reservationMapperFacade, times(3)).toResponse(any(Reservation.class));
    }

    /**
     * Test verification of reservation status filter
     */
    @Test
    void testGetReservationHistory_Success_VerifyStatusFilter() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findByUserAndStatusInAndStartTimeBetween(
                any(User.class),
                any(List.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(testPage);
        when(reservationMapperFacade.toResponse(testReservation)).thenReturn(testReservationResponse);

        // Act
        reservationService.getReservationHistory(startDate, endDate, page, size, connectedUser);

        // Assert - Verify the exact status list used
        verify(reservationRepository, times(1)).findByUserAndStatusInAndStartTimeBetween(
                eq(testUser),
                eq(List.of(ReservationStatus.NO_SHOW, ReservationStatus.FAILED,
                        ReservationStatus.CANCELLED, ReservationStatus.COMPLETED,
                        ReservationStatus.FORCE_CANCELLED)),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        );
    }

    /**
     * Test authentication name is null
     */
    @Test
    void testGetReservationHistory_AuthenticationNameIsNull_ThrowsCustomException() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 31);
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn(null);
        when(userRepository.findByEmail(null)).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.getReservationHistory(startDate, endDate, page, size, connectedUser);
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());

        verify(userRepository, times(1)).findByEmail(null);
    }
}