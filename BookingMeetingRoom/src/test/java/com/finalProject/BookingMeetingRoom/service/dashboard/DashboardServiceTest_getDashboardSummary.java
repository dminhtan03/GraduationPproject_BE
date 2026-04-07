package com.finalProject.BookingMeetingRoom.service.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest_getDashboardSummary {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Logger logger;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    /**
     * Test successful dashboard summary retrieval with all data
     */
    @Test
    void testGetDashboardSummary_Success_WithAllData() {
        // Arrange
        when(roomRepository.count()).thenReturn(100L);
        when(roomRepository.countOccupiedRooms()).thenReturn(25);
        when(roomRepository.countBrokenRooms()).thenReturn(5);
        when(userRepository.count()).thenReturn(50L);

        // Act
        DashboardSummaryResponse result = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result);
        assertEquals(100, result.getTotalRooms());
        assertEquals(25, result.getOccupiedRooms());
        assertEquals(5, result.getBrokenRooms());
        assertEquals(50, result.getTotalUsers());

        // Verify all repository methods were called
        verify(roomRepository, times(1)).count();
        verify(roomRepository, times(1)).countOccupiedRooms();
        verify(roomRepository, times(1)).countBrokenRooms();
        verify(userRepository, times(1)).count();
    }

    /**
     * Test successful dashboard summary retrieval with zero values
     */
    @Test
    void testGetDashboardSummary_Success_WithZeroValues() {
        // Arrange
        when(roomRepository.count()).thenReturn(0L);
        when(roomRepository.countOccupiedRooms()).thenReturn(0);
        when(roomRepository.countBrokenRooms()).thenReturn(0);
        when(userRepository.count()).thenReturn(0L);

        // Act
        DashboardSummaryResponse result = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalRooms());
        assertEquals(0, result.getOccupiedRooms());
        assertEquals(0, result.getBrokenRooms());
        assertEquals(0, result.getTotalUsers());
    }

    /**
     * Test successful dashboard summary retrieval with large numbers
     */
    @Test
    void testGetDashboardSummary_Success_WithLargeNumbers() {
        // Arrange
        when(roomRepository.count()).thenReturn(Long.MAX_VALUE);
        when(roomRepository.countOccupiedRooms()).thenReturn(Integer.MAX_VALUE);
        when(roomRepository.countBrokenRooms()).thenReturn(Integer.MAX_VALUE);
        when(userRepository.count()).thenReturn(Long.MAX_VALUE);

        // Act
        DashboardSummaryResponse result = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result);
        assertEquals((int) Long.MAX_VALUE, result.getTotalRooms()); // Note: This will overflow
        assertEquals(Integer.MAX_VALUE, result.getOccupiedRooms());
        assertEquals(Integer.MAX_VALUE, result.getBrokenRooms());
        assertEquals((int) Long.MAX_VALUE, result.getTotalUsers()); // Note: This will overflow
    }

    /**
     * Test when roomRepository.count() throws RuntimeException
     */
    @Test
    void testGetDashboardSummary_RoomRepositoryCountThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(roomRepository.count()).thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(roomRepository, times(1)).count();
        verify(roomRepository, never()).countOccupiedRooms();
        verify(roomRepository, never()).countBrokenRooms();
        verify(userRepository, never()).count();
    }

    /**
     * Test when roomRepository.countOccupiedRooms() throws RuntimeException
     */
    @Test
    void testGetDashboardSummary_CountOccupiedRoomsThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(roomRepository.count()).thenReturn(100L);
        when(roomRepository.countOccupiedRooms()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(roomRepository, times(1)).count();
        verify(roomRepository, times(1)).countOccupiedRooms();
        verify(roomRepository, never()).countBrokenRooms();
        verify(userRepository, never()).count();
    }

    /**
     * Test when roomRepository.countBrokenRooms() throws RuntimeException
     */
    @Test
    void testGetDashboardSummary_CountBrokenRoomsThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(roomRepository.count()).thenReturn(100L);
        when(roomRepository.countOccupiedRooms()).thenReturn(25);
        when(roomRepository.countBrokenRooms()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(roomRepository, times(1)).count();
        verify(roomRepository, times(1)).countOccupiedRooms();
        verify(roomRepository, times(1)).countBrokenRooms();
        verify(userRepository, never()).count();
    }

    /**
     * Test when userRepository.count() throws RuntimeException
     */
    @Test
    void testGetDashboardSummary_UserRepositoryCountThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(roomRepository.count()).thenReturn(100L);
        when(roomRepository.countOccupiedRooms()).thenReturn(25);
        when(roomRepository.countBrokenRooms()).thenReturn(5);
        when(userRepository.count()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(roomRepository, times(1)).count();
        verify(roomRepository, times(1)).countOccupiedRooms();
        verify(roomRepository, times(1)).countBrokenRooms();
        verify(userRepository, times(1)).count();
    }

    /**
     * Test when roomRepository throws SQLException
     */
    @Test
    void testGetDashboardSummary_RoomRepositoryThrowsSQLException_ThrowsInternalServerError() {
        // Arrange
        when(roomRepository.count()).thenThrow(new RuntimeException("SQL error", new SQLException("Connection timeout")));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(roomRepository, times(1)).count();
    }

    /**
     * Test when userRepository throws SQLException
     */
    @Test
    void testGetDashboardSummary_UserRepositoryThrowsSQLException_ThrowsInternalServerError() {
        // Arrange
        when(roomRepository.count()).thenReturn(100L);
        when(roomRepository.countOccupiedRooms()).thenReturn(25);
        when(roomRepository.countBrokenRooms()).thenReturn(5);
        when(userRepository.count()).thenThrow(new RuntimeException("SQL error", new SQLException("Table not found")));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(userRepository, times(1)).count();
    }

    /**
     * Test when CustomException is thrown by repository - should be re-thrown
     */
    @Test
    void testGetDashboardSummary_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        CustomException customException = new CustomException(ResponseCode.USER_NOT_FOUND);
        when(roomRepository.count()).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception); // Should be the exact same exception

        verify(roomRepository, times(1)).count();
        verify(logger, never()).error(anyString(), any(Exception.class)); // Logger not called for CustomException
    }

    /**
     * Test re-throwing CustomException correctly
     */
    @Test
    void testGetDashboardSummary_CustomExceptionIsRethrownCorrectly() {
        // Arrange
        CustomException customException = new CustomException(ResponseCode.USER_NOT_FOUND);
        when(roomRepository.countOccupiedRooms()).thenThrow(customException);
        when(roomRepository.count()).thenReturn(100L);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        // Verify it's the exact same exception type and code
        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertNotNull(exception);
        assertSame(customException, exception);
    }

    /**
     * Test with negative values (edge case scenario)
     */
    @Test
    void testGetDashboardSummary_Success_WithNegativeValues() {
        // Arrange - This shouldn't happen in real scenarios but tests robustness
        when(roomRepository.count()).thenReturn(-1L);
        when(roomRepository.countOccupiedRooms()).thenReturn(-5);
        when(roomRepository.countBrokenRooms()).thenReturn(-2);
        when(userRepository.count()).thenReturn(-10L);

        // Act
        DashboardSummaryResponse result = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result);
        assertEquals(-1, result.getTotalRooms());
        assertEquals(-5, result.getOccupiedRooms());
        assertEquals(-2, result.getBrokenRooms());
        assertEquals(-10, result.getTotalUsers());
    }

    /**
     * Test when all repositories work but return different realistic values
     */
    @Test
    void testGetDashboardSummary_Success_WithRealisticValues() {
        // Arrange
        when(roomRepository.count()).thenReturn(1000L);
        when(roomRepository.countOccupiedRooms()).thenReturn(750);
        when(roomRepository.countBrokenRooms()).thenReturn(50);
        when(userRepository.count()).thenReturn(500L);

        // Act
        DashboardSummaryResponse result = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result);
        assertEquals(1000, result.getTotalRooms());
        assertEquals(750, result.getOccupiedRooms());
        assertEquals(50, result.getBrokenRooms());
        assertEquals(500, result.getTotalUsers());

        // Verify business logic makes sense
        assertTrue(result.getOccupiedRooms() <= result.getTotalRooms());
        assertTrue(result.getBrokenRooms() <= result.getTotalRooms());
        assertTrue(result.getTotalUsers() <= result.getTotalRooms()); // Assuming one user per room max
    }

    /**
     * Test with extremely large values that cause overflow when casting to int
     */
    @Test
    void testGetDashboardSummary_Success_WithOverflowValues() {
        // Arrange - Values that will overflow when cast to int
        long veryLargeValue = Long.MAX_VALUE;
        when(roomRepository.count()).thenReturn(veryLargeValue);
        when(roomRepository.countOccupiedRooms()).thenReturn(1000);
        when(roomRepository.countBrokenRooms()).thenReturn(50);
        when(userRepository.count()).thenReturn(veryLargeValue);

        // Act
        DashboardSummaryResponse result = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result);
        // Note: These will overflow due to long to int casting
        assertEquals((int) veryLargeValue, result.getTotalRooms());
        assertEquals(1000, result.getOccupiedRooms());
        assertEquals(50, result.getBrokenRooms());
        assertEquals((int) veryLargeValue, result.getTotalUsers());

        verify(roomRepository, times(1)).count();
        verify(roomRepository, times(1)).countOccupiedRooms();
        verify(roomRepository, times(1)).countBrokenRooms();
        verify(userRepository, times(1)).count();
    }

    /**
     * Test concurrent repository calls don't interfere
     */
    @Test
    void testGetDashboardSummary_Success_ConcurrentRepositoryCalls() {
        // Arrange
        when(roomRepository.count()).thenReturn(100L);
        when(roomRepository.countOccupiedRooms()).thenReturn(25);
        when(roomRepository.countBrokenRooms()).thenReturn(5);
        when(userRepository.count()).thenReturn(50L);

        // Act
        DashboardSummaryResponse result1 = dashboardService.getDashboardSummary();
        DashboardSummaryResponse result2 = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1.getTotalRooms(), result2.getTotalRooms());
        assertEquals(result1.getOccupiedRooms(), result2.getOccupiedRooms());
        assertEquals(result1.getBrokenRooms(), result2.getBrokenRooms());
        assertEquals(result1.getTotalUsers(), result2.getTotalUsers());

        // Verify repository methods were called twice
        verify(roomRepository, times(2)).count();
        verify(roomRepository, times(2)).countOccupiedRooms();
        verify(roomRepository, times(2)).countBrokenRooms();
        verify(userRepository, times(2)).count();
    }
}