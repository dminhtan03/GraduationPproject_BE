package com.finalProject.BookingMeetingRoom.service.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest_getReservationTimeline {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationHistoryRepository reservationHistoryRepository;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    private Reservation testTimelineReservation;
    private List<ReservationHistory> testHistories;

    @BeforeEach
    void setUp() {
        // Setup timeline test data
        testTimelineReservation = new Reservation();
        testTimelineReservation.setId("R1");
        testTimelineReservation.setStatus(ReservationStatus.COMPLETED);
        testTimelineReservation.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 12, 0));
        testTimelineReservation.setCheckinTime(LocalDateTime.of(2024, 1, 15, 10, 30));

        // Create test histories
        ReservationHistory history1 = new ReservationHistory();
        history1.setReservation(testTimelineReservation);
        history1.setOldStatus(ReservationStatus.PENDING);
        history1.setAction(HistoryAction.CHECK_IN);
        history1.setPerformAt(LocalDateTime.of(2024, 1, 15, 9, 0));

        ReservationHistory history2 = new ReservationHistory();
        history2.setReservation(testTimelineReservation);
        history2.setOldStatus(ReservationStatus.RESERVED);
        history2.setAction(HistoryAction.EXTEND);
        history2.setPerformAt(LocalDateTime.of(2024, 1, 15, 11, 0));

        testHistories = List.of(history1, history2);
    }

    /**
     * Test case 1: Successful timeline retrieval with full data (reservation + checkin + histories)
     */
    @Test
    void testGetReservationTimeline_whenFullData_shouldReturnCompleteTimelineInDescendingOrder() {
        // Given
        String reservationId = "R1";

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(testTimelineReservation));
        when(reservationHistoryRepository.findByReservationId(reservationId)).thenReturn(testHistories);

        // When
        List<ReservationTimelineResponse> result = reservationService.getReservationTimeline(reservationId);

        // Then
        assertNotNull(result);
        assertEquals(4, result.size()); // Current status + checkin + 2 histories

        // Verify timeline is sorted by performAt in descending order (newest first)
        assertEquals(LocalDateTime.of(2024, 1, 15, 12, 0), result.get(0).getPerformAt()); // Current status (latest)
        assertEquals(LocalDateTime.of(2024, 1, 15, 11, 0), result.get(1).getPerformAt()); // History 2
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), result.get(2).getPerformAt()); // Checkin
        assertEquals(LocalDateTime.of(2024, 1, 15, 9, 0), result.get(3).getPerformAt());  // History 1

        // Verify current status entry
        ReservationTimelineResponse currentStatus = result.get(0);
        assertEquals("R1", currentStatus.getReservationId());
        assertEquals(ReservationStatus.COMPLETED, currentStatus.getOldStatus());
        assertNull(currentStatus.getAction());
        assertEquals(LocalDateTime.of(2024, 1, 15, 12, 0), currentStatus.getPerformAt());

        // Verify checkin entry
        ReservationTimelineResponse checkin = result.get(2);
        assertEquals("R1", checkin.getReservationId());
        assertNull(checkin.getOldStatus());
        assertEquals(HistoryAction.CHECK_IN, checkin.getAction());
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), checkin.getPerformAt());

        // Verify history entries
        ReservationTimelineResponse history2Response = result.get(1);
        assertEquals("R1", history2Response.getReservationId());
        assertEquals(ReservationStatus.RESERVED, history2Response.getOldStatus());
        assertEquals(HistoryAction.EXTEND, history2Response.getAction());

        ReservationTimelineResponse history1Response = result.get(3);
        assertEquals("R1", history1Response.getReservationId());
        assertEquals(ReservationStatus.PENDING, history1Response.getOldStatus());
        assertEquals(HistoryAction.CHECK_IN, history1Response.getAction());

        verify(reservationRepository).findById(reservationId);
        verify(reservationHistoryRepository).findByReservationId(reservationId);
    }

    /**
     * Test case 3: Reservation without history - should only include current status and checkin
     */
    @Test
    void testGetReservationTimeline_whenNoHistory_shouldOnlyIncludeCurrentAndCheckin() {
        // Given
        String reservationId = "R1";

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(testTimelineReservation));
        when(reservationHistoryRepository.findByReservationId(reservationId)).thenReturn(Collections.emptyList());

        // When
        List<ReservationTimelineResponse> result = reservationService.getReservationTimeline(reservationId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size()); // Current status + checkin only

        // Verify entries
        ReservationTimelineResponse currentStatus = result.get(0);
        assertEquals(ReservationStatus.COMPLETED, currentStatus.getOldStatus());
        assertEquals(LocalDateTime.of(2024, 1, 15, 12, 0), currentStatus.getPerformAt());

        ReservationTimelineResponse checkin = result.get(1);
        assertEquals(HistoryAction.CHECK_IN, checkin.getAction());
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), checkin.getPerformAt());

        verify(reservationRepository).findById(reservationId);
        verify(reservationHistoryRepository).findByReservationId(reservationId);
    }

    /**
     * Test case 4: Minimal data - only current status (no checkin, no history)
     */
    @Test
    void testGetReservationTimeline_whenMinimalData_shouldOnlyIncludeCurrentStatus() {
        // Given
        String reservationId = "R1";
        testTimelineReservation.setCheckinTime(null); // No checkin

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(testTimelineReservation));
        when(reservationHistoryRepository.findByReservationId(reservationId)).thenReturn(Collections.emptyList());

        // When
        List<ReservationTimelineResponse> result = reservationService.getReservationTimeline(reservationId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size()); // Only current status

        ReservationTimelineResponse currentStatus = result.get(0);
        assertEquals("R1", currentStatus.getReservationId());
        assertEquals(ReservationStatus.COMPLETED, currentStatus.getOldStatus());
        assertNull(currentStatus.getAction());
        assertEquals(LocalDateTime.of(2024, 1, 15, 12, 0), currentStatus.getPerformAt());

        verify(reservationRepository).findById(reservationId);
        verify(reservationHistoryRepository).findByReservationId(reservationId);
    }

    /**
     * Test case 5: Reservation not found - should throw CustomException
     */
    @Test
    void testGetReservationTimeline_whenReservationNotFound_shouldThrowCustomException() {
        // Given
        String reservationId = "NON_EXISTENT";

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.getReservationTimeline(reservationId);
        });

        assertEquals(ResponseCode.RESERVATION_NOT_FOUND, exception.getResponseCode());
        verify(reservationRepository).findById(reservationId);
        verify(reservationHistoryRepository, never()).findByReservationId(any());
    }

    /**
     * Test case 6: Repository throws exception - should catch and throw CustomException with INTERNAL_SERVER_ERROR
     */
    @Test
    void testGetReservationTimeline_whenRepositoryThrowsException_shouldThrowInternalServerError() {
        // Given
        String reservationId = "R1";

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(testTimelineReservation));
        when(reservationHistoryRepository.findByReservationId(reservationId))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.getReservationTimeline(reservationId);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(reservationRepository).findById(reservationId);
        verify(reservationHistoryRepository).findByReservationId(reservationId);
    }

    /**
     * Test case 7: Custom exception re-thrown - should not wrap in another CustomException
     */
    @Test
    void testGetReservationTimeline_whenCustomExceptionThrown_shouldRethrowSameException() {
        // Given
        String reservationId = "R1";
        CustomException originalException = new CustomException(ResponseCode.RESERVATION_NOT_FOUND);

        when(reservationRepository.findById(reservationId)).thenThrow(originalException);

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.getReservationTimeline(reservationId);
        });

        assertSame(originalException, exception);
        assertEquals(ResponseCode.RESERVATION_NOT_FOUND, exception.getResponseCode());
    }

    /**
     * Test case 8: Timeline sorting verification with mixed timestamps
     */
    @Test
    void testGetReservationTimeline_shouldSortTimelineByPerformAtDescending() {
        // Given
        String reservationId = "R1";

        // Create histories with mixed timestamps to test sorting
        ReservationHistory earlyHistory = new ReservationHistory();
        earlyHistory.setReservation(testTimelineReservation);
        earlyHistory.setOldStatus(ReservationStatus.PENDING);
        earlyHistory.setAction(HistoryAction.CHECK_IN);
        earlyHistory.setPerformAt(LocalDateTime.of(2024, 1, 15, 8, 0)); // Earliest

        ReservationHistory middleHistory = new ReservationHistory();
        middleHistory.setReservation(testTimelineReservation);
        middleHistory.setOldStatus(ReservationStatus.RESERVED);
        middleHistory.setAction(HistoryAction.EXTEND);
        middleHistory.setPerformAt(LocalDateTime.of(2024, 1, 15, 10, 0)); // Middle

        List<ReservationHistory> mixedHistories = List.of(middleHistory, earlyHistory); // Not in order

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(testTimelineReservation));
        when(reservationHistoryRepository.findByReservationId(reservationId)).thenReturn(mixedHistories);

        // When
        List<ReservationTimelineResponse> result = reservationService.getReservationTimeline(reservationId);

        // Then
        assertNotNull(result);
        assertEquals(4, result.size());

        // Verify correct descending order
        assertEquals(LocalDateTime.of(2024, 1, 15, 12, 0), result.get(0).getPerformAt()); // Current status (latest)
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), result.get(1).getPerformAt()); // Checkin
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 0), result.get(2).getPerformAt());  // Middle history
        assertEquals(LocalDateTime.of(2024, 1, 15, 8, 0), result.get(3).getPerformAt());   // Early history (oldest)
    }

    /**
     * Test case 9: Verify timeline response structure
     */
    @Test
    void testGetReservationTimeline_shouldCreateCorrectTimelineResponseStructure() {
        // Given
        String reservationId = "R1";

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(testTimelineReservation));
        when(reservationHistoryRepository.findByReservationId(reservationId)).thenReturn(testHistories);

        // When
        List<ReservationTimelineResponse> result = reservationService.getReservationTimeline(reservationId);

        // Then
        assertNotNull(result);
        assertEquals(4, result.size());

        // Verify current status entry structure
        ReservationTimelineResponse currentEntry = result.get(0);
        assertEquals("R1", currentEntry.getReservationId());
        assertEquals(ReservationStatus.COMPLETED, currentEntry.getOldStatus());
        assertNull(currentEntry.getAction());
        assertNotNull(currentEntry.getPerformAt());

        // Verify checkin entry structure
        ReservationTimelineResponse checkinEntry = result.stream()
                .filter(entry -> entry.getAction() == HistoryAction.CHECK_IN)
                .findFirst()
                .orElseThrow();
        assertEquals("R1", checkinEntry.getReservationId());
        assertNull(checkinEntry.getOldStatus());
        assertEquals(HistoryAction.CHECK_IN, checkinEntry.getAction());
        assertEquals(testTimelineReservation.getCheckinTime(), checkinEntry.getPerformAt());

        // Verify history entries structure
        List<ReservationTimelineResponse> historyEntries = result.stream()
                .filter(entry -> entry.getAction() != HistoryAction.CHECK_IN && entry.getAction() != null)
                .toList();
        assertEquals(1, historyEntries.size());

        for (ReservationTimelineResponse historyEntry : historyEntries) {
            assertEquals("R1", historyEntry.getReservationId());
            assertNotNull(historyEntry.getOldStatus());
            assertNotNull(historyEntry.getAction());
            assertNotNull(historyEntry.getPerformAt());
        }
    }

    /**
     * Test case 10: Test with different reservation statuses
     */
    @Test
    void testGetReservationTimeline_shouldHandleDifferentReservationStatuses() {
        // Given
        String reservationId = "R2";
        Reservation pendingReservation = new Reservation();
        pendingReservation.setId("R2");
        pendingReservation.setStatus(ReservationStatus.PENDING);
        pendingReservation.setUpdatedAt(LocalDateTime.of(2024, 1, 16, 9, 0));
        pendingReservation.setCheckinTime(null); // Pending reservations typically don't have checkin

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(pendingReservation));
        when(reservationHistoryRepository.findByReservationId(reservationId)).thenReturn(Collections.emptyList());

        // When
        List<ReservationTimelineResponse> result = reservationService.getReservationTimeline(reservationId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size()); // Only current status

        ReservationTimelineResponse currentStatus = result.get(0);
        assertEquals("R2", currentStatus.getReservationId());
        assertEquals(ReservationStatus.PENDING, currentStatus.getOldStatus());
        assertNull(currentStatus.getAction());
        assertEquals(LocalDateTime.of(2024, 1, 16, 9, 0), currentStatus.getPerformAt());
    }
}
