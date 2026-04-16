package com.finalProject.BookingMeetingRoom.service.reservationhis;

import com.finalProject.BookingMeetingRoom.common.enums.HistoryAction;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.ReservationHistory;
import com.finalProject.BookingMeetingRoom.repository.ReservationHistoryRepository;
import com.finalProject.BookingMeetingRoom.service.impl.ReservationHistoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservationHistoryServiceTest_saveHistory {
    @Mock
    private ReservationHistoryRepository reservationHistoryRepository;

    @InjectMocks
    private ReservationHistoryServiceImpl reservationService;

    private Reservation testReservation;

    @BeforeEach
    void setUp() {
        testReservation = new Reservation();
        testReservation.setId("R1");
        testReservation.setStatus(ReservationStatus.COMPLETED);
        testReservation.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 12, 0));
    }

    /**
     * Test case 1: Successful history save with all fields provided
     */
    @Test
    void testSaveHistory_whenAllFieldsProvided_shouldSaveHistorySuccessfully() {
        // Given
        String userId = "U1";
        ReservationStatus oldStatus = ReservationStatus.PENDING;
        HistoryAction action = HistoryAction.CHECK_IN;

        when(reservationHistoryRepository.save(any(ReservationHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveHistory(testReservation, userId, oldStatus, action, testReservation.getUpdatedAt());

        // Then
        ArgumentCaptor<ReservationHistory> historyCaptor = ArgumentCaptor.forClass(ReservationHistory.class);
        verify(reservationHistoryRepository).save(historyCaptor.capture());

        ReservationHistory savedHistory = historyCaptor.getValue();
        assertNotNull(savedHistory.getId());
        assertEquals(testReservation, savedHistory.getReservation());
        assertEquals(oldStatus, savedHistory.getOldStatus());
        assertEquals(userId, savedHistory.getPerformBy());
        assertEquals(action, savedHistory.getAction());
        assertEquals(testReservation.getUpdatedAt(), savedHistory.getPerformAt());
    }

    /**
     * Test case 2: Save history with null action - should not set action field
     */
    @Test
    void testSaveHistory_whenActionIsNull_shouldNotSetAction() {
        // Given
        String userId = "U1";
        ReservationStatus oldStatus = ReservationStatus.PENDING;
        HistoryAction action = null;

        when(reservationHistoryRepository.save(any(ReservationHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveHistory(testReservation, userId, oldStatus, action, testReservation.getUpdatedAt());

        // Then
        ArgumentCaptor<ReservationHistory> historyCaptor = ArgumentCaptor.forClass(ReservationHistory.class);
        verify(reservationHistoryRepository).save(historyCaptor.capture());

        ReservationHistory savedHistory = historyCaptor.getValue();
        assertNotNull(savedHistory.getId());
        assertEquals(testReservation, savedHistory.getReservation());
        assertEquals(oldStatus, savedHistory.getOldStatus());
        assertEquals(userId, savedHistory.getPerformBy());
        assertNull(savedHistory.getAction()); // Action should not be set
        assertEquals(testReservation.getUpdatedAt(), savedHistory.getPerformAt());
    }

    /**
     * Test case 3: Save history when reservation.updatedAt is null - should use current time
     */
    @Test
    void testSaveHistory_whenReservationUpdatedAtIsNull_shouldUseCurrentTime() {
        // Given
        testReservation.setUpdatedAt(null);
        String userId = "U1";
        ReservationStatus oldStatus = ReservationStatus.PENDING;
        HistoryAction action = HistoryAction.CHECK_IN;

        LocalDateTime beforeExecution = LocalDateTime.now();

        when(reservationHistoryRepository.save(any(ReservationHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveHistory(testReservation, userId, oldStatus, action, testReservation.getUpdatedAt());

        // Then
        LocalDateTime afterExecution = LocalDateTime.now();

        ArgumentCaptor<ReservationHistory> historyCaptor = ArgumentCaptor.forClass(ReservationHistory.class);
        verify(reservationHistoryRepository).save(historyCaptor.capture());

        ReservationHistory savedHistory = historyCaptor.getValue();
        assertNotNull(savedHistory.getId());
        assertEquals(testReservation, savedHistory.getReservation());
        assertEquals(oldStatus, savedHistory.getOldStatus());
        assertEquals(userId, savedHistory.getPerformBy());
        assertEquals(action, savedHistory.getAction());

        // Verify performAt is current time (within reasonable range)
        LocalDateTime performAt = savedHistory.getPerformAt();
        assertTrue(performAt.isAfter(beforeExecution.minusSeconds(1)) &&
                        performAt.isBefore(afterExecution.plusSeconds(1)),
                "PerformAt should be current time when reservation.updatedAt is null");
    }

    /**
     * Test case 4: Save history with null old status - should handle gracefully
     */
    @Test
    void testSaveHistory_whenOldStatusIsNull_shouldHandleGracefully() {
        // Given
        String userId = "U1";
        ReservationStatus oldStatus = null;
        HistoryAction action = HistoryAction.CHECK_IN;

        when(reservationHistoryRepository.save(any(ReservationHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveHistory(testReservation, userId, oldStatus, action, testReservation.getUpdatedAt());

        // Then
        ArgumentCaptor<ReservationHistory> historyCaptor = ArgumentCaptor.forClass(ReservationHistory.class);
        verify(reservationHistoryRepository).save(historyCaptor.capture());

        ReservationHistory savedHistory = historyCaptor.getValue();
        assertNotNull(savedHistory.getId());
        assertEquals(testReservation, savedHistory.getReservation());
        assertNull(savedHistory.getOldStatus());
        assertEquals(userId, savedHistory.getPerformBy());
        assertEquals(action, savedHistory.getAction());
        assertEquals(testReservation.getUpdatedAt(), savedHistory.getPerformAt());
    }

    /**
     * Test case 5: Save history with null userId - should handle gracefully
     */
    @Test
    void testSaveHistory_whenUserIdIsNull_shouldHandleGracefully() {
        // Given
        String userId = null;
        ReservationStatus oldStatus = ReservationStatus.PENDING;
        HistoryAction action = HistoryAction.CHECK_IN;

        when(reservationHistoryRepository.save(any(ReservationHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveHistory(testReservation, userId, oldStatus, action, testReservation.getUpdatedAt());

        // Then
        ArgumentCaptor<ReservationHistory> historyCaptor = ArgumentCaptor.forClass(ReservationHistory.class);
        verify(reservationHistoryRepository).save(historyCaptor.capture());

        ReservationHistory savedHistory = historyCaptor.getValue();
        assertNotNull(savedHistory.getId());
        assertEquals(testReservation, savedHistory.getReservation());
        assertEquals(oldStatus, savedHistory.getOldStatus());
        assertNull(savedHistory.getPerformBy());
        assertEquals(action, savedHistory.getAction());
        assertEquals(testReservation.getUpdatedAt(), savedHistory.getPerformAt());
    }

    /**
     * Test case 6: Repository throws exception - should throw CustomException with INTERNAL_SERVER_ERROR
     */
    @Test
    void testSaveHistory_whenRepositoryThrowsException_shouldThrowInternalServerError() {
        // Given
        String userId = "U1";
        ReservationStatus oldStatus = ReservationStatus.PENDING;
        HistoryAction action = HistoryAction.CHECK_IN;

        when(reservationHistoryRepository.save(any(ReservationHistory.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.saveHistory(testReservation, userId, oldStatus, action, testReservation.getUpdatedAt());
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(reservationHistoryRepository).save(any(ReservationHistory.class));
    }

    /**
     * Test case 7: Verify UUID generation for history ID
     */
    @Test
    void testSaveHistory_shouldGenerateUniqueUUIDForHistoryId() {
        // Given
        String userId = "U1";
        ReservationStatus oldStatus = ReservationStatus.PENDING;
        HistoryAction action = HistoryAction.CHECK_IN;

        when(reservationHistoryRepository.save(any(ReservationHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When - call method multiple times
        reservationService.saveHistory(testReservation, userId, oldStatus, action, testReservation.getUpdatedAt());
        reservationService.saveHistory(testReservation, userId, oldStatus, action, testReservation.getUpdatedAt());

        // Then
        ArgumentCaptor<ReservationHistory> historyCaptor = ArgumentCaptor.forClass(ReservationHistory.class);
        verify(reservationHistoryRepository, times(2)).save(historyCaptor.capture());

        List<ReservationHistory> savedHistories = historyCaptor.getAllValues();
        assertEquals(2, savedHistories.size());

        String id1 = savedHistories.get(0).getId();
        String id2 = savedHistories.get(1).getId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2); // IDs should be different

        // Verify both are valid UUIDs
        assertDoesNotThrow(() -> UUID.fromString(id1));
        assertDoesNotThrow(() -> UUID.fromString(id2));
    }

    /**
     * Test case 8: Test with different history actions
     */
    @Test
    void testSaveHistory_shouldHandleDifferentHistoryActions() {
        // Given
        String userId = "U1";
        ReservationStatus oldStatus = ReservationStatus.PENDING;
        HistoryAction[] actions = {
                HistoryAction.CHECK_IN,
                HistoryAction.EXTEND,
        };

        when(reservationHistoryRepository.save(any(ReservationHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When & Then
        for (HistoryAction action : actions) {
            reservationService.saveHistory(testReservation, userId, oldStatus, action, testReservation.getUpdatedAt());

            ArgumentCaptor<ReservationHistory> historyCaptor = ArgumentCaptor.forClass(ReservationHistory.class);
            verify(reservationHistoryRepository, atLeastOnce()).save(historyCaptor.capture());

            ReservationHistory savedHistory = historyCaptor.getValue();
            assertEquals(action, savedHistory.getAction());
        }

        verify(reservationHistoryRepository, times(actions.length)).save(any(ReservationHistory.class));
    }

    /**
     * Test case 9: Test with different reservation statuses
     */
    @Test
    void testSaveHistory_shouldHandleDifferentReservationStatuses() {
        // Given
        String userId = "U1";
        HistoryAction action = HistoryAction.CHECK_IN;
        ReservationStatus[] statuses = {
                ReservationStatus.PENDING,
                ReservationStatus.RESERVED,
                ReservationStatus.IN_USE,
                ReservationStatus.COMPLETED,
                ReservationStatus.CANCELLED,
                ReservationStatus.NO_SHOW,
                ReservationStatus.FAILED,
                ReservationStatus.FORCE_CANCELLED
        };

        when(reservationHistoryRepository.save(any(ReservationHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When & Then
        for (ReservationStatus oldStatus : statuses) {
            reservationService.saveHistory(testReservation, userId, oldStatus, action, testReservation.getUpdatedAt());

            ArgumentCaptor<ReservationHistory> historyCaptor = ArgumentCaptor.forClass(ReservationHistory.class);
            verify(reservationHistoryRepository, atLeastOnce()).save(historyCaptor.capture());

            ReservationHistory savedHistory = historyCaptor.getValue();
            assertEquals(oldStatus, savedHistory.getOldStatus());
        }

        verify(reservationHistoryRepository, times(statuses.length)).save(any(ReservationHistory.class));
    }

    /**
     * Test case 10: Test complete workflow with all edge cases in single test
     */
    @Test
    void testSaveHistory_completeWorkflow_shouldHandleAllScenarios() {
        // Given
        String userId = "U1";
        ReservationStatus oldStatus = ReservationStatus.PENDING;
        HistoryAction action = HistoryAction.CHECK_IN;

        when(reservationHistoryRepository.save(any(ReservationHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveHistory(testReservation, userId, oldStatus, action, testReservation.getUpdatedAt());

        // Then - Verify all fields are set correctly
        ArgumentCaptor<ReservationHistory> historyCaptor = ArgumentCaptor.forClass(ReservationHistory.class);
        verify(reservationHistoryRepository).save(historyCaptor.capture());

        ReservationHistory savedHistory = historyCaptor.getValue();

        // Verify ID generation
        assertNotNull(savedHistory.getId());
        assertDoesNotThrow(() -> UUID.fromString(savedHistory.getId()));

        // Verify all required fields
        assertEquals(testReservation, savedHistory.getReservation());
        assertEquals(oldStatus, savedHistory.getOldStatus());
        assertEquals(userId, savedHistory.getPerformBy());
        assertEquals(action, savedHistory.getAction());

        // Verify time handling
        assertEquals(testReservation.getUpdatedAt(), savedHistory.getPerformAt());

        // Verify repository interaction
        verify(reservationHistoryRepository, times(1)).save(any(ReservationHistory.class));
    }

    /**
     * Test case 11: Test method behavior with null reservation - should throw CustomException
     */
    @Test
    void testSaveHistory_whenReservationIsNull_shouldThrowCustomException() {
        // Given
        Reservation nullReservation = null;
        String userId = "U1";
        ReservationStatus oldStatus = ReservationStatus.PENDING;
        HistoryAction action = HistoryAction.CHECK_IN;

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.saveHistory(nullReservation, userId, oldStatus, action, testReservation.getUpdatedAt());
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        // Repository should not be called due to exception
        verify(reservationHistoryRepository, never()).save(any(ReservationHistory.class));
    }

    /**
     * Test case 12: Test with NullPointerException from other sources
     */
    @Test
    void testSaveHistory_whenUnexpectedNullPointerException_shouldThrowCustomException() {
        Reservation mockReservation = mock(Reservation.class);
        when(mockReservation.getUpdatedAt()).thenThrow(new NullPointerException("Mocked NPE"));

        String userId = "U1";
        ReservationStatus oldStatus = ReservationStatus.PENDING;
        HistoryAction action = HistoryAction.CHECK_IN;

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.saveHistory(mockReservation, userId, oldStatus, action, testReservation.getUpdatedAt());
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
    }
}
