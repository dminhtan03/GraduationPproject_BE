package com.finalProject.BookingMeetingRoom.service.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DashboardServiceTest_getSeatsMapDashboard {
    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private Logger logger;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private SeatMapDashboardProjection seatMapProjection;

    @BeforeEach
    void setUp() {
        seatMapProjection = new SeatMapDashboardProjection() {
            @Override
            public String getBuildingId() { return "B1"; }
            @Override
            public String getBuildingName() { return "Building A"; }
            @Override
            public String getAddress() { return "123 Main Street"; }
            @Override
            public String getFloorId() { return "F1"; }
            @Override
            public String getFloorName() { return "Floor 1"; }
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

    // Helper method để tạo SeatMapDashboardProjection
    private SeatMapDashboardProjection createSeatMapProjection(String buildingId, String buildingName,
                                                               String address, String floorId, String floorName,
                                                               String seatId, String locationCode,
                                                               SeatStatus status, Double score) {
        return new SeatMapDashboardProjection() {
            @Override
            public String getBuildingId() { return buildingId; }
            @Override
            public String getBuildingName() { return buildingName; }
            @Override
            public String getAddress() { return address; }
            @Override
            public String getFloorId() { return floorId; }
            @Override
            public String getFloorName() { return floorName; }
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

    /**
     * Test successful seat map dashboard with single building, single floor, single seat
     */
    @Test
    void testGetSeatsMapDashboard_Success_WithAllData() {
        // Arrange
        List<SeatMapDashboardProjection> seatMapData = Arrays.asList(seatMapProjection);
        when(buildingRepository.findSeatMapDashBoard()).thenReturn(seatMapData);

        // Act
        SeatMapDashboardResponse result = dashboardService.getSeatsMapDashboard();

        // Assert
        assertNotNull(result);
        assertNotNull(result.getBuildingResponse());
        assertEquals(1, result.getBuildingResponse().size());

        SeatMapBuildingResponse building = result.getBuildingResponse().get(0);
        assertEquals("B1", building.getBuildingId());
        assertEquals("Building A", building.getBuildingName());
        assertEquals(1, building.getFloors().size());

        DetailFloorResponse floor = building.getFloors().get(0);
        assertEquals("F1", floor.getFloorId());
        assertEquals("Floor 1", floor.getFloorName());
        assertEquals(1, floor.getSeats().size());

        SeatDto seat = floor.getSeats().get(0);
        assertEquals("S1", seat.getSeatId());
        assertEquals("A1-01", seat.getLocationCode());
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
        assertEquals(85.5, seat.getScore());

        verify(buildingRepository, times(1)).findSeatMapDashBoard();
    }

    /**
     * Test successful seat map dashboard with multiple buildings, floors, and seats
     */
    @Test
    void testGetSeatsMapDashboard_Success_WithMultipleBuildingsFloorsSeats() {
        // Arrange
        List<SeatMapDashboardProjection> seatMapData = Arrays.asList(
                // Building A, Floor 1
                createSeatMapProjection("B1", "Building A", "123 Main St", "F1", "Floor 1", "S1", "A1-01", SeatStatus.AVAILABLE, 85.5),
                createSeatMapProjection("B1", "Building A", "123 Main St", "F1", "Floor 1", "S2", "A1-02", SeatStatus.UNAVAILABLE, 90.0),
                // Building A, Floor 2
                createSeatMapProjection("B1", "Building A", "123 Main St", "F2", "Floor 2", "S3", "A2-01", SeatStatus.BROKEN, 75.5),
                // Building B, Floor 1
                createSeatMapProjection("B2", "Building B", "456 Oak Ave", "F3", "Floor 1", "S4", "B1-01", SeatStatus.AVAILABLE, 95.0),
                createSeatMapProjection("B2", "Building B", "456 Oak Ave", "F3", "Floor 1", "S5", "B1-02", SeatStatus.UNAVAILABLE, 80.5)
        );

        when(buildingRepository.findSeatMapDashBoard()).thenReturn(seatMapData);

        // Act
        SeatMapDashboardResponse result = dashboardService.getSeatsMapDashboard();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getBuildingResponse().size());

        // Verify Building A
        SeatMapBuildingResponse buildingA = result.getBuildingResponse().get(0);
        assertEquals("B1", buildingA.getBuildingId());
        assertEquals("Building A", buildingA.getBuildingName());
        assertEquals(2, buildingA.getFloors().size());

        // Building A, Floor 1
        DetailFloorResponse floorA1 = buildingA.getFloors().get(0);
        assertEquals("F1", floorA1.getFloorId());
        assertEquals("Floor 1", floorA1.getFloorName());
        assertEquals(2, floorA1.getSeats().size());

        // Building A, Floor 2
        DetailFloorResponse floorA2 = buildingA.getFloors().get(1);
        assertEquals("F2", floorA2.getFloorId());
        assertEquals("Floor 2", floorA2.getFloorName());
        assertEquals(1, floorA2.getSeats().size());

        // Verify Building B
        SeatMapBuildingResponse buildingB = result.getBuildingResponse().get(1);
        assertEquals("B2", buildingB.getBuildingId());
        assertEquals("Building B", buildingB.getBuildingName());
        assertEquals(1, buildingB.getFloors().size());

        DetailFloorResponse floorB1 = buildingB.getFloors().get(0);
        assertEquals("F3", floorB1.getFloorId());
        assertEquals("Floor 1", floorB1.getFloorName());
        assertEquals(2, floorB1.getSeats().size());
    }

    /**
     * Test successful seat map dashboard with empty data
     */
    @Test
    void testGetSeatsMapDashboard_Success_WithEmptyData() {
        // Arrange
        when(buildingRepository.findSeatMapDashBoard()).thenReturn(Collections.emptyList());

        // Act
        SeatMapDashboardResponse result = dashboardService.getSeatsMapDashboard();

        // Assert
        assertNotNull(result);
        assertNotNull(result.getBuildingResponse());
        assertEquals(0, result.getBuildingResponse().size());

        verify(buildingRepository, times(1)).findSeatMapDashBoard();
    }

    /**
     * Test successful seat map dashboard with zero values
     */
    @Test
    void testGetSeatsMapDashboard_Success_WithZeroValues() {
        // Arrange
        List<SeatMapDashboardProjection> seatMapData = Arrays.asList(
                createSeatMapProjection("B1", "Building A", "123 Main St", "F1", "Floor 1", "S1", "A1-01", SeatStatus.AVAILABLE, 0.0),
                createSeatMapProjection("B1", "Building A", "123 Main St", "F1", "Floor 1", "S2", "A1-02", SeatStatus.UNAVAILABLE, 0.0)
        );

        when(buildingRepository.findSeatMapDashBoard()).thenReturn(seatMapData);

        // Act
        SeatMapDashboardResponse result = dashboardService.getSeatsMapDashboard();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getBuildingResponse().size());

        SeatMapBuildingResponse building = result.getBuildingResponse().get(0);
        assertEquals(1, building.getFloors().size());

        DetailFloorResponse floor = building.getFloors().get(0);
        assertEquals(2, floor.getSeats().size());

        floor.getSeats().forEach(seat -> assertEquals(0.0, seat.getScore()));
    }

    /**
     * Test when buildingRepository throws RuntimeException
     */
    @Test
    void testGetSeatsMapDashboard_RepositoryThrowsRuntimeException_ThrowsInternalServerError() {
        // Arrange
        when(buildingRepository.findSeatMapDashBoard())
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getSeatsMapDashboard();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(buildingRepository, times(1)).findSeatMapDashBoard();
    }

    /**
     * Test when buildingRepository throws SQLException
     */
    @Test
    void testGetSeatsMapDashboard_RepositoryThrowsSQLException_ThrowsInternalServerError() {
        // Arrange
        when(buildingRepository.findSeatMapDashBoard())
                .thenThrow(new RuntimeException("SQL syntax error", new SQLException("Invalid query")));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getSeatsMapDashboard();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(buildingRepository, times(1)).findSeatMapDashBoard();
    }

    /**
     * Test when processing throws NullPointerException due to null projection
     */
    @Test
    void testGetSeatsMapDashboard_NullProjectionInList_ThrowsInternalServerError() {
        // Arrange
        List<SeatMapDashboardProjection> seatMapData = Arrays.asList(
                seatMapProjection,
                null // This will cause NPE during processing
        );

        when(buildingRepository.findSeatMapDashBoard()).thenReturn(seatMapData);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getSeatsMapDashboard();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(buildingRepository, times(1)).findSeatMapDashBoard();
    }

    /**
     * Test when buildingRepository returns null
     */
    @Test
    void testGetSeatsMapDashboard_RepositoryReturnsNull_ThrowsInternalServerError() {
        // Arrange
        when(buildingRepository.findSeatMapDashBoard()).thenReturn(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getSeatsMapDashboard();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(buildingRepository, times(1)).findSeatMapDashBoard();
    }

    /**
     * Test when CustomException is thrown by repository - should be re-thrown
     */
    @Test
    void testGetSeatsMapDashboard_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        CustomException customException = new CustomException(ResponseCode.USER_NOT_FOUND);
        when(buildingRepository.findSeatMapDashBoard()).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getSeatsMapDashboard();
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception); // Should be the exact same exception

        verify(buildingRepository, times(1)).findSeatMapDashBoard();
    }

    /**
     * Test when projection returns null values
     */
    @Test
    void testGetSeatsMapDashboard_Success_WithNullValues() {
        // Arrange
        List<SeatMapDashboardProjection> seatMapData = Arrays.asList(
                createSeatMapProjection("B1", "Building A", null, "F1", "Floor 1", "S1", null, null, null),
                createSeatMapProjection("B1", "Building A", "", "F1", "Floor 1", "S2", "", SeatStatus.AVAILABLE, 0.0)
        );

        when(buildingRepository.findSeatMapDashBoard()).thenReturn(seatMapData);

        // Act
        SeatMapDashboardResponse result = dashboardService.getSeatsMapDashboard();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getBuildingResponse().size());

        SeatMapBuildingResponse building = result.getBuildingResponse().get(0);
        assertEquals(1, building.getFloors().size());

        DetailFloorResponse floor = building.getFloors().get(0);
        assertEquals(2, floor.getSeats().size());

        SeatDto seat1 = floor.getSeats().get(0);
        assertNull(seat1.getLocationCode());
        assertNull(seat1.getStatus());
        assertNull(seat1.getScore());

        SeatDto seat2 = floor.getSeats().get(1);
        assertEquals("", seat2.getLocationCode());
        assertEquals(SeatStatus.AVAILABLE, seat2.getStatus());
        assertEquals(0.0, seat2.getScore());
    }

    /**
     * Test seat map dashboard with duplicate building/floor combinations
     */
    @Test
    void testGetSeatsMapDashboard_Success_DuplicateBuildingFloorCombinations() {
        // Arrange - Same building and floor for multiple seats
        List<SeatMapDashboardProjection> seatMapData = Arrays.asList(
                createSeatMapProjection("B1", "Building A", "123 Main St", "F1", "Floor 1", "S1", "A1-01", SeatStatus.AVAILABLE, 85.5),
                createSeatMapProjection("B1", "Building A", "123 Main St", "F1", "Floor 1", "S2", "A1-02", SeatStatus.UNAVAILABLE, 90.0),
                createSeatMapProjection("B1", "Building A", "123 Main St", "F1", "Floor 1", "S3", "A1-03", SeatStatus.BROKEN, 75.5)
        );

        when(buildingRepository.findSeatMapDashBoard()).thenReturn(seatMapData);

        // Act
        SeatMapDashboardResponse result = dashboardService.getSeatsMapDashboard();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getBuildingResponse().size());

        SeatMapBuildingResponse building = result.getBuildingResponse().get(0);
        assertEquals("B1", building.getBuildingId());
        assertEquals("Building A", building.getBuildingName());
        assertEquals(1, building.getFloors().size()); // Should not duplicate floors

        DetailFloorResponse floor = building.getFloors().get(0);
        assertEquals("F1", floor.getFloorId());
        assertEquals("Floor 1", floor.getFloorName());
        assertEquals(3, floor.getSeats().size()); // All 3 seats should be in the same floor

        // Verify all seats are present
        List<String> seatIds = floor.getSeats().stream()
                .map(SeatDto::getSeatId)
                .collect(Collectors.toList());
        assertTrue(seatIds.contains("S1"));
        assertTrue(seatIds.contains("S2"));
        assertTrue(seatIds.contains("S3"));
    }

    /**
     * Test with very large numbers
     */
    @Test
    void testGetSeatsMapDashboard_Success_WithLargeNumbers() {
        // Arrange
        List<SeatMapDashboardProjection> seatMapData = Arrays.asList(
                createSeatMapProjection("B1", "Building A", "123 Main St", "F1", "Floor 1", "S1", "A1-01", SeatStatus.AVAILABLE, Double.MAX_VALUE)
        );

        when(buildingRepository.findSeatMapDashBoard()).thenReturn(seatMapData);

        // Act
        SeatMapDashboardResponse result = dashboardService.getSeatsMapDashboard();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getBuildingResponse().size());

        SeatMapBuildingResponse building = result.getBuildingResponse().get(0);
        DetailFloorResponse floor = building.getFloors().get(0);
        SeatDto seat = floor.getSeats().get(0);

        assertEquals(Double.MAX_VALUE, seat.getScore());
    }

    /**
     * Test with large dataset to verify performance and memory handling
     */
    @Test
    void testGetSeatsMapDashboard_Success_LargeDataset() {
        // Arrange - Create 1000 seats across 10 buildings, 5 floors each
        List<SeatMapDashboardProjection> seatMapData = new ArrayList<>();
        SeatStatus[] statuses = {SeatStatus.AVAILABLE, SeatStatus.UNAVAILABLE, SeatStatus.BROKEN};

        for (int b = 1; b <= 10; b++) {
            for (int f = 1; f <= 5; f++) {
                for (int s = 1; s <= 20; s++) {
                    seatMapData.add(createSeatMapProjection(
                            "B" + b, "Building " + b, "Address " + b,
                            "F" + f, "Floor " + f,
                            "S" + b + f + s, "B" + b + "F" + f + "-" + String.format("%02d", s),
                            statuses[s % 3],
                            70.0 + (s % 30)
                    ));
                }
            }
        }

        when(buildingRepository.findSeatMapDashBoard()).thenReturn(seatMapData);

        // Act
        long startTime = System.currentTimeMillis();
        SeatMapDashboardResponse result = dashboardService.getSeatsMapDashboard();
        long endTime = System.currentTimeMillis();

        // Assert
        assertNotNull(result);
        assertEquals(10, result.getBuildingResponse().size());

        // Verify structure
        for (SeatMapBuildingResponse building : result.getBuildingResponse()) {
            assertEquals(5, building.getFloors().size());
            for (DetailFloorResponse floor : building.getFloors()) {
                assertEquals(20, floor.getSeats().size());
            }
        }

        System.out.println("Processing time for 1000 seats: " + (endTime - startTime) + "ms");

        verify(buildingRepository, times(1)).findSeatMapDashBoard();
    }

    /**
     * Test re-throwing CustomException correctly
     */
    @Test
    void testGetSeatsMapDashboard_CustomExceptionIsRethrownCorrectly() {
        // Arrange
        CustomException customException = new CustomException(ResponseCode.USER_NOT_FOUND);
        when(buildingRepository.findSeatMapDashBoard()).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getSeatsMapDashboard();
        });

        // Verify it's the exact same exception type and code
        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertNotNull(exception);
        assertSame(customException, exception);
    }

    /**
     * Test with all three SeatStatus enum values
     */
    @Test
    void testGetSeatsMapDashboard_Success_WithAllSeatStatuses() {
        // Arrange
        List<SeatMapDashboardProjection> seatMapData = Arrays.asList(
                createSeatMapProjection("B1", "Building A", "123 Main St", "F1", "Floor 1", "S1", "A1-01", SeatStatus.AVAILABLE, 85.5),
                createSeatMapProjection("B1", "Building A", "123 Main St", "F1", "Floor 1", "S2", "A1-02", SeatStatus.UNAVAILABLE, 90.0),
                createSeatMapProjection("B1", "Building A", "123 Main St", "F1", "Floor 1", "S3", "A1-03", SeatStatus.BROKEN, 75.5)
        );

        when(buildingRepository.findSeatMapDashBoard()).thenReturn(seatMapData);

        // Act
        SeatMapDashboardResponse result = dashboardService.getSeatsMapDashboard();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getBuildingResponse().size());

        SeatMapBuildingResponse building = result.getBuildingResponse().get(0);
        DetailFloorResponse floor = building.getFloors().get(0);
        assertEquals(3, floor.getSeats().size());

        // Verify all seat statuses are present
        List<SeatStatus> statuses = floor.getSeats().stream()
                .map(SeatDto::getStatus)
                .collect(Collectors.toList());

        assertTrue(statuses.contains(SeatStatus.AVAILABLE));
        assertTrue(statuses.contains(SeatStatus.UNAVAILABLE));
        assertTrue(statuses.contains(SeatStatus.BROKEN));
    }

    /**
     * Test with address field included
     */
    @Test
    void testGetSeatsMapDashboard_Success_WithAddressField() {
        // Arrange
        List<SeatMapDashboardProjection> seatMapData = Arrays.asList(
                createSeatMapProjection("B1", "Building A", "123 Main Street, City, State", "F1", "Floor 1", "S1", "A1-01", SeatStatus.AVAILABLE, 85.5)
        );

        when(buildingRepository.findSeatMapDashBoard()).thenReturn(seatMapData);

        // Act
        SeatMapDashboardResponse result = dashboardService.getSeatsMapDashboard();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getBuildingResponse().size());

        SeatMapBuildingResponse building = result.getBuildingResponse().get(0);
        assertEquals("B1", building.getBuildingId());
        assertEquals("Building A", building.getBuildingName());
        // Note: Address field might be used in building response if your implementation includes it

        verify(buildingRepository, times(1)).findSeatMapDashBoard();
    }
}
