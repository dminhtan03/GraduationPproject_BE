package com.finalProject.BookingMeetingRoom.service.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DashboardServiceTest_getMulti {

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private AmbiguousBuildingResponse buildingResponse;
    private AmbiguousFloorResponse floorResponse;
    private RoomDtoProjection roomProjection;

    @BeforeEach
    void setUp() {
        buildingResponse = new AmbiguousBuildingResponse();
        buildingResponse.setId("B1");
        buildingResponse.setName("Building A");

        floorResponse = new AmbiguousFloorResponse();
        floorResponse.setId("F1");
        floorResponse.setName("Floor 1");

        roomProjection = new RoomDtoProjection() {
            @Override
            public String getRoomId() { return "S1"; }
            @Override
            public String getLocationCode() { return "A1-01"; }
            @Override
            public RoomStatus getStatus() { return RoomStatus.AVAILABLE; }
            @Override
            public Double getScore() { return 85.5; }
        };
    }

    // Helper method để tạo RoomDtoProjection
    private RoomDtoProjection createRoomProjection(String roomId, String locationCode,
                                                   RoomStatus status, Double score) {
        return new RoomDtoProjection() {
            @Override
            public String getRoomId() { return roomId; }
            @Override
            public String getLocationCode() { return locationCode; }
            @Override
            public RoomStatus getStatus() { return status; }
            @Override
            public Double getScore() { return score; }
        };
    }

    // ==================== getAllBuildings Tests ====================

    /**
     * Test successful retrieval of all buildings with data
     */
    @Test
    void testGetAllBuildings_Success_WithData() {
        // Arrange
        AmbiguousBuildingResponse building1 = new AmbiguousBuildingResponse();
        building1.setId("B1");
        building1.setName("Building A");

        AmbiguousBuildingResponse building2 = new AmbiguousBuildingResponse();
        building2.setId("B2");
        building2.setName("Building B");

        List<AmbiguousBuildingResponse> buildings = Arrays.asList(building1, building2);
        when(buildingRepository.findAllBuildings()).thenReturn(buildings);

        // Act
        List<AmbiguousBuildingResponse> result = dashboardService.getAllBuildings();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("B1", result.get(0).getId());
        assertEquals("Building A", result.get(0).getName());
        assertEquals("B2", result.get(1).getId());
        assertEquals("Building B", result.get(1).getName());

        verify(buildingRepository, times(1)).findAllBuildings();
    }

    /**
     * Test when buildingRepository throws RuntimeException
     */
    @Test
    void testGetAllBuildings_RepositoryThrowsRuntimeException_ThrowsInternalServerError() {
        // Arrange
        when(buildingRepository.findAllBuildings())
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            dashboardService.getAllBuildings();
        });

        assertTrue(exception.getMessage().contains("Internal server error"));

        verify(buildingRepository, times(1)).findAllBuildings();
    }

    /**
     * Test when buildingRepository throws SQLException
     */
    @Test
    void testGetAllBuildings_RepositoryThrowsSQLException_ThrowsInternalServerError() {
        // Arrange
        when(buildingRepository.findAllBuildings())
                .thenThrow(new RuntimeException("SQL error", new SQLException("Connection timeout")));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            dashboardService.getAllBuildings();
        });

        assertEquals("Internal server error", exception.getMessage());

        verify(buildingRepository, times(1)).findAllBuildings();
    }

    /**
     * Test when CustomException is thrown by repository - should be re-thrown
     */
    @Test
    void testGetAllBuildings_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        CustomException customException = new CustomException(ResponseCode.USER_NOT_FOUND);
        when(buildingRepository.findAllBuildings()).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getAllBuildings();
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception); // Should be the exact same exception

        verify(buildingRepository, times(1)).findAllBuildings();
    }

    /**
     * Test with single building
     */
    @Test
    void testGetAllBuildings_Success_WithSingleBuilding() {
        // Arrange
        List<AmbiguousBuildingResponse> buildings = Arrays.asList(buildingResponse);
        when(buildingRepository.findAllBuildings()).thenReturn(buildings);

        // Act
        List<AmbiguousBuildingResponse> result = dashboardService.getAllBuildings();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("B1", result.get(0).getId());
        assertEquals("Building A", result.get(0).getName());
    }

    // ==================== getAllFloorsByBuildingId Tests ====================

    /**
     * Test successful retrieval of floors by building ID with data
     */
    @Test
    void testGetAllFloorsByBuildingId_Success_WithData() {
        // Arrange
        String buildingId = "B1";
        AmbiguousFloorResponse floor1 = new AmbiguousFloorResponse();
        floor1.setId("F1");
        floor1.setName("Floor 1");

        AmbiguousFloorResponse floor2 = new AmbiguousFloorResponse();
        floor2.setId("F2");
        floor2.setName("Floor 2");

        List<AmbiguousFloorResponse> floors = Arrays.asList(floor1, floor2);
        when(floorRepository.findAllFloorsByBuildingId(buildingId)).thenReturn(floors);

        // Act
        List<AmbiguousFloorResponse> result = dashboardService.getAllFloorsByBuildingId(buildingId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("F1", result.get(0).getId());
        assertEquals("Floor 1", result.get(0).getName());
        assertEquals("F2", result.get(1).getId());
        assertEquals("Floor 2", result.get(1).getName());

        verify(floorRepository, times(1)).findAllFloorsByBuildingId(buildingId);
    }

    /**
     * Test successful retrieval of floors with empty data
     */
    @Test
    void testGetAllFloorsByBuildingId_Success_WithEmptyData() {
        // Arrange
        String buildingId = "B1";
        when(floorRepository.findAllFloorsByBuildingId(buildingId)).thenReturn(Collections.emptyList());

        // Act
        List<AmbiguousFloorResponse> result = dashboardService.getAllFloorsByBuildingId(buildingId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());

        verify(floorRepository, times(1)).findAllFloorsByBuildingId(buildingId);
    }

    /**
     * Test when floorRepository throws RuntimeException
     */
    @Test
    void testGetAllFloorsByBuildingId_RepositoryThrowsRuntimeException_ThrowsInternalServerError() {
        // Arrange
        String buildingId = "B1";
        when(floorRepository.findAllFloorsByBuildingId(buildingId))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getAllFloorsByBuildingId(buildingId);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(floorRepository, times(1)).findAllFloorsByBuildingId(buildingId);
    }

    /**
     * Test when CustomException is thrown by floor repository - should be re-thrown
     */
    @Test
    void testGetAllFloorsByBuildingId_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        String buildingId = "B1";
        CustomException customException = new CustomException(ResponseCode.USER_NOT_FOUND);
        when(floorRepository.findAllFloorsByBuildingId(buildingId)).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getAllFloorsByBuildingId(buildingId);
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception);

        verify(floorRepository, times(1)).findAllFloorsByBuildingId(buildingId);
    }

    /**
     * Test with null building ID
     */
    @Test
    void testGetAllFloorsByBuildingId_WithNullBuildingId() {
        // Arrange
        String buildingId = null;
        when(floorRepository.findAllFloorsByBuildingId(buildingId)).thenReturn(Collections.emptyList());

        // Act
        List<AmbiguousFloorResponse> result = dashboardService.getAllFloorsByBuildingId(buildingId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());

        verify(floorRepository, times(1)).findAllFloorsByBuildingId(buildingId);
    }

    /**
     * Test with empty building ID
     */
    @Test
    void testGetAllFloorsByBuildingId_WithEmptyBuildingId() {
        // Arrange
        String buildingId = "";
        when(floorRepository.findAllFloorsByBuildingId(buildingId)).thenReturn(Collections.emptyList());

        // Act
        List<AmbiguousFloorResponse> result = dashboardService.getAllFloorsByBuildingId(buildingId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());

        verify(floorRepository, times(1)).findAllFloorsByBuildingId(buildingId);
    }

    // ==================== getAllRoomsByFloorId Tests ====================

    /**
     * Test successful retrieval of rooms by floor ID with data
     */
    @Test
    void testGetAllRoomsByFloorId_Success_WithData() {
        // Arrange
        String floorId = "F1";
        List<RoomDtoProjection> roomProjections = Arrays.asList(
                createRoomProjection("S1", "A1-01", RoomStatus.AVAILABLE, 85.5),
                createRoomProjection("S2", "A1-02", RoomStatus.UNAVAILABLE, 90.0),
                createRoomProjection("S3", "A1-03", RoomStatus.BROKEN, 75.5)
        );

        when(roomRepository.findRooms(floorId)).thenReturn(roomProjections);

        // Act
        List<RoomDto> result = dashboardService.getAllRoomsByFloorId(floorId);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());

        RoomDto room1 = result.get(0);
        assertEquals("S1", room1.getRoomId());
        assertEquals("A1-01", room1.getLocationCode());
        assertEquals(RoomStatus.AVAILABLE, room1.getStatus());
        assertEquals(85.5, room1.getScore());

        RoomDto room2 = result.get(1);
        assertEquals("S2", room2.getRoomId());
        assertEquals("A1-02", room2.getLocationCode());
        assertEquals(RoomStatus.UNAVAILABLE, room2.getStatus());
        assertEquals(90.0, room2.getScore());

        RoomDto room3 = result.get(2);
        assertEquals("S3", room3.getRoomId());
        assertEquals("A1-03", room3.getLocationCode());
        assertEquals(RoomStatus.BROKEN, room3.getStatus());
        assertEquals(75.5, room3.getScore());

        verify(roomRepository, times(1)).findRooms(floorId);
    }

    /**
     * Test successful retrieval of rooms with empty data
     */
    @Test
    void testGetAllRoomsByFloorId_Success_WithEmptyData() {
        // Arrange
        String floorId = "F1";
        when(roomRepository.findRooms(floorId)).thenReturn(Collections.emptyList());

        // Act
        List<RoomDto> result = dashboardService.getAllRoomsByFloorId(floorId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());

        verify(roomRepository, times(1)).findRooms(floorId);
    }

    /**
     * Test when roomRepository throws RuntimeException
     */
    @Test
    void testGetAllRoomsByFloorId_RepositoryThrowsRuntimeException_ThrowsInternalServerError() {
        // Arrange
        String floorId = "F1";
        when(roomRepository.findRooms(floorId))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getAllRoomsByFloorId(floorId);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(roomRepository, times(1)).findRooms(floorId);
    }

    /**
     * Test when stream processing throws exception due to null projection
     */
    @Test
    void testGetAllRoomsByFloorId_NullProjectionInList_ThrowsInternalServerError() {
        // Arrange
        String floorId = "F1";
        List<RoomDtoProjection> roomProjections = Arrays.asList(
                roomProjection,
                null // This will cause NPE during stream processing
        );

        when(roomRepository.findRooms(floorId)).thenReturn(roomProjections);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getAllRoomsByFloorId(floorId);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(roomRepository, times(1)).findRooms(floorId);
    }

    /**
     * Test when CustomException is thrown by room repository - should be re-thrown
     */
    @Test
    void testGetAllRoomsByFloorId_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        String floorId = "F1";
        CustomException customException = new CustomException(ResponseCode.USER_NOT_FOUND);
        when(roomRepository.findRooms(floorId)).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getAllRoomsByFloorId(floorId);
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception);

        verify(roomRepository, times(1)).findRooms(floorId);
    }

    /**
     * Test with null values in projection
     */
    @Test
    void testGetAllRoomsByFloorId_Success_WithNullValues() {
        // Arrange
        String floorId = "F1";
        List<RoomDtoProjection> roomProjections = Arrays.asList(
                createRoomProjection(null, null, null, null),
                createRoomProjection("S2", "", RoomStatus.AVAILABLE, 0.0)
        );

        when(roomRepository.findRooms(floorId)).thenReturn(roomProjections);

        // Act
        List<RoomDto> result = dashboardService.getAllRoomsByFloorId(floorId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());

        RoomDto room1 = result.get(0);
        assertNull(room1.getRoomId());
        assertNull(room1.getLocationCode());
        assertNull(room1.getStatus());
        assertNull(room1.getScore());

        RoomDto room2 = result.get(1);
        assertEquals("S2", room2.getRoomId());
        assertEquals("", room2.getLocationCode());
        assertEquals(RoomStatus.AVAILABLE, room2.getStatus());
        assertEquals(0.0, room2.getScore());
    }

    /**
     * Test with all RoomStatus enum values
     */
    @Test
    void testGetAllRoomsByFloorId_Success_WithAllRoomStatuses() {
        // Arrange
        String floorId = "F1";
        List<RoomDtoProjection> roomProjections = Arrays.asList(
                createRoomProjection("S1", "A1-01", RoomStatus.AVAILABLE, 85.5),
                createRoomProjection("S2", "A1-02", RoomStatus.UNAVAILABLE, 90.0),
                createRoomProjection("S3", "A1-03", RoomStatus.BROKEN, 75.5)
        );

        when(roomRepository.findRooms(floorId)).thenReturn(roomProjections);

        // Act
        List<RoomDto> result = dashboardService.getAllRoomsByFloorId(floorId);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());

        // Verify all room statuses are present
        List<RoomStatus> statuses = result.stream()
                .map(RoomDto::getStatus)
                .toList();

        assertTrue(statuses.contains(RoomStatus.AVAILABLE));
        assertTrue(statuses.contains(RoomStatus.UNAVAILABLE));
        assertTrue(statuses.contains(RoomStatus.BROKEN));
    }

    /**
     * Test with large dataset
     */
    @Test
    void testGetAllRoomsByFloorId_Success_WithLargeDataset() {
        // Arrange
        String floorId = "F1";
        List<RoomDtoProjection> roomProjections = new ArrayList<>();
        RoomStatus[] statuses = {RoomStatus.AVAILABLE, RoomStatus.UNAVAILABLE, RoomStatus.BROKEN};

        // Create 100 rooms
        for (int i = 1; i <= 100; i++) {
            roomProjections.add(createRoomProjection(
                    "S" + i,
                    "A1-" + String.format("%02d", i),
                    statuses[i % 3],
                    70.0 + (i % 30)
            ));
        }

        when(roomRepository.findRooms(floorId)).thenReturn(roomProjections);

        // Act
        long startTime = System.currentTimeMillis();
        List<RoomDto> result = dashboardService.getAllRoomsByFloorId(floorId);
        long endTime = System.currentTimeMillis();

        // Assert
        assertNotNull(result);
        assertEquals(100, result.size());

        // Verify first and last rooms
        assertEquals("S1", result.get(0).getRoomId());
        assertEquals("S100", result.get(99).getRoomId());

        System.out.println("Processing time for 100 rooms: " + (endTime - startTime) + "ms");

        verify(roomRepository, times(1)).findRooms(floorId);
    }

    /**
     * Test with null floor ID
     */
    @Test
    void testGetAllRoomsByFloorId_WithNullFloorId() {
        // Arrange
        String floorId = null;
        when(roomRepository.findRooms(floorId)).thenReturn(Collections.emptyList());

        // Act
        List<RoomDto> result = dashboardService.getAllRoomsByFloorId(floorId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());

        verify(roomRepository, times(1)).findRooms(floorId);
    }
}