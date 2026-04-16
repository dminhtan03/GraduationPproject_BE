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
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservationHistoryServiceTest_saveAllHistories {

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
     * Test case 1: Successful save all histories with multiple reservations
     */
    @Test
    void testSaveAllHistories_whenMultipleReservations_shouldSaveAllSuccessfully() {
        // Given
        String userId = "U1";
        HistoryAction action = HistoryAction.CHECK_IN;

        Reservation reservation1 = new Reservation();
        reservation1.setId("R1");
        reservation1.setStatus(ReservationStatus.PENDING);
        reservation1.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 10, 0));

        Reservation reservation2 = new Reservation();
        reservation2.setId("R2");
        reservation2.setStatus(ReservationStatus.RESERVED);
        reservation2.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 11, 0));

        List<Reservation> reservations = List.of(reservation1, reservation2);

        when(reservationHistoryRepository.saveAll(any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveAllHistories(reservations, userId, action);

        // Then
        ArgumentCaptor<List<ReservationHistory>> historiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservationHistoryRepository).saveAll(historiesCaptor.capture());

        List<ReservationHistory> savedHistories = historiesCaptor.getValue();
        assertEquals(2, savedHistories.size());

        // Verify first history
        ReservationHistory history1 = savedHistories.get(0);
        assertNotNull(history1.getId());
        assertEquals(reservation1, history1.getReservation());
        assertEquals(ReservationStatus.PENDING, history1.getOldStatus());
        assertEquals(userId, history1.getPerformBy());
        assertEquals(action, history1.getAction());
        assertEquals(reservation1.getUpdatedAt(), history1.getPerformAt());

        // Verify second history
        ReservationHistory history2 = savedHistories.get(1);
        assertNotNull(history2.getId());
        assertEquals(reservation2, history2.getReservation());
        assertEquals(ReservationStatus.RESERVED, history2.getOldStatus());
        assertEquals(userId, history2.getPerformBy());
        assertEquals(action, history2.getAction());
        assertEquals(reservation2.getUpdatedAt(), history2.getPerformAt());

        // Verify unique IDs
        assertNotEquals(history1.getId(), history2.getId());
    }

    /**
     * Test case 2: Save all histories with null action - should not set action
     */
    @Test
    void testSaveAllHistories_whenActionIsNull_shouldNotSetAction() {
        // Given
        String userId = "U1";
        HistoryAction action = null;

        Reservation reservation = new Reservation();
        reservation.setId("R1");
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 10, 0));

        List<Reservation> reservations = List.of(reservation);

        when(reservationHistoryRepository.saveAll(any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveAllHistories(reservations, userId, action);

        // Then
        ArgumentCaptor<List<ReservationHistory>> historiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservationHistoryRepository).saveAll(historiesCaptor.capture());

        List<ReservationHistory> savedHistories = historiesCaptor.getValue();
        assertEquals(1, savedHistories.size());

        ReservationHistory history = savedHistories.get(0);
        assertNotNull(history.getId());
        assertEquals(reservation, history.getReservation());
        assertEquals(ReservationStatus.PENDING, history.getOldStatus());
        assertEquals(userId, history.getPerformBy());
        assertNull(history.getAction()); // Action should not be set
        assertEquals(reservation.getUpdatedAt(), history.getPerformAt());
    }

    /**
     * Test case 3: Save all histories with null reservations list - should return without action
     */
    @Test
    void testSaveAllHistories_whenReservationsIsNull_shouldReturnEarly() {
        // Given
        List<Reservation> nullReservations = null;
        String userId = "U1";
        HistoryAction action = HistoryAction.CHECK_IN;

        // When
        reservationService.saveAllHistories(nullReservations, userId, action);

        // Then
        verify(reservationHistoryRepository, never()).saveAll(any());
    }

    /**
     * Test case 4: Save all histories with empty reservations list - should return without action
     */
    @Test
    void testSaveAllHistories_whenReservationsIsEmpty_shouldReturnEarly() {
        // Given
        List<Reservation> emptyReservations = Collections.emptyList();
        String userId = "U1";
        HistoryAction action = HistoryAction.CHECK_IN;

        // When
        reservationService.saveAllHistories(emptyReservations, userId, action);

        Reservation reservation = new Reservation();
        reservation.setId("R1");
        reservation.setStatus(ReservationStatus.IN_USE);
        reservation.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 14, 30));

        List<Reservation> reservations = List.of(reservation);

        when(reservationHistoryRepository.saveAll(any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveAllHistories(reservations, userId, action);

        // Then
        ArgumentCaptor<List<ReservationHistory>> historiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservationHistoryRepository).saveAll(historiesCaptor.capture());

        List<ReservationHistory> savedHistories = historiesCaptor.getValue();
        assertEquals(1, savedHistories.size());

        ReservationHistory history = savedHistories.get(0);
        assertNotNull(history.getId());
        assertEquals(reservation, history.getReservation());
        assertEquals(ReservationStatus.IN_USE, history.getOldStatus());
        assertEquals(userId, history.getPerformBy());
        assertEquals(action, history.getAction());
        assertEquals(reservation.getUpdatedAt(), history.getPerformAt());
    }

    /**
     * Test case 6: Repository throws exception during saveAll - should throw CustomException
     */
    @Test
    void testSaveAllHistories_whenRepositoryThrowsException_shouldThrowInternalServerError() {
        // Given
        String userId = "U1";
        HistoryAction action = HistoryAction.CHECK_IN;

        Reservation reservation = new Reservation();
        reservation.setId("R1");
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 10, 0));

        List<Reservation> reservations = List.of(reservation);

        when(reservationHistoryRepository.saveAll(any(List.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            reservationService.saveAllHistories(reservations, userId, action);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(reservationHistoryRepository).saveAll(any(List.class));
    }

    /**
     * Test case 7: Save all histories with null userId - should handle gracefully
     */
    @Test
    void testSaveAllHistories_whenUserIdIsNull_shouldHandleGracefully() {
        // Given
        String userId = null;
        HistoryAction action = HistoryAction.CHECK_IN;

        Reservation reservation = new Reservation();
        reservation.setId("R1");
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 10, 0));

        List<Reservation> reservations = List.of(reservation);

        when(reservationHistoryRepository.saveAll(any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveAllHistories(reservations, userId, action);

        // Then
        ArgumentCaptor<List<ReservationHistory>> historiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservationHistoryRepository).saveAll(historiesCaptor.capture());

        List<ReservationHistory> savedHistories = historiesCaptor.getValue();
        assertEquals(1, savedHistories.size());

        ReservationHistory history = savedHistories.get(0);
        assertNull(history.getPerformBy());
        assertEquals(action, history.getAction());
    }

    /**
     * Test case 8: Save all histories with reservations having null status - should use null as oldStatus
     */
    @Test
    void testSaveAllHistories_whenReservationStatusIsNull_shouldUseNullAsOldStatus() {
        // Given
        String userId = "U1";
        HistoryAction action = HistoryAction.CHECK_IN;

        Reservation reservation = new Reservation();
        reservation.setId("R1");
        reservation.setStatus(null); // Null status
        reservation.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 10, 0));

        List<Reservation> reservations = List.of(reservation);

        when(reservationHistoryRepository.saveAll(any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveAllHistories(reservations, userId, action);

        // Then
        ArgumentCaptor<List<ReservationHistory>> historiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservationHistoryRepository).saveAll(historiesCaptor.capture());

        List<ReservationHistory> savedHistories = historiesCaptor.getValue();
        assertEquals(1, savedHistories.size());

        ReservationHistory history = savedHistories.get(0);
        assertNull(history.getOldStatus());
        assertEquals(reservation, history.getReservation());
    }

    /**
     * Test case 9: Save all histories with large number of reservations - performance test
     */
    @Test
    void testSaveAllHistories_whenLargeNumberOfReservations_shouldHandleEfficiently() {
        // Given
        String userId = "U1";
        HistoryAction action = HistoryAction.CHECK_IN;

        List<Reservation> reservations = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            Reservation reservation = new Reservation();
            reservation.setId("R" + i);
            reservation.setStatus(ReservationStatus.PENDING);
            reservation.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 10, i % 60));
            reservations.add(reservation);
        }

        when(reservationHistoryRepository.saveAll(any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveAllHistories(reservations, userId, action);

        // Then
        ArgumentCaptor<List<ReservationHistory>> historiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservationHistoryRepository).saveAll(historiesCaptor.capture());

        List<ReservationHistory> savedHistories = historiesCaptor.getValue();
        assertEquals(100, savedHistories.size());

        // Verify all histories have unique IDs
        Set<String> uniqueIds = savedHistories.stream()
                .map(ReservationHistory::getId)
                .collect(Collectors.toSet());
        assertEquals(100, uniqueIds.size());

        // Verify all histories have correct action
        assertTrue(savedHistories.stream().allMatch(h -> h.getAction() == action));
        assertTrue(savedHistories.stream().allMatch(h -> h.getPerformBy().equals(userId)));
    }

    /**
     * Test case 10: Save all histories with mixed reservation states
     */
    @Test
    void testSaveAllHistories_whenMixedReservationStates_shouldHandleAllCorrectly() {
        // Given
        String userId = "U1";
        HistoryAction action = HistoryAction.CHECK_IN;

        Reservation reservation1 = new Reservation();
        reservation1.setId("R1");
        reservation1.setStatus(ReservationStatus.PENDING);
        reservation1.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 10, 0));

        Reservation reservation2 = new Reservation();
        reservation2.setId("R2");
        reservation2.setStatus(ReservationStatus.RESERVED);
        reservation2.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 11, 0));

        Reservation reservation3 = new Reservation();
        reservation3.setId("R3");
        reservation3.setStatus(ReservationStatus.IN_USE);
        reservation3.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 12, 0));

        List<Reservation> reservations = List.of(reservation1, reservation2, reservation3);

        when(reservationHistoryRepository.saveAll(any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveAllHistories(reservations, userId, action);

        // Then
        ArgumentCaptor<List<ReservationHistory>> historiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservationHistoryRepository).saveAll(historiesCaptor.capture());

        List<ReservationHistory> savedHistories = historiesCaptor.getValue();
        assertEquals(3, savedHistories.size());

        // Verify each history maintains the original reservation's status as oldStatus
        ReservationHistory history1 = savedHistories.stream()
                .filter(h -> h.getReservation().getId().equals("R1"))
                .findFirst().orElseThrow();
        assertEquals(ReservationStatus.PENDING, history1.getOldStatus());

        ReservationHistory history2 = savedHistories.stream()
                .filter(h -> h.getReservation().getId().equals("R2"))
                .findFirst().orElseThrow();
        assertEquals(ReservationStatus.RESERVED, history2.getOldStatus());

        ReservationHistory history3 = savedHistories.stream()
                .filter(h -> h.getReservation().getId().equals("R3"))
                .findFirst().orElseThrow();
        assertEquals(ReservationStatus.IN_USE, history3.getOldStatus());

        // Verify all have same action and user
        assertTrue(savedHistories.stream().allMatch(h -> h.getAction() == action));
        assertTrue(savedHistories.stream().allMatch(h -> h.getPerformBy().equals(userId)));
    }

    /**
     * Test case 11: Save all histories stream processing - verify stream operations work correctly
     */
    @Test
    void testSaveAllHistories_streamProcessing_shouldWorkCorrectly() {
        // Given
        String userId = "U1";
        HistoryAction action = HistoryAction.CHECK_IN;

        Reservation reservation = new Reservation();
        reservation.setId("R1");
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setUpdatedAt(LocalDateTime.of(2024, 1, 15, 10, 0));

        List<Reservation> reservations = List.of(reservation);

        when(reservationHistoryRepository.saveAll(any(List.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        reservationService.saveAllHistories(reservations, userId, action);

        // Then
        ArgumentCaptor<List<ReservationHistory>> historiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservationHistoryRepository).saveAll(historiesCaptor.capture());

        List<ReservationHistory> savedHistories = historiesCaptor.getValue();
        assertEquals(1, savedHistories.size());

        ReservationHistory history = savedHistories.get(0);

        // Verify stream mapping worked correctly
        assertNotNull(history.getId());
        assertDoesNotThrow(() -> UUID.fromString(history.getId()));
        assertEquals(reservation, history.getReservation());
        assertEquals(reservation.getStatus(), history.getOldStatus());
        assertEquals(userId, history.getPerformBy());
        assertEquals(reservation.getUpdatedAt(), history.getPerformAt());
        assertEquals(action, history.getAction());

        // Verify repository was called exactly once
        verify(reservationHistoryRepository, times(1)).saveAll(any(List.class));
    }
}
