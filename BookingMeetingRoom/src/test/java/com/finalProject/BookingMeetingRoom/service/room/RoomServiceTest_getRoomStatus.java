package com.finalProject.BookingMeetingRoom.service.room;

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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest_getRoomStatus {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private Logger logger;

    @InjectMocks
    private SeatServiceImpl seatSearchService;

    private SeatSearchRequest validRequest;
    private Floor testFloor;
    private Seat availableSeat;
    private Seat brokenSeat;
    private Seat reservedSeat;
    private Reservation overlappingReservation;

    @BeforeEach
    void setUp() {
        // Setup test floor
        testFloor = new Floor();
        testFloor.setId("floor-123");
        testFloor.setName("Test Floor");

        // Setup test seats
        availableSeat = new Seat();
        availableSeat.setId("seat-available");
        availableSeat.setLocationCode("A1-01");
        availableSeat.setStatus(SeatStatus.AVAILABLE);
        availableSeat.setScore(85.5);

        brokenSeat = new Seat();
        brokenSeat.setId("seat-broken");
        brokenSeat.setLocationCode("A1-02");
        brokenSeat.setStatus(SeatStatus.BROKEN);
        brokenSeat.setScore(90.0);

        reservedSeat = new Seat();
        reservedSeat.setId("seat-reserved");
        reservedSeat.setLocationCode("A1-03");
        reservedSeat.setStatus(SeatStatus.AVAILABLE);
        reservedSeat.setScore(80.0);

        // Setup test reservation
        overlappingReservation = new Reservation();
        overlappingReservation.setId("reservation-123");
        overlappingReservation.setStatus(ReservationStatus.RESERVED);
        overlappingReservation.setStartTime(LocalDateTime.of(2025, 1, 15, 10, 0));
        overlappingReservation.setEndTime(LocalDateTime.of(2025, 1, 15, 12, 0));

        // Setup valid request
        validRequest = new SeatSearchRequest();
        validRequest.setFloorId("floor-123");
        validRequest.setStartTime(LocalDateTime.of(2025, 1, 15, 9, 0));
        validRequest.setEndTime(LocalDateTime.of(2025, 1, 15, 11, 0));
    }

    // ==================== getSeatsStatus Tests ====================

    /**
     * Test successful getSeatsStatus with available seats
     */
    @Test
    void testGetSeatsStatus_Success_WithAvailableSeats() {
        // Arrange
        List<Seat> seats = Arrays.asList(availableSeat);
        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor)).thenReturn(seats);
        when(reservationRepository.findOverlappingReservations(
                eq("seat-available"),
                eq(validRequest.getStartTime()),
                eq(validRequest.getEndTime())
        )).thenReturn(Collections.emptyList());

        // Act
        List<SeatSearchResponse> result = seatSearchService.getSeatsStatus(validRequest);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        SeatSearchResponse response = result.get(0);
        assertEquals("seat-available", response.getSeatId());
        assertEquals("A1-01", response.getLocationCode());
        assertEquals(85.5, response.getScore());
        assertEquals(SeatStatus.AVAILABLE, response.getStatus());

        verify(floorRepository, times(1)).findById("floor-123");
        verify(seatRepository, times(1)).findByFloorOrderByLocationCode(testFloor);
        verify(reservationRepository, times(1)).findOverlappingReservations(
                "seat-available", validRequest.getStartTime(), validRequest.getEndTime());
    }

    /**
     * Test successful getSeatsStatus with broken seat
     */
    @Test
    void testGetSeatsStatus_Success_WithBrokenSeat() {
        // Arrange
        List<Seat> seats = Arrays.asList(brokenSeat);
        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor)).thenReturn(seats);

        // Act
        List<SeatSearchResponse> result = seatSearchService.getSeatsStatus(validRequest);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        SeatSearchResponse response = result.get(0);
        assertEquals("seat-broken", response.getSeatId());
        assertEquals("A1-02", response.getLocationCode());
        assertEquals(90.0, response.getScore());
        assertEquals(SeatStatus.UNAVAILABLE, response.getStatus()); // Broken seat shows as UNAVAILABLE

        verify(floorRepository, times(1)).findById("floor-123");
        verify(seatRepository, times(1)).findByFloorOrderByLocationCode(testFloor);
        // Reservation query should not be called for broken seats
        verify(reservationRepository, never()).findOverlappingReservations(anyString(), any(), any());
    }

    /**
     * Test successful getSeatsStatus with reserved seat
     */
    @Test
    void testGetSeatsStatus_Success_WithReservedSeat() {
        // Arrange
        List<Seat> seats = Arrays.asList(reservedSeat);
        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor)).thenReturn(seats);
        when(reservationRepository.findOverlappingReservations(
                eq("seat-reserved"),
                eq(validRequest.getStartTime()),
                eq(validRequest.getEndTime())
        )).thenReturn(Arrays.asList(overlappingReservation));

        // Act
        List<SeatSearchResponse> result = seatSearchService.getSeatsStatus(validRequest);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        SeatSearchResponse response = result.get(0);
        assertEquals("seat-reserved", response.getSeatId());
        assertEquals("A1-03", response.getLocationCode());
        assertEquals(80.0, response.getScore());
        assertEquals(SeatStatus.UNAVAILABLE, response.getStatus()); // Reserved seat shows as UNAVAILABLE

        verify(reservationRepository, times(1)).findOverlappingReservations(
                "seat-reserved", validRequest.getStartTime(), validRequest.getEndTime());
    }

    /**
     * Test successful getSeatsStatus with mixed seat types
     */
    @Test
    void testGetSeatsStatus_Success_WithMixedSeatTypes() {
        // Arrange
        List<Seat> seats = Arrays.asList(availableSeat, brokenSeat, reservedSeat);
        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor)).thenReturn(seats);
        when(reservationRepository.findOverlappingReservations(
                eq("seat-available"), any(), any())).thenReturn(Collections.emptyList());
        when(reservationRepository.findOverlappingReservations(
                eq("seat-reserved"), any(), any())).thenReturn(Arrays.asList(overlappingReservation));

        // Act
        List<SeatSearchResponse> result = seatSearchService.getSeatsStatus(validRequest);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());

        // Check available seat
        SeatSearchResponse availableResponse = result.stream()
                .filter(r -> r.getSeatId().equals("seat-available"))
                .findFirst().orElseThrow();
        assertEquals(SeatStatus.AVAILABLE, availableResponse.getStatus());

        // Check broken seat
        SeatSearchResponse brokenResponse = result.stream()
                .filter(r -> r.getSeatId().equals("seat-broken"))
                .findFirst().orElseThrow();
        assertEquals(SeatStatus.UNAVAILABLE, brokenResponse.getStatus());

        // Check reserved seat
        SeatSearchResponse reservedResponse = result.stream()
                .filter(r -> r.getSeatId().equals("seat-reserved"))
                .findFirst().orElseThrow();
        assertEquals(SeatStatus.UNAVAILABLE, reservedResponse.getStatus());

        verify(reservationRepository, times(2)).findOverlappingReservations(anyString(), any(), any());
    }

    /**
     * Test getSeatsStatus with empty seat list
     */
    @Test
    void testGetSeatsStatus_Success_WithEmptySeats() {
        // Arrange
        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor)).thenReturn(Collections.emptyList());

        // Act
        List<SeatSearchResponse> result = seatSearchService.getSeatsStatus(validRequest);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());

        verify(floorRepository, times(1)).findById("floor-123");
        verify(seatRepository, times(1)).findByFloorOrderByLocationCode(testFloor);
    }

    /**
     * Test getSeatsStatus when floor is not found
     */
    @Test
    void testGetSeatsStatus_FloorNotFound_ThrowsCustomException() {
        // Arrange
        when(floorRepository.findById("nonexistent-floor")).thenReturn(Optional.empty());

        SeatSearchRequest invalidRequest = new SeatSearchRequest();
        invalidRequest.setFloorId("nonexistent-floor");
        invalidRequest.setStartTime(LocalDateTime.of(2025, 1, 15, 9, 0));
        invalidRequest.setEndTime(LocalDateTime.of(2025, 1, 15, 11, 0));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            seatSearchService.getSeatsStatus(invalidRequest);
        });

        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, times(1)).findById("nonexistent-floor");
        verify(seatRepository, never()).findByFloorOrderByLocationCode(any());
    }

    /**
     * Test getSeatsStatus when repository throws RuntimeException
     */
    @Test
    void testGetSeatsStatus_RepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            seatSearchService.getSeatsStatus(validRequest);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(floorRepository, times(1)).findById("floor-123");
        verify(seatRepository, times(1)).findByFloorOrderByLocationCode(testFloor);
        verify(logger, times(1)).error(anyString(), any(Exception.class));
    }

    // ==================== getSeatStatus (Paginated) Tests ====================

    /**
     * Test successful getSeatStatus with pagination
     */
    @Test
    void testGetSeatStatus_Success_WithPagination() {
        // Arrange
        int page = 0;
        int size = 10;
        List<Seat> seats = Arrays.asList(availableSeat);
        Pageable pageable = PageRequest.of(page, size);
        Page<Seat> seatPage = new PageImpl<>(seats, pageable, 1);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor, pageable)).thenReturn(seatPage);
        when(reservationRepository.findOverlappingReservations(
                eq("seat-available"), any(), any())).thenReturn(Collections.emptyList());

        // Act
        Page<SeatSearchResponse> result = seatSearchService.getSeatStatus(validRequest, page, size);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());

        SeatSearchResponse response = result.getContent().get(0);
        assertEquals("seat-available", response.getSeatId());
        assertEquals("A1-01", response.getLocationCode());
        assertEquals(85.5, response.getScore());
        assertEquals(SeatStatus.AVAILABLE, response.getStatus());

        verify(floorRepository, times(1)).findById("floor-123");
        verify(seatRepository, times(1)).findByFloorOrderByLocationCode(testFloor, pageable);
    }

    /**
     * Test getSeatStatus with different page parameters
     */
    @Test
    void testGetSeatStatus_Success_WithDifferentPageParameters() {
        // Arrange
        int page = 2;
        int size = 20;
        List<Seat> seats = Arrays.asList(availableSeat);
        Pageable pageable = PageRequest.of(page, size);
        Page<Seat> seatPage = new PageImpl<>(seats, pageable, 1);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor, pageable)).thenReturn(seatPage);
        when(reservationRepository.findOverlappingReservations(anyString(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        Page<SeatSearchResponse> result = seatSearchService.getSeatStatus(validRequest, page, size);

        // Assert
        assertNotNull(result);
        assertEquals(page, result.getNumber());
        assertEquals(size, result.getSize());

        verify(seatRepository, times(1)).findByFloorOrderByLocationCode(testFloor, pageable);
    }

    /**
     * Test getSeatStatus when repository throws RuntimeException
     */
    @Test
    void testGetSeatStatus_RepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        int page = 0;
        int size = 10;
        Pageable pageable = PageRequest.of(page, size);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor, pageable))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            seatSearchService.getSeatStatus(validRequest, page, size);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(logger, times(1)).error(anyString(), any(Exception.class));
    }

    // ==================== validateAndGetContext Tests ====================

    /**
     * Test validateAndGetContext with null start time
     */
    @Test
    void testValidateAndGetContext_NullStartTime_ThrowsCustomException() {
        // Arrange
        SeatSearchRequest invalidRequest = new SeatSearchRequest();
        invalidRequest.setFloorId("floor-123");
        invalidRequest.setStartTime(null);
        invalidRequest.setEndTime(LocalDateTime.of(2025, 1, 15, 11, 0));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            seatSearchService.getSeatsStatus(invalidRequest);
        });

        assertEquals(ResponseCode.SEAT_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, never()).findById(anyString());
    }

    /**
     * Test validateAndGetContext with null end time
     */
    @Test
    void testValidateAndGetContext_NullEndTime_ThrowsCustomException() {
        // Arrange
        SeatSearchRequest invalidRequest = new SeatSearchRequest();
        invalidRequest.setFloorId("floor-123");
        invalidRequest.setStartTime(LocalDateTime.of(2025, 1, 15, 9, 0));
        invalidRequest.setEndTime(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            seatSearchService.getSeatsStatus(invalidRequest);
        });

        assertEquals(ResponseCode.SEAT_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, never()).findById(anyString());
    }

    /**
     * Test validateAndGetContext with start time after end time
     */
    @Test
    void testValidateAndGetContext_StartTimeAfterEndTime_ThrowsCustomException() {
        // Arrange
        SeatSearchRequest invalidRequest = new SeatSearchRequest();
        invalidRequest.setFloorId("floor-123");
        invalidRequest.setStartTime(LocalDateTime.of(2025, 1, 15, 12, 0));
        invalidRequest.setEndTime(LocalDateTime.of(2025, 1, 15, 10, 0));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            seatSearchService.getSeatsStatus(invalidRequest);
        });

        assertEquals(ResponseCode.SEAT_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, never()).findById(anyString());
    }

    /**
     * Test validateAndGetContext with equal start and end time
     */
    @Test
    void testValidateAndGetContext_EqualStartAndEndTime_ThrowsCustomException() {
        // Arrange
        LocalDateTime sameTime = LocalDateTime.of(2025, 1, 15, 10, 0);
        SeatSearchRequest invalidRequest = new SeatSearchRequest();
        invalidRequest.setFloorId("floor-123");
        invalidRequest.setStartTime(sameTime);
        invalidRequest.setEndTime(sameTime);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            seatSearchService.getSeatsStatus(invalidRequest);
        });

        assertEquals(ResponseCode.SEAT_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, never()).findById(anyString());
    }

    // ==================== mapToSeatSearchResponse Tests ====================

    /**
     * Test mapToSeatSearchResponse with IN_USE reservation
     */
    @Test
    void testMapToSeatSearchResponse_WithInUseReservation() {
        // Arrange
        Reservation inUseReservation = new Reservation();
        inUseReservation.setStatus(ReservationStatus.IN_USE);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor))
                .thenReturn(Arrays.asList(availableSeat));
        when(reservationRepository.findOverlappingReservations(anyString(), any(), any()))
                .thenReturn(Arrays.asList(inUseReservation));

        // Act
        List<SeatSearchResponse> result = seatSearchService.getSeatsStatus(validRequest);

        // Assert
        assertEquals(1, result.size());
        assertEquals(SeatStatus.UNAVAILABLE, result.get(0).getStatus());
    }

    /**
     * Test mapToSeatSearchResponse with non-conflicting reservation statuses
     */
    @Test
    void testMapToSeatSearchResponse_WithNonConflictingReservations() {
        // Arrange
        Reservation completedReservation = new Reservation();
        completedReservation.setStatus(ReservationStatus.COMPLETED);

        Reservation failedReservation = new Reservation();
        failedReservation.setStatus(ReservationStatus.FAILED);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor))
                .thenReturn(Arrays.asList(availableSeat));
        when(reservationRepository.findOverlappingReservations(anyString(), any(), any()))
                .thenReturn(Arrays.asList(completedReservation, failedReservation));

        // Act
        List<SeatSearchResponse> result = seatSearchService.getSeatsStatus(validRequest);

        // Assert
        assertEquals(1, result.size());
        assertEquals(SeatStatus.AVAILABLE, result.get(0).getStatus()); // Should be available as no conflicting reservations
    }

    /**
     * Test mapToSeatSearchResponse with mixed reservation statuses
     */
    @Test
    void testMapToSeatSearchResponse_WithMixedReservationStatuses() {
        // Arrange
        Reservation reservedReservation = new Reservation();
        reservedReservation.setStatus(ReservationStatus.RESERVED);

        Reservation completedReservation = new Reservation();
        completedReservation.setStatus(ReservationStatus.COMPLETED);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor))
                .thenReturn(Arrays.asList(availableSeat));
        when(reservationRepository.findOverlappingReservations(anyString(), any(), any()))
                .thenReturn(Arrays.asList(reservedReservation, completedReservation));

        // Act
        List<SeatSearchResponse> result = seatSearchService.getSeatsStatus(validRequest);

        // Assert
        assertEquals(1, result.size());
        assertEquals(SeatStatus.UNAVAILABLE, result.get(0).getStatus()); // Should be unavailable due to RESERVED status
    }

    // ==================== Integration Tests ====================

    /**
     * Test complete flow with complex scenario
     */
    @Test
    void testCompleteFlow_Success_ComplexScenario() {
        // Arrange - Create multiple seats with different scenarios
        Seat seat1 = new Seat();
        seat1.setId("seat-1");
        seat1.setLocationCode("A1-01");
        seat1.setStatus(SeatStatus.AVAILABLE);
        seat1.setScore(85.0);

        Seat seat2 = new Seat();
        seat2.setId("seat-2");
        seat2.setLocationCode("A1-02");
        seat2.setStatus(SeatStatus.BROKEN);
        seat2.setScore(90.0);

        Seat seat3 = new Seat();
        seat3.setId("seat-3");
        seat3.setLocationCode("A1-03");
        seat3.setStatus(SeatStatus.AVAILABLE);
        seat3.setScore(80.0);

        List<Seat> seats = Arrays.asList(seat1, seat2, seat3);

        Reservation conflictingReservation = new Reservation();
        conflictingReservation.setStatus(ReservationStatus.IN_USE);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(seatRepository.findByFloorOrderByLocationCode(testFloor)).thenReturn(seats);
        when(reservationRepository.findOverlappingReservations("seat-1", validRequest.getStartTime(), validRequest.getEndTime()))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findOverlappingReservations("seat-3", validRequest.getStartTime(), validRequest.getEndTime()))
                .thenReturn(Arrays.asList(conflictingReservation));

        // Act
        List<SeatSearchResponse> result = seatSearchService.getSeatsStatus(validRequest);

        // Assert
        assertEquals(3, result.size());

        // Seat 1 should be available
        SeatSearchResponse response1 = result.stream()
                .filter(r -> r.getSeatId().equals("seat-1"))
                .findFirst().orElseThrow();
        assertEquals(SeatStatus.AVAILABLE, response1.getStatus());

        // Seat 2 should be unavailable (broken)
        SeatSearchResponse response2 = result.stream()
                .filter(r -> r.getSeatId().equals("seat-2"))
                .findFirst().orElseThrow();
        assertEquals(SeatStatus.UNAVAILABLE, response2.getStatus());

        // Seat 3 should be unavailable (conflicting reservation)
        SeatSearchResponse response3 = result.stream()
                .filter(r -> r.getSeatId().equals("seat-3"))
                .findFirst().orElseThrow();
        assertEquals(SeatStatus.UNAVAILABLE, response3.getStatus());

        verify(reservationRepository, times(2)).findOverlappingReservations(anyString(), any(), any());
    }

    /**
     * Test both methods use same validation logic
     */
    @Test
    void testBothMethods_UseSameValidation() {
        // Arrange
        SeatSearchRequest invalidRequest = new SeatSearchRequest();
        invalidRequest.setFloorId("floor-123");
        invalidRequest.setStartTime(LocalDateTime.of(2025, 1, 15, 12, 0));
        invalidRequest.setEndTime(LocalDateTime.of(2025, 1, 15, 10, 0)); // Invalid: start after end

        // Act & Assert - Both methods should throw same exception
        CustomException exception1 = assertThrows(CustomException.class, () -> {
            seatSearchService.getSeatsStatus(invalidRequest);
        });

        CustomException exception2 = assertThrows(CustomException.class, () -> {
            seatSearchService.getSeatStatus(invalidRequest, 0, 10);
        });

        assertEquals(ResponseCode.SEAT_NOT_FOUND, exception1.getResponseCode());
        assertEquals(ResponseCode.SEAT_NOT_FOUND, exception2.getResponseCode());
    }
}