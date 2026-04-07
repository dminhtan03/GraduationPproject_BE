package com.finalProject.BookingMeetingRoom.service.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest_getDashboardOverview {

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private Logger logger;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private BuildingOccupancyProjection buildingOccupancyProjection;
    private RecentActivityProjection recentActivityProjection;

    @BeforeEach
    void setUp() {
        buildingOccupancyProjection = new BuildingOccupancyProjection() {
            @Override
            public String getBuildingName() { return "Building A"; }
            @Override
            public int getOccupied() { return 25; }
            @Override
            public int getTotalRooms() { return 100; }
            @Override
            public int getBrokenRooms() { return 5; }
            @Override
            public int getAvailableRooms() { return 70; }
            @Override
            public int getOccupancyRate() { return 25; }
        };

        recentActivityProjection = new RecentActivityProjection() {
            @Override
            public String getUserName() { return "John Doe"; }
            @Override
            public String getLocationCode() { return "A1-01"; }
            @Override
            public String getBuildingName() { return "Building A"; }
            @Override
            public String getReservationStatus() { return ReservationStatus.RESERVED.toString(); }
            @Override
            public LocalDateTime getReservationTime() { return LocalDateTime.of(2025, 1, 15, 9, 30); }
        };
    }

    // Helper method để tạo BuildingOccupancyProjection
    private BuildingOccupancyProjection createBuildingOccupancyProjection(String buildingName, int occupied,
                                                                          int totalRooms, int brokenRooms,
                                                                          int availableRooms, int occupancyRate) {
        return new BuildingOccupancyProjection() {
            @Override
            public String getBuildingName() { return buildingName; }
            @Override
            public int getOccupied() { return occupied; }
            @Override
            public int getTotalRooms() { return totalRooms; }
            @Override
            public int getBrokenRooms() { return brokenRooms; }
            @Override
            public int getAvailableRooms() { return availableRooms; }
            @Override
            public int getOccupancyRate() { return occupancyRate; }
        };
    }

    // Helper method để tạo RecentActivityProjection
    private RecentActivityProjection createRecentActivityProjection(String userName, String locationCode,
                                                                    String buildingName, String status,
                                                                    LocalDateTime reservationTime) {
        return new RecentActivityProjection() {
            @Override
            public String getUserName() { return userName; }
            @Override
            public String getLocationCode() { return locationCode; }
            @Override
            public String getBuildingName() { return buildingName; }
            @Override
            public String getReservationStatus() { return status; }
            @Override
            public LocalDateTime getReservationTime() { return reservationTime; }
        };
    }

    /**
     * Test successful dashboard overview retrieval with all data
     */
    @Test
    void testGetDashboardOverview_Success_WithAllData() {
        // Arrange
        List<BuildingOccupancyProjection> buildingOccupancy = Arrays.asList(buildingOccupancyProjection);
        List<RecentActivityProjection> recentActivity = Arrays.asList(recentActivityProjection);

        when(buildingRepository.findBuildingOccupancy()).thenReturn(buildingOccupancy);
        when(reservationRepository.findRecentActivity()).thenReturn(recentActivity);

        // Act
        DashboardOverviewResponse result = dashboardService.getDashboardOverview();

        // Assert
        assertNotNull(result);
        assertNotNull(result.getBuildingOccupancyDto());
        assertNotNull(result.getRecentActivityDto());

        // Verify building occupancy
        assertEquals(1, result.getBuildingOccupancyDto().size());
        BuildingOccupancyDto occupancyDto = result.getBuildingOccupancyDto().get(0);
        assertEquals("Building A", occupancyDto.getBuildingName());
        assertEquals(25, occupancyDto.getOccupied());
        assertEquals(100, occupancyDto.getTotalRooms());
        assertEquals(5, occupancyDto.getBrokenRooms());
        assertEquals(70, occupancyDto.getAvailableRooms());
        assertEquals(25, occupancyDto.getOccupancyRate());

        // Verify recent activity
        assertEquals(1, result.getRecentActivityDto().size());
        RecentActivityDto activityDto = result.getRecentActivityDto().get(0);
        assertEquals("John Doe", activityDto.getUserName());
        assertEquals("A1-01", activityDto.getLocationCode());
        assertEquals("Building A", activityDto.getBuildingName());
        assertEquals(ReservationStatus.RESERVED.toString(), activityDto.getReservationStatus());
        assertEquals(LocalDateTime.of(2025, 1, 15, 9, 30), activityDto.getReservationTime());

        verify(buildingRepository, times(1)).findBuildingOccupancy();
        verify(reservationRepository, times(1)).findRecentActivity();
    }

    /**
     * Test successful dashboard overview with multiple buildings and activities
     */
    @Test
    void testGetDashboardOverview_Success_WithMultipleData() {
        // Arrange
        List<BuildingOccupancyProjection> buildingOccupancy = Arrays.asList(
                createBuildingOccupancyProjection("Building A", 25, 100, 5, 70, 25),
                createBuildingOccupancyProjection("Building B", 40, 80, 2, 38, 50),
                createBuildingOccupancyProjection("Building C", 10, 60, 8, 42, 17)
        );

        List<RecentActivityProjection> recentActivity = Arrays.asList(
                createRecentActivityProjection("John Doe", "A1-01", "Building A", ReservationStatus.RESERVED.toString(),
                        LocalDateTime.of(2025, 1, 15, 9, 30)),
                createRecentActivityProjection("Jane Smith", "B2-05", "Building B", ReservationStatus.IN_USE.toString(),
                        LocalDateTime.of(2025, 1, 15, 10, 15)),
                createRecentActivityProjection("Bob Wilson", "C1-03", "Building C", ReservationStatus.FAILED.toString(),
                        LocalDateTime.of(2025, 1, 15, 11, 0))
        );

        when(buildingRepository.findBuildingOccupancy()).thenReturn(buildingOccupancy);
        when(reservationRepository.findRecentActivity()).thenReturn(recentActivity);

        // Act
        DashboardOverviewResponse result = dashboardService.getDashboardOverview();

        // Assert
        assertNotNull(result);
        assertEquals(3, result.getBuildingOccupancyDto().size());
        assertEquals(3, result.getRecentActivityDto().size());

        // Verify building occupancy data
        BuildingOccupancyDto buildingA = result.getBuildingOccupancyDto().get(0);
        assertEquals("Building A", buildingA.getBuildingName());
        assertEquals(25, buildingA.getOccupied());

        BuildingOccupancyDto buildingB = result.getBuildingOccupancyDto().get(1);
        assertEquals("Building B", buildingB.getBuildingName());
        assertEquals(40, buildingB.getOccupied());

        BuildingOccupancyDto buildingC = result.getBuildingOccupancyDto().get(2);
        assertEquals("Building C", buildingC.getBuildingName());
        assertEquals(10, buildingC.getOccupied());

        // Verify recent activity data
        RecentActivityDto activity1 = result.getRecentActivityDto().get(0);
        assertEquals("John Doe", activity1.getUserName());
        assertEquals(ReservationStatus.RESERVED.toString(), activity1.getReservationStatus());

        RecentActivityDto activity2 = result.getRecentActivityDto().get(1);
        assertEquals("Jane Smith", activity2.getUserName());
        assertEquals(ReservationStatus.IN_USE.toString(), activity2.getReservationStatus());

        RecentActivityDto activity3 = result.getRecentActivityDto().get(2);
        assertEquals("Bob Wilson", activity3.getUserName());
        assertEquals(ReservationStatus.FAILED.toString(), activity3.getReservationStatus());
    }

    /**
     * Test successful dashboard overview with empty data
     */
    @Test
    void testGetDashboardOverview_Success_WithEmptyData() {
        // Arrange
        when(buildingRepository.findBuildingOccupancy()).thenReturn(Collections.emptyList());
        when(reservationRepository.findRecentActivity()).thenReturn(Collections.emptyList());

        // Act
        DashboardOverviewResponse result = dashboardService.getDashboardOverview();

        // Assert
        assertNotNull(result);
        assertNotNull(result.getBuildingOccupancyDto());
        assertNotNull(result.getRecentActivityDto());
        assertEquals(0, result.getBuildingOccupancyDto().size());
        assertEquals(0, result.getRecentActivityDto().size());

        verify(buildingRepository, times(1)).findBuildingOccupancy();
        verify(reservationRepository, times(1)).findRecentActivity();
    }

    /**
     * Test successful dashboard overview with zero values
     */
    @Test
    void testGetDashboardOverview_Success_WithZeroValues() {
        // Arrange
        List<BuildingOccupancyProjection> buildingOccupancy = Arrays.asList(
                createBuildingOccupancyProjection("Building A", 0, 0, 0, 0, 0)
        );

        List<RecentActivityProjection> recentActivity = Arrays.asList(recentActivityProjection);

        when(buildingRepository.findBuildingOccupancy()).thenReturn(buildingOccupancy);
        when(reservationRepository.findRecentActivity()).thenReturn(recentActivity);

        // Act
        DashboardOverviewResponse result = dashboardService.getDashboardOverview();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getBuildingOccupancyDto().size());

        BuildingOccupancyDto occupancyDto = result.getBuildingOccupancyDto().get(0);
        assertEquals(0, occupancyDto.getOccupied());
        assertEquals(0, occupancyDto.getTotalRooms());
        assertEquals(0, occupancyDto.getBrokenRooms());
        assertEquals(0, occupancyDto.getAvailableRooms());
        assertEquals(0, occupancyDto.getOccupancyRate());
    }

    /**
     * Test when buildingRepository.findBuildingOccupancy throws RuntimeException
     */
    @Test
    void testGetDashboardOverview_BuildingRepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(buildingRepository.findBuildingOccupancy())
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardOverview();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(buildingRepository, times(1)).findBuildingOccupancy();
        verify(reservationRepository, never()).findRecentActivity();
    }

    /**
     * Test when reservationRepository.findRecentActivity throws RuntimeException
     */
    @Test
    void testGetDashboardOverview_ReservationRepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(buildingRepository.findBuildingOccupancy()).thenReturn(Collections.emptyList());
        when(reservationRepository.findRecentActivity())
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardOverview();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(buildingRepository, times(1)).findBuildingOccupancy();
        verify(reservationRepository, times(1)).findRecentActivity();
    }

    /**
     * Test when buildingRepository throws SQLException
     */
    @Test
    void testGetDashboardOverview_BuildingRepositoryThrowsSQLException_ThrowsInternalServerError() {
        // Arrange
        when(buildingRepository.findBuildingOccupancy())
                .thenThrow(new RuntimeException("SQL error", new SQLException("Connection timeout")));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardOverview();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(buildingRepository, times(1)).findBuildingOccupancy();
    }

    /**
     * Test when reservationRepository throws SQLException
     */
    @Test
    void testGetDashboardOverview_ReservationRepositoryThrowsSQLException_ThrowsInternalServerError() {
        // Arrange
        when(buildingRepository.findBuildingOccupancy()).thenReturn(Collections.emptyList());
        when(reservationRepository.findRecentActivity())
                .thenThrow(new RuntimeException("SQL error", new SQLException("Table not found")));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardOverview();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(reservationRepository, times(1)).findRecentActivity();
    }

    /**
     * Test when CustomException is thrown by repository - should be re-thrown
     */
    @Test
    void testGetDashboardOverview_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        CustomException customException = new CustomException(ResponseCode.USER_NOT_FOUND);
        when(buildingRepository.findBuildingOccupancy()).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardOverview();
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception); // Should be the exact same exception

        verify(buildingRepository, times(1)).findBuildingOccupancy();
        verify(logger, never()).error(anyString(), any(Exception.class)); // Logger not called for CustomException
    }

    /**
     * Test when projection returns null values - Note: primitive int cannot be null
     */
    @Test
    void testGetDashboardOverview_Success_WithNullStringValues() {
        // Arrange - Only String values can be null, int values will be 0
        List<BuildingOccupancyProjection> buildingOccupancy = Arrays.asList(
                new BuildingOccupancyProjection() {
                    @Override
                    public String getBuildingName() { return null; }
                    @Override
                    public int getOccupied() { return 0; }
                    @Override
                    public int getTotalRooms() { return 0; }
                    @Override
                    public int getBrokenRooms() { return 0; }
                    @Override
                    public int getAvailableRooms() { return 0; }
                    @Override
                    public int getOccupancyRate() { return 0; }
                }
        );

        List<RecentActivityProjection> recentActivity = Arrays.asList(
                createRecentActivityProjection(null, null, null, null, null)
        );

        when(buildingRepository.findBuildingOccupancy()).thenReturn(buildingOccupancy);
        when(reservationRepository.findRecentActivity()).thenReturn(recentActivity);

        // Act
        DashboardOverviewResponse result = dashboardService.getDashboardOverview();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getBuildingOccupancyDto().size());
        assertEquals(1, result.getRecentActivityDto().size());

        BuildingOccupancyDto occupancyDto = result.getBuildingOccupancyDto().get(0);
        assertNull(occupancyDto.getBuildingName());
        assertEquals(0, occupancyDto.getOccupied());
        assertEquals(0, occupancyDto.getTotalRooms());
        assertEquals(0, occupancyDto.getBrokenRooms());
        assertEquals(0, occupancyDto.getAvailableRooms());
        assertEquals(0, occupancyDto.getOccupancyRate());

        RecentActivityDto activityDto = result.getRecentActivityDto().get(0);
        assertNull(activityDto.getUserName());
        assertNull(activityDto.getLocationCode());
        assertNull(activityDto.getBuildingName());
        assertNull(activityDto.getReservationStatus());
        assertNull(activityDto.getReservationTime());
    }

    /**
     * Test when stream processing throws exception due to null projection in list
     */
    @Test
    void testGetDashboardOverview_NullProjectionInList_ThrowsInternalServerError() {
        // Arrange
        List<BuildingOccupancyProjection> buildingOccupancy = Arrays.asList(
                buildingOccupancyProjection,
                null // This will cause NPE during stream processing
        );

        when(buildingRepository.findBuildingOccupancy()).thenReturn(buildingOccupancy);
        when(reservationRepository.findRecentActivity()).thenReturn(Collections.emptyList());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardOverview();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(buildingRepository, times(1)).findBuildingOccupancy();
        verify(reservationRepository, times(1)).findRecentActivity();
    }

    /**
     * Test re-throwing CustomException correctly
     */
    @Test
    void testGetDashboardOverview_CustomExceptionIsRethrownCorrectly() {
        // Arrange
        CustomException customException = new CustomException(ResponseCode.USER_NOT_FOUND);
        when(buildingRepository.findBuildingOccupancy()).thenReturn(Collections.emptyList());
        when(reservationRepository.findRecentActivity()).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardOverview();
        });

        // Verify it's the exact same exception type and code
        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertNotNull(exception);
        assertSame(customException, exception);
    }

    /**
     * Test with very large numbers
     */
    @Test
    void testGetDashboardOverview_Success_WithLargeNumbers() {
        // Arrange
        List<BuildingOccupancyProjection> buildingOccupancy = Arrays.asList(
                createBuildingOccupancyProjection("Building A", Integer.MAX_VALUE, Integer.MAX_VALUE,
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
        );

        List<RecentActivityProjection> recentActivity = Arrays.asList(recentActivityProjection);

        when(buildingRepository.findBuildingOccupancy()).thenReturn(buildingOccupancy);
        when(reservationRepository.findRecentActivity()).thenReturn(recentActivity);

        // Act
        DashboardOverviewResponse result = dashboardService.getDashboardOverview();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getBuildingOccupancyDto().size());

        BuildingOccupancyDto occupancyDto = result.getBuildingOccupancyDto().get(0);
        assertEquals(Integer.MAX_VALUE, occupancyDto.getOccupied());
        assertEquals(Integer.MAX_VALUE, occupancyDto.getTotalRooms());
        assertEquals(Integer.MAX_VALUE, occupancyDto.getBrokenRooms());
        assertEquals(Integer.MAX_VALUE, occupancyDto.getAvailableRooms());
        assertEquals(Integer.MAX_VALUE, occupancyDto.getOccupancyRate());
    }

    /**
     * Test with different reservation statuses
     */
    @Test
    void testGetDashboardOverview_Success_WithAllReservationStatuses() {
        // Arrange
        List<BuildingOccupancyProjection> buildingOccupancy = Arrays.asList(buildingOccupancyProjection);

        List<RecentActivityProjection> recentActivity = Arrays.asList(
                createRecentActivityProjection("User1", "A1-01", "Building A", ReservationStatus.PENDING.toString(),
                        LocalDateTime.of(2025, 1, 15, 9, 0)),
                createRecentActivityProjection("User2", "A1-02", "Building A", ReservationStatus.RESERVED.toString(),
                        LocalDateTime.of(2025, 1, 15, 9, 30)),
                createRecentActivityProjection("User3", "A1-03", "Building A", ReservationStatus.IN_USE.toString(),
                        LocalDateTime.of(2025, 1, 15, 10, 0)),
                createRecentActivityProjection("User4", "A1-04", "Building A", ReservationStatus.FAILED.toString(),
                        LocalDateTime.of(2025, 1, 15, 10, 30))
        );

        when(buildingRepository.findBuildingOccupancy()).thenReturn(buildingOccupancy);
        when(reservationRepository.findRecentActivity()).thenReturn(recentActivity);

        // Act
        DashboardOverviewResponse result = dashboardService.getDashboardOverview();

        // Assert
        assertNotNull(result);
        assertEquals(4, result.getRecentActivityDto().size());

        // Verify all reservation statuses are present
        List<String> statuses = result.getRecentActivityDto().stream()
                .map(RecentActivityDto::getReservationStatus)
                .toList();

        assertTrue(statuses.contains(ReservationStatus.PENDING.toString()));
        assertTrue(statuses.contains(ReservationStatus.RESERVED.toString()));
        assertTrue(statuses.contains(ReservationStatus.IN_USE.toString()));
        assertTrue(statuses.contains(ReservationStatus.FAILED.toString()));
    }
}