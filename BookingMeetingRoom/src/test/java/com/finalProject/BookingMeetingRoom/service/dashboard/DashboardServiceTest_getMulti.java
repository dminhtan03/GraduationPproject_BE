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
    private SeatRepository seatRepository;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private AmbiguousBuildingResponse buildingResponse;
    private AmbiguousFloorResponse floorResponse;
    private SeatDtoProjection seatProjection;

    @BeforeEach
    void setUp() {
        buildingResponse = new AmbiguousBuildingResponse();
        buildingResponse.setId("B1");
        buildingResponse.setName("Building A");

        floorResponse = new AmbiguousFloorResponse();
        floorResponse.setId("F1");
        floorResponse.setName("Floor 1");

        seatProjection = new SeatDtoProjection() {
            @Override
            public String getSeatId() { return "S1"; }
            @Override
            public String getLocationCode() { return "A1-01"; }
            @Override
            public SeatStatus getStatus() { return SeatStatus.AVAILABLE; }
            @Override
            public Double getScore() { return 85.5; }
        };
    }

    // Helper method để tạo SeatDtoProjection
    private SeatDtoProjection createSeatProjection(String seatId, String locationCode,
                                                   SeatStatus status, Double score) {
        return new SeatDtoProjection() {
            @Override
            public String getSeatId() { return seatId; }
            @Override
            public String getLocationCode() { return locationCode; }
            @Override
            public SeatStatus getStatus() { return status; }
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

    // ==================== getAllSeatsByFloorId Tests ====================

    /**
     * Test successful retrieval of seats by floor ID with data
     */
    @Test
    void testGetAllSeatsByFloorId_Success_WithData() {
        // Arrange
        String floorId = "F1";
        List<SeatDtoProjection> seatProjections = Arrays.asList(
                createSeatProjection("S1", "A1-01", SeatStatus.AVAILABLE, 85.5),
                createSeatProjection("S2", "A1-02", SeatStatus.UNAVAILABLE, 90.0),
                createSeatProjection("S3", "A1-03", SeatStatus.BROKEN, 75.5)
        );

        when(seatRepository.findSeats(floorId)).thenReturn(seatProjections);

        // Act
        List<SeatDto> result = dashboardService.getAllSeatsByFloorId(floorId);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());

        SeatDto seat1 = result.get(0);
        assertEquals("S1", seat1.getSeatId());
        assertEquals("A1-01", seat1.getLocationCode());
        assertEquals(SeatStatus.AVAILABLE, seat1.getStatus());
        assertEquals(85.5, seat1.getScore());

        SeatDto seat2 = result.get(1);
        assertEquals("S2", seat2.getSeatId());
        assertEquals("A1-02", seat2.getLocationCode());
        assertEquals(SeatStatus.UNAVAILABLE, seat2.getStatus());
        assertEquals(90.0, seat2.getScore());

        SeatDto seat3 = result.get(2);
        assertEquals("S3", seat3.getSeatId());
        assertEquals("A1-03", seat3.getLocationCode());
        assertEquals(SeatStatus.BROKEN, seat3.getStatus());
        assertEquals(75.5, seat3.getScore());

        verify(seatRepository, times(1)).findSeats(floorId);
    }

    /**
     * Test successful retrieval of seats with empty data
     */
    @Test
    void testGetAllSeatsByFloorId_Success_WithEmptyData() {
        // Arrange
        String floorId = "F1";
        when(seatRepository.findSeats(floorId)).thenReturn(Collections.emptyList());

        // Act
        List<SeatDto> result = dashboardService.getAllSeatsByFloorId(floorId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());

        verify(seatRepository, times(1)).findSeats(floorId);
    }

    /**
     * Test when seatRepository throws RuntimeException
     */
    @Test
    void testGetAllSeatsByFloorId_RepositoryThrowsRuntimeException_ThrowsInternalServerError() {
        // Arrange
        String floorId = "F1";
        when(seatRepository.findSeats(floorId))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getAllSeatsByFloorId(floorId);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(seatRepository, times(1)).findSeats(floorId);
    }

    /**
     * Test when stream processing throws exception due to null projection
     */
    @Test
    void testGetAllSeatsByFloorId_NullProjectionInList_ThrowsInternalServerError() {
        // Arrange
        String floorId = "F1";
        List<SeatDtoProjection> seatProjections = Arrays.asList(
                seatProjection,
                null // This will cause NPE during stream processing
        );

        when(seatRepository.findSeats(floorId)).thenReturn(seatProjections);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getAllSeatsByFloorId(floorId);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(seatRepository, times(1)).findSeats(floorId);
    }

    /**
     * Test when CustomException is thrown by seat repository - should be re-thrown
     */
    @Test
    void testGetAllSeatsByFloorId_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        String floorId = "F1";
        CustomException customException = new CustomException(ResponseCode.USER_NOT_FOUND);
        when(seatRepository.findSeats(floorId)).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getAllSeatsByFloorId(floorId);
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception);

        verify(seatRepository, times(1)).findSeats(floorId);
    }

    /**
     * Test with null values in projection
     */
    @Test
    void testGetAllSeatsByFloorId_Success_WithNullValues() {
        // Arrange
        String floorId = "F1";
        List<SeatDtoProjection> seatProjections = Arrays.asList(
                createSeatProjection(null, null, null, null),
                createSeatProjection("S2", "", SeatStatus.AVAILABLE, 0.0)
        );

        when(seatRepository.findSeats(floorId)).thenReturn(seatProjections);

        // Act
        List<SeatDto> result = dashboardService.getAllSeatsByFloorId(floorId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());

        SeatDto seat1 = result.get(0);
        assertNull(seat1.getSeatId());
        assertNull(seat1.getLocationCode());
        assertNull(seat1.getStatus());
        assertNull(seat1.getScore());

        SeatDto seat2 = result.get(1);
        assertEquals("S2", seat2.getSeatId());
        assertEquals("", seat2.getLocationCode());
        assertEquals(SeatStatus.AVAILABLE, seat2.getStatus());
        assertEquals(0.0, seat2.getScore());
    }

    /**
     * Test with all SeatStatus enum values
     */
    @Test
    void testGetAllSeatsByFloorId_Success_WithAllSeatStatuses() {
        // Arrange
        String floorId = "F1";
        List<SeatDtoProjection> seatProjections = Arrays.asList(
                createSeatProjection("S1", "A1-01", SeatStatus.AVAILABLE, 85.5),
                createSeatProjection("S2", "A1-02", SeatStatus.UNAVAILABLE, 90.0),
                createSeatProjection("S3", "A1-03", SeatStatus.BROKEN, 75.5)
        );

        when(seatRepository.findSeats(floorId)).thenReturn(seatProjections);

        // Act
        List<SeatDto> result = dashboardService.getAllSeatsByFloorId(floorId);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());

        // Verify all seat statuses are present
        List<SeatStatus> statuses = result.stream()
                .map(SeatDto::getStatus)
                .toList();

        assertTrue(statuses.contains(SeatStatus.AVAILABLE));
        assertTrue(statuses.contains(SeatStatus.UNAVAILABLE));
        assertTrue(statuses.contains(SeatStatus.BROKEN));
    }

    /**
     * Test with large dataset
     */
    @Test
    void testGetAllSeatsByFloorId_Success_WithLargeDataset() {
        // Arrange
        String floorId = "F1";
        List<SeatDtoProjection> seatProjections = new ArrayList<>();
        SeatStatus[] statuses = {SeatStatus.AVAILABLE, SeatStatus.UNAVAILABLE, SeatStatus.BROKEN};

        // Create 100 seats
        for (int i = 1; i <= 100; i++) {
            seatProjections.add(createSeatProjection(
                    "S" + i,
                    "A1-" + String.format("%02d", i),
                    statuses[i % 3],
                    70.0 + (i % 30)
            ));
        }

        when(seatRepository.findSeats(floorId)).thenReturn(seatProjections);

        // Act
        long startTime = System.currentTimeMillis();
        List<SeatDto> result = dashboardService.getAllSeatsByFloorId(floorId);
        long endTime = System.currentTimeMillis();

        // Assert
        assertNotNull(result);
        assertEquals(100, result.size());

        // Verify first and last seats
        assertEquals("S1", result.get(0).getSeatId());
        assertEquals("S100", result.get(99).getSeatId());

        System.out.println("Processing time for 100 seats: " + (endTime - startTime) + "ms");

        verify(seatRepository, times(1)).findSeats(floorId);
    }

    /**
     * Test with null floor ID
     */
    @Test
    void testGetAllSeatsByFloorId_WithNullFloorId() {
        // Arrange
        String floorId = null;
        when(seatRepository.findSeats(floorId)).thenReturn(Collections.emptyList());

        // Act
        List<SeatDto> result = dashboardService.getAllSeatsByFloorId(floorId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size());

        verify(seatRepository, times(1)).findSeats(floorId);
    }
}