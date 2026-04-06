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
    private SeatRepository seatRepository;

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
        when(seatRepository.count()).thenReturn(100L);
        when(seatRepository.countOccupiedSeats()).thenReturn(25);
        when(seatRepository.countBrokenSeats()).thenReturn(5);
        when(userRepository.count()).thenReturn(50L);

        // Act
        DashboardSummaryResponse result = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result);
        assertEquals(100, result.getTotalSeats());
        assertEquals(25, result.getOccupiedSeats());
        assertEquals(5, result.getBrokenSeats());
        assertEquals(50, result.getTotalUsers());

        // Verify all repository methods were called
        verify(seatRepository, times(1)).count();
        verify(seatRepository, times(1)).countOccupiedSeats();
        verify(seatRepository, times(1)).countBrokenSeats();
        verify(userRepository, times(1)).count();
    }

    /**
     * Test successful dashboard summary retrieval with zero values
     */
    @Test
    void testGetDashboardSummary_Success_WithZeroValues() {
        // Arrange
        when(seatRepository.count()).thenReturn(0L);
        when(seatRepository.countOccupiedSeats()).thenReturn(0);
        when(seatRepository.countBrokenSeats()).thenReturn(0);
        when(userRepository.count()).thenReturn(0L);

        // Act
        DashboardSummaryResponse result = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalSeats());
        assertEquals(0, result.getOccupiedSeats());
        assertEquals(0, result.getBrokenSeats());
        assertEquals(0, result.getTotalUsers());
    }

    /**
     * Test successful dashboard summary retrieval with large numbers
     */
    @Test
    void testGetDashboardSummary_Success_WithLargeNumbers() {
        // Arrange
        when(seatRepository.count()).thenReturn(Long.MAX_VALUE);
        when(seatRepository.countOccupiedSeats()).thenReturn(Integer.MAX_VALUE);
        when(seatRepository.countBrokenSeats()).thenReturn(Integer.MAX_VALUE);
        when(userRepository.count()).thenReturn(Long.MAX_VALUE);

        // Act
        DashboardSummaryResponse result = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result);
        assertEquals((int) Long.MAX_VALUE, result.getTotalSeats()); // Note: This will overflow
        assertEquals(Integer.MAX_VALUE, result.getOccupiedSeats());
        assertEquals(Integer.MAX_VALUE, result.getBrokenSeats());
        assertEquals((int) Long.MAX_VALUE, result.getTotalUsers()); // Note: This will overflow
    }

    /**
     * Test when seatRepository.count() throws RuntimeException
     */
    @Test
    void testGetDashboardSummary_SeatRepositoryCountThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(seatRepository.count()).thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(seatRepository, times(1)).count();
        verify(seatRepository, never()).countOccupiedSeats();
        verify(seatRepository, never()).countBrokenSeats();
        verify(userRepository, never()).count();
    }

    /**
     * Test when seatRepository.countOccupiedSeats() throws RuntimeException
     */
    @Test
    void testGetDashboardSummary_CountOccupiedSeatsThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(seatRepository.count()).thenReturn(100L);
        when(seatRepository.countOccupiedSeats()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(seatRepository, times(1)).count();
        verify(seatRepository, times(1)).countOccupiedSeats();
        verify(seatRepository, never()).countBrokenSeats();
        verify(userRepository, never()).count();
    }

    /**
     * Test when seatRepository.countBrokenSeats() throws RuntimeException
     */
    @Test
    void testGetDashboardSummary_CountBrokenSeatsThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(seatRepository.count()).thenReturn(100L);
        when(seatRepository.countOccupiedSeats()).thenReturn(25);
        when(seatRepository.countBrokenSeats()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(seatRepository, times(1)).count();
        verify(seatRepository, times(1)).countOccupiedSeats();
        verify(seatRepository, times(1)).countBrokenSeats();
        verify(userRepository, never()).count();
    }

    /**
     * Test when userRepository.count() throws RuntimeException
     */
    @Test
    void testGetDashboardSummary_UserRepositoryCountThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(seatRepository.count()).thenReturn(100L);
        when(seatRepository.countOccupiedSeats()).thenReturn(25);
        when(seatRepository.countBrokenSeats()).thenReturn(5);
        when(userRepository.count()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(seatRepository, times(1)).count();
        verify(seatRepository, times(1)).countOccupiedSeats();
        verify(seatRepository, times(1)).countBrokenSeats();
        verify(userRepository, times(1)).count();
    }

    /**
     * Test when seatRepository throws SQLException
     */
    @Test
    void testGetDashboardSummary_SeatRepositoryThrowsSQLException_ThrowsInternalServerError() {
        // Arrange
        when(seatRepository.count()).thenThrow(new RuntimeException("SQL error", new SQLException("Connection timeout")));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(seatRepository, times(1)).count();
    }

    /**
     * Test when userRepository throws SQLException
     */
    @Test
    void testGetDashboardSummary_UserRepositoryThrowsSQLException_ThrowsInternalServerError() {
        // Arrange
        when(seatRepository.count()).thenReturn(100L);
        when(seatRepository.countOccupiedSeats()).thenReturn(25);
        when(seatRepository.countBrokenSeats()).thenReturn(5);
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
        when(seatRepository.count()).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            dashboardService.getDashboardSummary();
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception); // Should be the exact same exception

        verify(seatRepository, times(1)).count();
        verify(logger, never()).error(anyString(), any(Exception.class)); // Logger not called for CustomException
    }

    /**
     * Test re-throwing CustomException correctly
     */
    @Test
    void testGetDashboardSummary_CustomExceptionIsRethrownCorrectly() {
        // Arrange
        CustomException customException = new CustomException(ResponseCode.USER_NOT_FOUND);
        when(seatRepository.countOccupiedSeats()).thenThrow(customException);
        when(seatRepository.count()).thenReturn(100L);

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
        when(seatRepository.count()).thenReturn(-1L);
        when(seatRepository.countOccupiedSeats()).thenReturn(-5);
        when(seatRepository.countBrokenSeats()).thenReturn(-2);
        when(userRepository.count()).thenReturn(-10L);

        // Act
        DashboardSummaryResponse result = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result);
        assertEquals(-1, result.getTotalSeats());
        assertEquals(-5, result.getOccupiedSeats());
        assertEquals(-2, result.getBrokenSeats());
        assertEquals(-10, result.getTotalUsers());
    }

    /**
     * Test when all repositories work but return different realistic values
     */
    @Test
    void testGetDashboardSummary_Success_WithRealisticValues() {
        // Arrange
        when(seatRepository.count()).thenReturn(1000L);
        when(seatRepository.countOccupiedSeats()).thenReturn(750);
        when(seatRepository.countBrokenSeats()).thenReturn(50);
        when(userRepository.count()).thenReturn(500L);

        // Act
        DashboardSummaryResponse result = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result);
        assertEquals(1000, result.getTotalSeats());
        assertEquals(750, result.getOccupiedSeats());
        assertEquals(50, result.getBrokenSeats());
        assertEquals(500, result.getTotalUsers());

        // Verify business logic makes sense
        assertTrue(result.getOccupiedSeats() <= result.getTotalSeats());
        assertTrue(result.getBrokenSeats() <= result.getTotalSeats());
        assertTrue(result.getTotalUsers() <= result.getTotalSeats()); // Assuming one user per seat max
    }

    /**
     * Test with extremely large values that cause overflow when casting to int
     */
    @Test
    void testGetDashboardSummary_Success_WithOverflowValues() {
        // Arrange - Values that will overflow when cast to int
        long veryLargeValue = Long.MAX_VALUE;
        when(seatRepository.count()).thenReturn(veryLargeValue);
        when(seatRepository.countOccupiedSeats()).thenReturn(1000);
        when(seatRepository.countBrokenSeats()).thenReturn(50);
        when(userRepository.count()).thenReturn(veryLargeValue);

        // Act
        DashboardSummaryResponse result = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result);
        // Note: These will overflow due to long to int casting
        assertEquals((int) veryLargeValue, result.getTotalSeats());
        assertEquals(1000, result.getOccupiedSeats());
        assertEquals(50, result.getBrokenSeats());
        assertEquals((int) veryLargeValue, result.getTotalUsers());

        verify(seatRepository, times(1)).count();
        verify(seatRepository, times(1)).countOccupiedSeats();
        verify(seatRepository, times(1)).countBrokenSeats();
        verify(userRepository, times(1)).count();
    }

    /**
     * Test concurrent repository calls don't interfere
     */
    @Test
    void testGetDashboardSummary_Success_ConcurrentRepositoryCalls() {
        // Arrange
        when(seatRepository.count()).thenReturn(100L);
        when(seatRepository.countOccupiedSeats()).thenReturn(25);
        when(seatRepository.countBrokenSeats()).thenReturn(5);
        when(userRepository.count()).thenReturn(50L);

        // Act
        DashboardSummaryResponse result1 = dashboardService.getDashboardSummary();
        DashboardSummaryResponse result2 = dashboardService.getDashboardSummary();

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(result1.getTotalSeats(), result2.getTotalSeats());
        assertEquals(result1.getOccupiedSeats(), result2.getOccupiedSeats());
        assertEquals(result1.getBrokenSeats(), result2.getBrokenSeats());
        assertEquals(result1.getTotalUsers(), result2.getTotalUsers());

        // Verify repository methods were called twice
        verify(seatRepository, times(2)).count();
        verify(seatRepository, times(2)).countOccupiedSeats();
        verify(seatRepository, times(2)).countBrokenSeats();
        verify(userRepository, times(2)).count();
    }
}