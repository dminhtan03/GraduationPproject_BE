package com.finalProject.BookingMeetingRoom.service.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest_getReservationStatus {

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
    private MyReservationProjection testProjection;
    private MyReservationResponse testReservationResponse;
    private Page<MyReservationProjection> testPage;
    private DateTimeFormatter formatter;

    @BeforeEach
    void setUp() {
        UserInfo userInfo = new UserInfo();
        userInfo.setEmail("test@example.com");

        testUser = new User();
        testUser.setId("user-123");
        testUser.setUserInfo(userInfo);

        // Mock MyReservationProjection
        testProjection = new MyReservationProjection() {
            @Override
            public String getReservationId() { return "reservation-123"; }
            @Override
            public String getLocationCode() { return "LOC123"; }
            @Override
            public String getBuildingName() { return "Building A"; }
            @Override
            public String getFloorName() { return "Floor 1"; }
            @Override
            public String getAddress() { return "Hanoi"; }
            @Override
            public String getReservationStatus() { return "RESERVED"; }
            @Override
            public LocalDate getSelectedDate() { return LocalDate.now(); }
            @Override
            public LocalDateTime getStartTime() { return LocalDateTime.now().plusHours(2); }
            @Override
            public LocalDateTime getEndTime() { return LocalDateTime.now().plusHours(3); }
            @Override
            public Double getDuration() { return 60.0; }
            @Override
            public Boolean getIsFeedback() { return false; }
        };

        testReservationResponse = new MyReservationResponse();
        testReservationResponse.setReservationId("reservation-123");
        testReservationResponse.setLocationCode("LOC123");
        testReservationResponse.setBuildingName("Building A");
        testReservationResponse.setFloorName("Floor 1");
        testReservationResponse.setReservationStatus("RESERVED");
        testReservationResponse.setSelectedDate(LocalDate.now());
        testReservationResponse.setStartTime(LocalDateTime.now().plusHours(2));
        testReservationResponse.setEndTime(LocalDateTime.now().plusHours(3));
        testReservationResponse.setDuration(60.0);

        List<MyReservationProjection> projections = Arrays.asList(testProjection);
        testPage = new PageImpl<>(projections, PageRequest.of(0, 10), 1);

        formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    }

    @Test
    void testGetReservationStatus_Success_WithStartAndEndTime() {
        // Arrange
        int page = 0;
        int size = 10;
        String startTime = "2025-07-31 00:00:00.000";
        String endTime = "2025-07-31 14:30:00.000";
        String effectiveStartTime = LocalDateTime.parse(startTime, formatter).with(LocalTime.MIN).format(formatter);
        String effectiveEndTime = LocalDateTime.parse(endTime, formatter).format(formatter);

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findMyReservations(
                eq(testUser.getId()),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(effectiveStartTime),
                eq(effectiveEndTime),
                any(Pageable.class)
        )).thenReturn(testPage);
        when(reservationMapperFacade.toMyResponse(testProjection)).thenReturn(testReservationResponse);

        // Act
        Page<MyReservationResponse> result = reservationService.getReservationStatus(
                page, size, connectedUser, null, null, null, null, startTime, endTime);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("reservation-123", result.getContent().get(0).getReservationId());
        assertEquals("LOC123", result.getContent().get(0).getLocationCode());
        assertEquals("Building A", result.getContent().get(0).getBuildingName());
        assertEquals("Floor 1", result.getContent().get(0).getFloorName());
        assertEquals("RESERVED", result.getContent().get(0).getReservationStatus());
        assertEquals(60.0, result.getContent().get(0).getDuration());

        verify(reservationRepository, times(1)).findMyReservations(
                eq(testUser.getId()),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(effectiveStartTime),
                eq(effectiveEndTime),
                any(Pageable.class)
        );
        verify(reservationMapperFacade, times(1)).toMyResponse(testProjection);
    }

    @Test
    void testGetReservationStatus_Success_WithOnlyStartTime() {
        // Arrange
        int page = 0;
        int size = 10;
        String startTime = "2025-07-31 00:00:00.000";
        String effectiveStartTime = LocalDateTime.parse(startTime, formatter).with(LocalTime.MIN).format(formatter);

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findMyReservations(
                eq(testUser.getId()),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(effectiveStartTime),
                isNull(),
                any(Pageable.class)
        )).thenReturn(testPage);
        when(reservationMapperFacade.toMyResponse(testProjection)).thenReturn(testReservationResponse);

        // Act
        Page<MyReservationResponse> result = reservationService.getReservationStatus(
                page, size, connectedUser, null, null, null, null, startTime, null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("reservation-123", result.getContent().get(0).getReservationId());

        verify(reservationRepository, times(1)).findMyReservations(
                eq(testUser.getId()),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(effectiveStartTime),
                isNull(),
                any(Pageable.class)
        );
        verify(reservationMapperFacade, times(1)).toMyResponse(testProjection);
    }

    @Test
    void testGetReservationStatus_Success_WithNoTime_ActiveStatuses() {
        // Arrange
        int page = 0;
        int size = 10;
        String effectiveStartTime = LocalDate.now().atStartOfDay().format(formatter);
        String effectiveEndTime = LocalDate.now().atTime(LocalTime.MAX).format(formatter);
        List<String> statuses = List.of("RESERVED");

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findMyReservations(
                eq(testUser.getId()),
                isNull(),
                isNull(),
                eq(statuses),
                isNull(),
                eq(effectiveStartTime),
                eq(effectiveEndTime),
                any(Pageable.class)
        )).thenReturn(testPage);
        when(reservationMapperFacade.toMyResponse(testProjection)).thenReturn(testReservationResponse);

        // Act
        Page<MyReservationResponse> result = reservationService.getReservationStatus(
                page, size, connectedUser, null, null, statuses, null, null, null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("reservation-123", result.getContent().get(0).getReservationId());

        verify(reservationRepository, times(1)).findMyReservations(
                eq(testUser.getId()),
                isNull(),
                isNull(),
                eq(statuses),
                isNull(),
                eq(effectiveStartTime),
                eq(effectiveEndTime),
                any(Pageable.class)
        );
        verify(reservationMapperFacade, times(1)).toMyResponse(testProjection);
    }

    @Test
    void testGetReservationStatus_Success_WithFilters() {
        // Arrange
        int page = 0;
        int size = 10;
        String locationCode = "LOC123";
        String address = "123 Main St";
        List<String> statuses = List.of("RESERVED", "PENDING");
        String buildingId = "BLD456";
        String startTime = "2025-07-31 00:00:00.000";
        String endTime = "2025-07-31 14:30:00.000";
        String effectiveStartTime = LocalDateTime.parse(startTime, formatter).with(LocalTime.MIN).format(formatter);
        String effectiveEndTime = LocalDateTime.parse(endTime, formatter).format(formatter);

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findMyReservations(
                eq(testUser.getId()),
                eq(locationCode),
                eq(address),
                eq(statuses),
                eq(buildingId),
                eq(effectiveStartTime),
                eq(effectiveEndTime),
                any(Pageable.class)
        )).thenReturn(testPage);
        when(reservationMapperFacade.toMyResponse(testProjection)).thenReturn(testReservationResponse);

        // Act
        Page<MyReservationResponse> result = reservationService.getReservationStatus(
                page, size, connectedUser, locationCode, address, statuses, buildingId, startTime, endTime);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("reservation-123", result.getContent().get(0).getReservationId());
        assertEquals("LOC123", result.getContent().get(0).getLocationCode());

        verify(reservationRepository, times(1)).findMyReservations(
                eq(testUser.getId()),
                eq(locationCode),
                eq(address),
                eq(statuses),
                eq(buildingId),
                eq(effectiveStartTime),
                eq(effectiveEndTime),
                any(Pageable.class)
        );
        verify(reservationMapperFacade, times(1)).toMyResponse(testProjection);
    }

    @Test
    void testGetReservationStatus_UserNotFound_ThrowsRuntimeException() {
        // Arrange
        int page = 0;
        int size = 10;

        when(connectedUser.getName()).thenReturn("nonexistent@example.com");
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            reservationService.getReservationStatus(page, size, connectedUser, null, null, null, null, null, null);
        });

        verify(reservationRepository, never()).findMyReservations(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testGetReservationStatus_RepositoryThrowsCustomException() {
        // Arrange
        int page = 0;
        int size = 10;
        String startTime = "2025-07-31 00:00:00.000";
        String endTime = "2025-07-31 14:30:00.000";
        String effectiveStartTime = LocalDateTime.parse(startTime, formatter).with(LocalTime.MIN).format(formatter);
        String effectiveEndTime = LocalDateTime.parse(endTime, formatter).format(formatter);

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findMyReservations(
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)
        )).thenThrow(new CustomException(ResponseCode.INTERNAL_SERVER_ERROR));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.getReservationStatus(page, size, connectedUser, null, null, null, null, startTime, endTime);
        });
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(reservationRepository, times(1)).findMyReservations(
                eq(testUser.getId()),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(effectiveStartTime),
                eq(effectiveEndTime),
                any(Pageable.class)
        );
        verify(reservationMapperFacade, never()).toMyResponse(any());
    }
}
