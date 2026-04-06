package com.finalProject.BookingMeetingRoom.service.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest_getDashboard {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private Authentication connectedUser;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private User testUser;
    private LastCheckedInDtoProjection lastCheckedInProjection;

    @BeforeEach
    void setUp() {
        UserInfo userInfo = new UserInfo();
        userInfo.setEmail("test@example.com");

        testUser = new User();
        testUser.setId("user-123");
        testUser.setUserInfo(userInfo);

        lastCheckedInProjection = new LastCheckedInDtoProjection() {
            @Override
            public String getSeatId() {
                return "SEAT-001";
            }

            @Override
            public String getBuildingName() {
                return "Building A";
            }

            @Override
            public String getFloorName() {
                return "Floor 1";
            }

            @Override
            public LocalDateTime getLastCheckedInTime() {
                return LocalDateTime.of(2025, 1, 15, 9, 30);
            }
        };
    }

    /**
     * Test successful dashboard retrieval with all data
     */
    @Test
    void testGetDashboard_Success_WithAllData() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.countActiveReservations("user-123")).thenReturn(2);
        when(reservationRepository.countUpcomingReservations("user-123")).thenReturn(5);
        when(reservationRepository.findLastCheckedInOfUser("user-123")).thenReturn(lastCheckedInProjection);
        when(reservationRepository.totalHoursThisWeek("user-123")).thenReturn(25);
        when(reservationRepository.totalHoursThisMonth("user-123")).thenReturn(80);
        when(reservationRepository.countReservationsThisMonth("user-123")).thenReturn(15);

        // Act
        EmployeeDashboardResponse result = dashboardService.getDashboard(connectedUser);

        // Assert
        assertNotNull(result);

        // Verify ActiveReservationDto
        assertNotNull(result.getActiveReservationDto());
        assertEquals(2, result.getActiveReservationDto().getTodayActiveReservations());
        assertEquals(5, result.getActiveReservationDto().getUpcomingReservations());

        // Verify LastCheckedInDto
        assertNotNull(result.getLastCheckedInDto());
        assertEquals("SEAT-001", result.getLastCheckedInDto().getSeatId());
        assertEquals("Building A", result.getLastCheckedInDto().getBuildingName());
        assertEquals("Floor 1", result.getLastCheckedInDto().getFloorName());
        assertEquals(LocalDateTime.of(2025, 1, 15, 9, 30), result.getLastCheckedInDto().getLastCheckedInTime());

        // Verify HoursThisWeekDto
        assertNotNull(result.getHoursThisWeekDto());
        assertEquals(25, result.getHoursThisWeekDto().getTotalHoursThisWeek());
        assertEquals(80, result.getHoursThisWeekDto().getTotalHoursThisMonth());

        // Verify total reservations
        assertEquals(15, result.getTotalReservationsThisMonth());

        // Verify all repository methods were called
        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(reservationRepository, times(1)).countActiveReservations("user-123");
        verify(reservationRepository, times(1)).countUpcomingReservations("user-123");
        verify(reservationRepository, times(1)).findLastCheckedInOfUser("user-123");
        verify(reservationRepository, times(1)).totalHoursThisWeek("user-123");
        verify(reservationRepository, times(1)).totalHoursThisMonth("user-123");
        verify(reservationRepository, times(1)).countReservationsThisMonth("user-123");
    }

    /**
     * Test dashboard retrieval with zero values
     */
    @Test
    void testGetDashboard_Success_WithZeroValues() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.countActiveReservations("user-123")).thenReturn(0);
        when(reservationRepository.countUpcomingReservations("user-123")).thenReturn(0);
        when(reservationRepository.findLastCheckedInOfUser("user-123")).thenReturn(lastCheckedInProjection);
        when(reservationRepository.totalHoursThisWeek("user-123")).thenReturn(0);
        when(reservationRepository.totalHoursThisMonth("user-123")).thenReturn(0);
        when(reservationRepository.countReservationsThisMonth("user-123")).thenReturn(0);

        // Act
        EmployeeDashboardResponse result = dashboardService.getDashboard(connectedUser);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getActiveReservationDto().getTodayActiveReservations());
        assertEquals(0, result.getActiveReservationDto().getUpcomingReservations());
        assertEquals(0, result.getHoursThisWeekDto().getTotalHoursThisWeek());
        assertEquals(0, result.getHoursThisWeekDto().getTotalHoursThisMonth());
        assertEquals(0, result.getTotalReservationsThisMonth());
    }

    /**
     * Test when user is not found - should throw CustomException
     */
    @Test
    void testGetDashboard_UserNotFound_ThrowsCustomException() {
        // Arrange
        when(connectedUser.getName()).thenReturn("nonexistent@example.com");
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboard(connectedUser);
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());

        // Verify only user lookup was called
        verify(userRepository, times(1)).findByEmail("nonexistent@example.com");
        verify(reservationRepository, never()).countActiveReservations(any());
        verify(reservationRepository, never()).countUpcomingReservations(any());
        verify(reservationRepository, never()).findLastCheckedInOfUser(any());
    }

    /**
     * Test when userRepository throws exception
     */
    @Test
    void testGetDashboard_UserRepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com"))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboard(connectedUser);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(reservationRepository, never()).countActiveReservations(any());
    }

    /**
     * Test when reservationRepository.countActiveReservations throws exception
     */
    @Test
    void testGetDashboard_CountActiveReservationsThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.countActiveReservations("user-123"))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboard(connectedUser);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(reservationRepository, times(1)).countActiveReservations("user-123");
        verify(reservationRepository, never()).countUpcomingReservations(any());
    }

    /**
     * Test when reservationRepository.countUpcomingReservations throws exception
     */
    @Test
    void testGetDashboard_CountUpcomingReservationsThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.countActiveReservations("user-123")).thenReturn(2);
        when(reservationRepository.countUpcomingReservations("user-123"))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboard(connectedUser);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(reservationRepository, times(1)).countActiveReservations("user-123");
        verify(reservationRepository, times(1)).countUpcomingReservations("user-123");
        verify(reservationRepository, never()).findLastCheckedInOfUser(any());
    }

    /**
     * Test when reservationRepository.findLastCheckedInOfUser throws exception
     */
    @Test
    void testGetDashboard_FindLastCheckedInThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.countActiveReservations("user-123")).thenReturn(2);
        when(reservationRepository.countUpcomingReservations("user-123")).thenReturn(5);
        when(reservationRepository.findLastCheckedInOfUser("user-123"))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboard(connectedUser);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(reservationRepository, times(1)).findLastCheckedInOfUser("user-123");
        verify(reservationRepository, never()).totalHoursThisWeek(any());
    }

    /**
     * Test when reservationRepository.totalHoursThisWeek throws exception
     */
    @Test
    void testGetDashboard_TotalHoursThisWeekThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.countActiveReservations("user-123")).thenReturn(2);
        when(reservationRepository.countUpcomingReservations("user-123")).thenReturn(5);
        when(reservationRepository.findLastCheckedInOfUser("user-123")).thenReturn(lastCheckedInProjection);
        when(reservationRepository.totalHoursThisWeek("user-123"))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboard(connectedUser);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(reservationRepository, times(1)).totalHoursThisWeek("user-123");
        verify(reservationRepository, never()).totalHoursThisMonth(any());
    }

    /**
     * Test when reservationRepository.totalHoursThisMonth throws exception
     */
    @Test
    void testGetDashboard_TotalHoursThisMonthThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.countActiveReservations("user-123")).thenReturn(2);
        when(reservationRepository.countUpcomingReservations("user-123")).thenReturn(5);
        when(reservationRepository.findLastCheckedInOfUser("user-123")).thenReturn(lastCheckedInProjection);
        when(reservationRepository.totalHoursThisWeek("user-123")).thenReturn(25);
        when(reservationRepository.totalHoursThisMonth("user-123"))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboard(connectedUser);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(reservationRepository, times(1)).totalHoursThisMonth("user-123");
        verify(reservationRepository, never()).countReservationsThisMonth(any());
    }

    /**
     * Test when reservationRepository.countReservationsThisMonth throws exception
     */
    @Test
    void testGetDashboard_CountReservationsThisMonthThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.countActiveReservations("user-123")).thenReturn(2);
        when(reservationRepository.countUpcomingReservations("user-123")).thenReturn(5);
        when(reservationRepository.findLastCheckedInOfUser("user-123")).thenReturn(lastCheckedInProjection);
        when(reservationRepository.totalHoursThisWeek("user-123")).thenReturn(25);
        when(reservationRepository.totalHoursThisMonth("user-123")).thenReturn(80);
        when(reservationRepository.countReservationsThisMonth("user-123"))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboard(connectedUser);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(reservationRepository, times(1)).countReservationsThisMonth("user-123");
    }

    /**
     * Test when Authentication.getName() returns null
     */
    @Test
    void testGetDashboard_AuthenticationNameIsNull_ThrowsInternalServerError() {
        // Arrange
        when(connectedUser.getName()).thenReturn(null);
        when(userRepository.findByEmail(null)).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboard(connectedUser);
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());

        verify(userRepository, times(1)).findByEmail(null);
    }

    /**
     * Test when lastCheckedInProjection returns null values
     */
    @Test
    void testGetDashboard_Success_WithNullLastCheckedInValues() {
        // Arrange
        LastCheckedInDtoProjection nullProjection = new LastCheckedInDtoProjection() {
            @Override
            public String getSeatId() {
                return null;
            }

            @Override
            public String getBuildingName() {
                return null;
            }

            @Override
            public String getFloorName() {
                return null;
            }

            @Override
            public LocalDateTime getLastCheckedInTime() {
                return null;
            }
        };

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.countActiveReservations("user-123")).thenReturn(2);
        when(reservationRepository.countUpcomingReservations("user-123")).thenReturn(5);
        when(reservationRepository.findLastCheckedInOfUser("user-123")).thenReturn(nullProjection);
        when(reservationRepository.totalHoursThisWeek("user-123")).thenReturn(25);
        when(reservationRepository.totalHoursThisMonth("user-123")).thenReturn(80);
        when(reservationRepository.countReservationsThisMonth("user-123")).thenReturn(15);

        // Act
        EmployeeDashboardResponse result = dashboardService.getDashboard(connectedUser);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getLastCheckedInDto());
        assertNull(result.getLastCheckedInDto().getSeatId());
        assertNull(result.getLastCheckedInDto().getBuildingName());
        assertNull(result.getLastCheckedInDto().getFloorName());
        assertNull(result.getLastCheckedInDto().getLastCheckedInTime());
    }

    /**
     * Test with very large numbers
     */
    @Test
    void testGetDashboard_Success_WithLargeNumbers() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.countActiveReservations("user-123")).thenReturn(Integer.MAX_VALUE);
        when(reservationRepository.countUpcomingReservations("user-123")).thenReturn(Integer.MAX_VALUE);
        when(reservationRepository.findLastCheckedInOfUser("user-123")).thenReturn(lastCheckedInProjection);
        when(reservationRepository.totalHoursThisWeek("user-123")).thenReturn(Integer.MAX_VALUE);
        when(reservationRepository.totalHoursThisMonth("user-123")).thenReturn(Integer.MAX_VALUE);
        when(reservationRepository.countReservationsThisMonth("user-123")).thenReturn(Integer.MAX_VALUE);

        // Act
        EmployeeDashboardResponse result = dashboardService.getDashboard(connectedUser);

        // Assert
        assertNotNull(result);
        assertEquals(Integer.MAX_VALUE, result.getActiveReservationDto().getTodayActiveReservations());
        assertEquals(Integer.MAX_VALUE, result.getActiveReservationDto().getUpcomingReservations());
        assertEquals(Integer.MAX_VALUE, result.getHoursThisWeekDto().getTotalHoursThisWeek());
        assertEquals(Integer.MAX_VALUE, result.getHoursThisWeekDto().getTotalHoursThisMonth());
        assertEquals(Integer.MAX_VALUE, result.getTotalReservationsThisMonth());
    }

    /**
     * Test re-throwing CustomException correctly
     */
    @Test
    void testGetDashboard_CustomExceptionIsRethrownCorrectly() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboard(connectedUser);
        });

        // Verify it's the exact same exception type and code
        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertNotNull(exception);
    }
}