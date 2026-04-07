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
    private RoomRepository roomRepository;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private Logger logger;

    @InjectMocks
    private RoomServiceImpl roomSearchService;

    private RoomSearchRequest validRequest;
    private Floor testFloor;
    private Room availableRoom;
    private Room brokenRoom;
    private Room reservedRoom;
    private Reservation overlappingReservation;

    @BeforeEach
    void setUp() {
        // Setup test floor
        testFloor = new Floor();
        testFloor.setId("floor-123");
        testFloor.setName("Test Floor");

        // Setup test rooms
        availableRoom = new Room();
        availableRoom.setId("room-available");
        availableRoom.setLocationCode("A1-01");
        availableRoom.setStatus(RoomStatus.AVAILABLE);
        availableRoom.setScore(85.5);

        brokenRoom = new Room();
        brokenRoom.setId("room-broken");
        brokenRoom.setLocationCode("A1-02");
        brokenRoom.setStatus(RoomStatus.BROKEN);
        brokenRoom.setScore(90.0);

        reservedRoom = new Room();
        reservedRoom.setId("room-reserved");
        reservedRoom.setLocationCode("A1-03");
        reservedRoom.setStatus(RoomStatus.AVAILABLE);
        reservedRoom.setScore(80.0);

        // Setup test reservation
        overlappingReservation = new Reservation();
        overlappingReservation.setId("reservation-123");
        overlappingReservation.setStatus(ReservationStatus.RESERVED);
        overlappingReservation.setStartTime(LocalDateTime.of(2025, 1, 15, 10, 0));
        overlappingReservation.setEndTime(LocalDateTime.of(2025, 1, 15, 12, 0));

        // Setup valid request
        validRequest = new RoomSearchRequest();
        validRequest.setFloorId("floor-123");
        validRequest.setStartTime(LocalDateTime.of(2025, 1, 15, 9, 0));
        validRequest.setEndTime(LocalDateTime.of(2025, 1, 15, 11, 0));
    }

    // ==================== getRoomsStatus Tests ====================

    /**
     * Test successful getRoomsStatus with available rooms
     */
    @Test
    void testGetRoomsStatus_Success_WithAvailableRooms() {
        // Arrange
        List<Room> rooms = Arrays.asList(availableRoom);
        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor)).thenReturn(rooms);
        when(reservationRepository.findOverlappingReservations(
                eq("room-available"),
                eq(validRequest.getStartTime()),
                eq(validRequest.getEndTime())
        )).thenReturn(Collections.emptyList());

        // Act
        List<RoomSearchResponse> result = roomSearchService.getRoomsStatus(validRequest);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        RoomSearchResponse response = result.get(0);
        assertEquals("room-available", response.getRoomId());
        assertEquals("A1-01", response.getLocationCode());
        assertEquals(85.5, response.getScore());
        assertEquals(RoomStatus.AVAILABLE, response.getStatus());

        verify(floorRepository, times(1)).findById("floor-123");
        verify(roomRepository, times(1)).findByFloorOrderByLocationCode(testFloor);
        verify(reservationRepository, times(1)).findOverlappingReservations(
                "room-available", validRequest.getStartTime(), validRequest.getEndTime());
    }

    /**
     * Test successful getRoomsStatus with broken room
     */
    @Test
    void testGetRoomsStatus_Success_WithBrokenRoom() {
        // Arrange
        List<Room> rooms = Arrays.asList(brokenRoom);
        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor)).thenReturn(rooms);

        // Act
        List<RoomSearchResponse> result = roomSearchService.getRoomsStatus(validRequest);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        RoomSearchResponse response = result.get(0);
        assertEquals("room-broken", response.getRoomId());
        assertEquals("A1-02", response.getLocationCode());
        assertEquals(90.0, response.getScore());
        assertEquals(RoomStatus.UNAVAILABLE, response.getStatus()); // Broken room shows as UNAVAILABLE

        verify(floorRepository, times(1)).findById("floor-123");
        verify(roomRepository, times(1)).findByFloorOrderByLocationCode(testFloor);
        // Reservation query should not be called for broken rooms
        verify(reservationRepository, never()).findOverlappingReservations(anyString(), any(), any());
    }

    /**
     * Test successful getRoomsStatus with reserved room
     */
    @Test
    void testGetRoomsStatus_Success_WithReservedRoom() {
        // Arrange
        List<Room> rooms = Arrays.asList(reservedRoom);
        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor)).thenReturn(rooms);
        when(reservationRepository.findOverlappingReservations(
                eq("room-reserved"),
                eq(validRequest.getStartTime()),
                eq(validRequest.getEndTime())
        )).thenReturn(Arrays.asList(overlappingReservation));

        // Act
        List<RoomSearchResponse> result = roomSearchService.getRoomsStatus(validRequest);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        RoomSearchResponse response = result.get(0);
        assertEquals("room-reserved", response.getRoomId());
        assertEquals("A1-03", response.getLocationCode());
        assertEquals(80.0, response.getScore());
        assertEquals(RoomStatus.UNAVAILABLE, response.getStatus()); // Reserved room shows as UNAVAILABLE

        verify(reservationRepository, times(1)).findOverlappingReservations(
                "room-reserved", validRequest.getStartTime(), validRequest.getEndTime());
    }

    /**
     * Test successful getRoomsStatus with mixed room types
     */
    @Test
    void testGetRoomsStatus_Success_WithMixedRoomTypes() {
        // Arrange
        List<Room> rooms = Arrays.asList(availableRoom, brokenRoom, reservedRoom);
        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor)).thenReturn(rooms);
        when(reservationRepository.findOverlappingReservations(
                eq("room-available"), any(), any())).thenReturn(Collections.emptyList());
        when(reservationRepository.findOverlappingReservations(
                eq("room-reserved"), any(), any())).thenReturn(Arrays.asList(overlappingReservation));

        // Act
        List<RoomSearchResponse> result = roomSearchService.getRoomsStatus(validRequest);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());

        // Check available room
        RoomSearchResponse availableResponse = result.stream()
                .filter(r -> r.getRoomId().equals("room-available"))
                .findFirst().orElseThrow();
        assertEquals(RoomStatus.AVAILABLE, availableResponse.getStatus());

        // Check broken room
        RoomSearchResponse brokenResponse = result.stream()
                .filter(r -> r.getRoomId().equals("room-broken"))
                .findFirst().orElseThrow();
        assertEquals(RoomStatus.UNAVAILABLE, brokenResponse.getStatus());

        // Check reserved room
        RoomSearchResponse reservedResponse = result.stream()
                .filter(r -> r.getRoomId().equals("room-reserved"))
                .findFirst().orElseThrow();
        assertEquals(RoomStatus.UNAVAILABLE, reservedResponse.getStatus());

        verify(reservationRepository, times(2)).findOverlappingReservations(anyString(), any(), any());
    }

    /**
     * Test getRoomsStatus with empty room list
     */
    @Test
    void testGetRoomsStatus_Success_WithEmptyRooms() {
        // Arrange
        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor)).thenReturn(Collections.emptyList());

        // Act
        List<RoomSearchResponse> result = roomSearchService.getRoomsStatus(validRequest);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());

        verify(floorRepository, times(1)).findById("floor-123");
        verify(roomRepository, times(1)).findByFloorOrderByLocationCode(testFloor);
    }

    /**
     * Test getRoomsStatus when floor is not found
     */
    @Test
    void testGetRoomsStatus_FloorNotFound_ThrowsCustomException() {
        // Arrange
        when(floorRepository.findById("nonexistent-floor")).thenReturn(Optional.empty());

        RoomSearchRequest invalidRequest = new RoomSearchRequest();
        invalidRequest.setFloorId("nonexistent-floor");
        invalidRequest.setStartTime(LocalDateTime.of(2025, 1, 15, 9, 0));
        invalidRequest.setEndTime(LocalDateTime.of(2025, 1, 15, 11, 0));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            roomSearchService.getRoomsStatus(invalidRequest);
        });

        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, times(1)).findById("nonexistent-floor");
        verify(roomRepository, never()).findByFloorOrderByLocationCode(any());
    }

    /**
     * Test getRoomsStatus when repository throws RuntimeException
     */
    @Test
    void testGetRoomsStatus_RepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            roomSearchService.getRoomsStatus(validRequest);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(floorRepository, times(1)).findById("floor-123");
        verify(roomRepository, times(1)).findByFloorOrderByLocationCode(testFloor);
        verify(logger, times(1)).error(anyString(), any(Exception.class));
    }

    // ==================== getRoomStatus (Paginated) Tests ====================

    /**
     * Test successful getRoomStatus with pagination
     */
    @Test
    void testGetRoomStatus_Success_WithPagination() {
        // Arrange
        int page = 0;
        int size = 10;
        List<Room> rooms = Arrays.asList(availableRoom);
        Pageable pageable = PageRequest.of(page, size);
        Page<Room> roomPage = new PageImpl<>(rooms, pageable, 1);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor, pageable)).thenReturn(roomPage);
        when(reservationRepository.findOverlappingReservations(
                eq("room-available"), any(), any())).thenReturn(Collections.emptyList());

        // Act
        Page<RoomSearchResponse> result = roomSearchService.getRoomStatus(validRequest, page, size);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());

        RoomSearchResponse response = result.getContent().get(0);
        assertEquals("room-available", response.getRoomId());
        assertEquals("A1-01", response.getLocationCode());
        assertEquals(85.5, response.getScore());
        assertEquals(RoomStatus.AVAILABLE, response.getStatus());

        verify(floorRepository, times(1)).findById("floor-123");
        verify(roomRepository, times(1)).findByFloorOrderByLocationCode(testFloor, pageable);
    }

    /**
     * Test getRoomStatus with different page parameters
     */
    @Test
    void testGetRoomStatus_Success_WithDifferentPageParameters() {
        // Arrange
        int page = 2;
        int size = 20;
        List<Room> rooms = Arrays.asList(availableRoom);
        Pageable pageable = PageRequest.of(page, size);
        Page<Room> roomPage = new PageImpl<>(rooms, pageable, 1);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor, pageable)).thenReturn(roomPage);
        when(reservationRepository.findOverlappingReservations(anyString(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        Page<RoomSearchResponse> result = roomSearchService.getRoomStatus(validRequest, page, size);

        // Assert
        assertNotNull(result);
        assertEquals(page, result.getNumber());
        assertEquals(size, result.getSize());

        verify(roomRepository, times(1)).findByFloorOrderByLocationCode(testFloor, pageable);
    }

    /**
     * Test getRoomStatus when repository throws RuntimeException
     */
    @Test
    void testGetRoomStatus_RepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        int page = 0;
        int size = 10;
        Pageable pageable = PageRequest.of(page, size);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor, pageable))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            roomSearchService.getRoomStatus(validRequest, page, size);
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
        RoomSearchRequest invalidRequest = new RoomSearchRequest();
        invalidRequest.setFloorId("floor-123");
        invalidRequest.setStartTime(null);
        invalidRequest.setEndTime(LocalDateTime.of(2025, 1, 15, 11, 0));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            roomSearchService.getRoomsStatus(invalidRequest);
        });

        assertEquals(ResponseCode.ROOM_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, never()).findById(anyString());
    }

    /**
     * Test validateAndGetContext with null end time
     */
    @Test
    void testValidateAndGetContext_NullEndTime_ThrowsCustomException() {
        // Arrange
        RoomSearchRequest invalidRequest = new RoomSearchRequest();
        invalidRequest.setFloorId("floor-123");
        invalidRequest.setStartTime(LocalDateTime.of(2025, 1, 15, 9, 0));
        invalidRequest.setEndTime(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            roomSearchService.getRoomsStatus(invalidRequest);
        });

        assertEquals(ResponseCode.ROOM_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, never()).findById(anyString());
    }

    /**
     * Test validateAndGetContext with start time after end time
     */
    @Test
    void testValidateAndGetContext_StartTimeAfterEndTime_ThrowsCustomException() {
        // Arrange
        RoomSearchRequest invalidRequest = new RoomSearchRequest();
        invalidRequest.setFloorId("floor-123");
        invalidRequest.setStartTime(LocalDateTime.of(2025, 1, 15, 12, 0));
        invalidRequest.setEndTime(LocalDateTime.of(2025, 1, 15, 10, 0));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            roomSearchService.getRoomsStatus(invalidRequest);
        });

        assertEquals(ResponseCode.ROOM_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, never()).findById(anyString());
    }

    /**
     * Test validateAndGetContext with equal start and end time
     */
    @Test
    void testValidateAndGetContext_EqualStartAndEndTime_ThrowsCustomException() {
        // Arrange
        LocalDateTime sameTime = LocalDateTime.of(2025, 1, 15, 10, 0);
        RoomSearchRequest invalidRequest = new RoomSearchRequest();
        invalidRequest.setFloorId("floor-123");
        invalidRequest.setStartTime(sameTime);
        invalidRequest.setEndTime(sameTime);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            roomSearchService.getRoomsStatus(invalidRequest);
        });

        assertEquals(ResponseCode.ROOM_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, never()).findById(anyString());
    }

    // ==================== mapToRoomSearchResponse Tests ====================

    /**
     * Test mapToRoomSearchResponse with IN_USE reservation
     */
    @Test
    void testMapToRoomSearchResponse_WithInUseReservation() {
        // Arrange
        Reservation inUseReservation = new Reservation();
        inUseReservation.setStatus(ReservationStatus.IN_USE);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor))
                .thenReturn(Arrays.asList(availableRoom));
        when(reservationRepository.findOverlappingReservations(anyString(), any(), any()))
                .thenReturn(Arrays.asList(inUseReservation));

        // Act
        List<RoomSearchResponse> result = roomSearchService.getRoomsStatus(validRequest);

        // Assert
        assertEquals(1, result.size());
        assertEquals(RoomStatus.UNAVAILABLE, result.get(0).getStatus());
    }

    /**
     * Test mapToRoomSearchResponse with non-conflicting reservation statuses
     */
    @Test
    void testMapToRoomSearchResponse_WithNonConflictingReservations() {
        // Arrange
        Reservation completedReservation = new Reservation();
        completedReservation.setStatus(ReservationStatus.COMPLETED);

        Reservation failedReservation = new Reservation();
        failedReservation.setStatus(ReservationStatus.FAILED);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor))
                .thenReturn(Arrays.asList(availableRoom));
        when(reservationRepository.findOverlappingReservations(anyString(), any(), any()))
                .thenReturn(Arrays.asList(completedReservation, failedReservation));

        // Act
        List<RoomSearchResponse> result = roomSearchService.getRoomsStatus(validRequest);

        // Assert
        assertEquals(1, result.size());
        assertEquals(RoomStatus.AVAILABLE, result.get(0).getStatus()); // Should be available as no conflicting reservations
    }

    /**
     * Test mapToRoomSearchResponse with mixed reservation statuses
     */
    @Test
    void testMapToRoomSearchResponse_WithMixedReservationStatuses() {
        // Arrange
        Reservation reservedReservation = new Reservation();
        reservedReservation.setStatus(ReservationStatus.RESERVED);

        Reservation completedReservation = new Reservation();
        completedReservation.setStatus(ReservationStatus.COMPLETED);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor))
                .thenReturn(Arrays.asList(availableRoom));
        when(reservationRepository.findOverlappingReservations(anyString(), any(), any()))
                .thenReturn(Arrays.asList(reservedReservation, completedReservation));

        // Act
        List<RoomSearchResponse> result = roomSearchService.getRoomsStatus(validRequest);

        // Assert
        assertEquals(1, result.size());
        assertEquals(RoomStatus.UNAVAILABLE, result.get(0).getStatus()); // Should be unavailable due to RESERVED status
    }

    // ==================== Integration Tests ====================

    /**
     * Test complete flow with complex scenario
     */
    @Test
    void testCompleteFlow_Success_ComplexScenario() {
        // Arrange - Create multiple rooms with different scenarios
        Room room1 = new Room();
        room1.setId("room-1");
        room1.setLocationCode("A1-01");
        room1.setStatus(RoomStatus.AVAILABLE);
        room1.setScore(85.0);

        Room room2 = new Room();
        room2.setId("room-2");
        room2.setLocationCode("A1-02");
        room2.setStatus(RoomStatus.BROKEN);
        room2.setScore(90.0);

        Room room3 = new Room();
        room3.setId("room-3");
        room3.setLocationCode("A1-03");
        room3.setStatus(RoomStatus.AVAILABLE);
        room3.setScore(80.0);

        List<Room> rooms = Arrays.asList(room1, room2, room3);

        Reservation conflictingReservation = new Reservation();
        conflictingReservation.setStatus(ReservationStatus.IN_USE);

        when(floorRepository.findById("floor-123")).thenReturn(Optional.of(testFloor));
        when(roomRepository.findByFloorOrderByLocationCode(testFloor)).thenReturn(rooms);
        when(reservationRepository.findOverlappingReservations("room-1", validRequest.getStartTime(), validRequest.getEndTime()))
                .thenReturn(Collections.emptyList());
        when(reservationRepository.findOverlappingReservations("room-3", validRequest.getStartTime(), validRequest.getEndTime()))
                .thenReturn(Arrays.asList(conflictingReservation));

        // Act
        List<RoomSearchResponse> result = roomSearchService.getRoomsStatus(validRequest);

        // Assert
        assertEquals(3, result.size());

        // Room 1 should be available
        RoomSearchResponse response1 = result.stream()
                .filter(r -> r.getRoomId().equals("room-1"))
                .findFirst().orElseThrow();
        assertEquals(RoomStatus.AVAILABLE, response1.getStatus());

        // Room 2 should be unavailable (broken)
        RoomSearchResponse response2 = result.stream()
                .filter(r -> r.getRoomId().equals("room-2"))
                .findFirst().orElseThrow();
        assertEquals(RoomStatus.UNAVAILABLE, response2.getStatus());

        // Room 3 should be unavailable (conflicting reservation)
        RoomSearchResponse response3 = result.stream()
                .filter(r -> r.getRoomId().equals("room-3"))
                .findFirst().orElseThrow();
        assertEquals(RoomStatus.UNAVAILABLE, response3.getStatus());

        verify(reservationRepository, times(2)).findOverlappingReservations(anyString(), any(), any());
    }

    /**
     * Test both methods use same validation logic
     */
    @Test
    void testBothMethods_UseSameValidation() {
        // Arrange
        RoomSearchRequest invalidRequest = new RoomSearchRequest();
        invalidRequest.setFloorId("floor-123");
        invalidRequest.setStartTime(LocalDateTime.of(2025, 1, 15, 12, 0));
        invalidRequest.setEndTime(LocalDateTime.of(2025, 1, 15, 10, 0)); // Invalid: start after end

        // Act & Assert - Both methods should throw same exception
        CustomException exception1 = assertThrows(CustomException.class, () -> {
            roomSearchService.getRoomsStatus(invalidRequest);
        });

        CustomException exception2 = assertThrows(CustomException.class, () -> {
            roomSearchService.getRoomStatus(invalidRequest, 0, 10);
        });

        assertEquals(ResponseCode.ROOM_NOT_FOUND, exception1.getResponseCode());
        assertEquals(ResponseCode.ROOM_NOT_FOUND, exception2.getResponseCode());
    }
}