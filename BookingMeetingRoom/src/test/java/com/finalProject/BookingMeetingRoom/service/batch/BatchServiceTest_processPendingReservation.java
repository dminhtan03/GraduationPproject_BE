package com.finalProject.BookingMeetingRoom.service.batch;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.service.NotificationService;
import com.finalProject.BookingMeetingRoom.service.RealTimeService;
import com.finalProject.BookingMeetingRoom.service.impl.BatchServiceImpl;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest_processPendingReservation {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BatchServiceImpl batchService;

    @Mock
    private RealTimeService realTimeService;

    private User user;
    private Room room;
    private Reservation reservation;

    @BeforeEach
    void setup() {
        user = new User();
        user.setId("U1");

        room = new Room();
        room.setId("S1");

        reservation = new Reservation();
        reservation.setId("R1");
        reservation.setUser(user);
        reservation.setRoom(room);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setCreateAt(LocalDateTime.now().minusMinutes(10));
        reservation.setStartTime(LocalDateTime.now().plusMinutes(30));
        reservation.setEndTime(LocalDateTime.now().plusMinutes(90));
    }

    /**
     * Test case for processing pending reservations when there are no pending reservations.
     * This should simply return without any action.
     */
    @Test
    void testProcessPendingReservations_whenNoPendingReservations_shouldReturn() {
        when(reservationRepository.findByStatus(ReservationStatus.PENDING))
                .thenReturn(Collections.emptyList());

        batchService.processPendingReservations();

        verify(reservationRepository, never()).saveAll(any());
        verify(notificationService, never()).noticeSuccessfulReservation(any());
    }

    /**
     * Test case for processing pending reservations when reservation is confirmed.
     * This should change the status to RESERVED and notify the user.
     */
    @Test
    void testProcessPendingReservations_whenReservationConfirmed_shouldBeReserved() {
        when(reservationRepository.findByStatus(ReservationStatus.PENDING))
                .thenReturn(List.of(reservation));

        when(reservationRepository.countReservationsTodayByUserIds(
                anySet(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"U1", 0}));

        when(reservationRepository.findByRoomIdsAndStatusIn(
                anySet(), anyList()))
                .thenReturn(Collections.emptyList());

        batchService.processPendingReservations();

        ArgumentCaptor<List<Reservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).saveAll(captor.capture());

        Reservation updated = captor.getValue().get(0);
        assertEquals(ReservationStatus.RESERVED, updated.getStatus());
        assertNotNull(updated.getUpdatedAt());

        verify(notificationService).noticeSuccessfulReservation(List.of(updated));
        verify(notificationService, never()).noticeFailedReservation(any());
    }

    /**
     * Test case for processing pending reservations when reservation overlaps with existing ones.
     * This should change the status to FAILED and notify the user.
     */
    @Test
    void testProcessPendingReservations_whenOverlap_shouldBeFailed() {
        when(reservationRepository.findByStatus(ReservationStatus.PENDING))
                .thenReturn(List.of(reservation));

        when(reservationRepository.countReservationsTodayByUserIds(anySet(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"U1", 0}));

        Reservation overlapping = new Reservation();
        overlapping.setId("R2");
        overlapping.setRoom(room);
        overlapping.setStartTime(reservation.getStartTime().minusMinutes(15));
        overlapping.setEndTime(reservation.getEndTime().plusMinutes(15));

        when(reservationRepository.findByRoomIdsAndStatusIn(anySet(), anyList()))
                .thenReturn(List.of(overlapping));

        batchService.processPendingReservations();

        ArgumentCaptor<List<Reservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).saveAll(captor.capture());

        Reservation updated = captor.getValue().get(0);
        assertEquals(ReservationStatus.FAILED, updated.getStatus());

        verify(notificationService).noticeFailedReservation(List.of(updated));
        verify(notificationService, never()).noticeSuccessfulReservation(any());
    }

    /**
     * Test case for processing pending reservations when two users compete for the same room.
     * This should select the user with the lower count of reservations today.
     */
    @Test
    void testProcessPendingReservations_whenTwoUsersCompeteForSameRoom_shouldSelectLowerCountUser() {
        User user2 = new User();
        user2.setId("U2");

        Reservation reservation2 = new Reservation();
        reservation2.setId("R2");
        reservation2.setUser(user2);
        reservation2.setRoom(room);
        reservation2.setStatus(ReservationStatus.PENDING);
        reservation2.setCreateAt(LocalDateTime.now().minusMinutes(10));
        reservation2.setStartTime(LocalDateTime.now().plusMinutes(30));
        reservation2.setEndTime(LocalDateTime.now().plusMinutes(90));

        when(reservationRepository.findByStatus(ReservationStatus.PENDING))
                .thenReturn(List.of(reservation, reservation2));

        when(reservationRepository.countReservationsTodayByUserIds(
                anySet(), any(), any()))
                .thenReturn(List.of(
                        new Object[]{"U1", 2},
                        new Object[]{"U2", 0}
                ));

        when(reservationRepository.findByRoomIdsAndStatusIn(anySet(), anyList()))
                .thenReturn(Collections.emptyList());

        batchService.processPendingReservations();

        ArgumentCaptor<List<Reservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).saveAll(captor.capture());
        List<Reservation> updated = captor.getValue();

        Reservation updated1 = updated.stream().filter(r -> r.getId().equals("R1")).findFirst().orElseThrow();
        Reservation updated2 = updated.stream().filter(r -> r.getId().equals("R2")).findFirst().orElseThrow();

        assertEquals(ReservationStatus.FAILED, updated1.getStatus());
        assertEquals(ReservationStatus.RESERVED, updated2.getStatus());

        verify(notificationService).noticeSuccessfulReservation(List.of(updated2));
        verify(notificationService).noticeFailedReservation(List.of(updated1));
    }

    /**
     * Test case for processing pending reservations when two users compete for the same room.
     * This should select the user with the earlier created reservation.
     */
    @Test
    void testProcessPendingReservations_whenTwoUsersCompeteForSameRoom_shouldSelectEarlierCreatedReservation() {
        User user2 = new User();
        user2.setId("U2");

        Reservation reservation2 = new Reservation();
        reservation2.setId("R2");
        reservation2.setUser(user2);
        reservation2.setRoom(room);
        reservation2.setStatus(ReservationStatus.PENDING);
        reservation2.setCreateAt(LocalDateTime.now().minusMinutes(5));
        reservation2.setStartTime(LocalDateTime.now().plusMinutes(30));
        reservation2.setEndTime(LocalDateTime.now().plusMinutes(90));

        when(reservationRepository.findByStatus(ReservationStatus.PENDING))
                .thenReturn(List.of(reservation, reservation2));

        when(reservationRepository.countReservationsTodayByUserIds(
                anySet(), any(), any()))
                .thenReturn(List.of(
                        new Object[]{"U1", 2},
                        new Object[]{"U2", 2}
                ));

        when(reservationRepository.findByRoomIdsAndStatusIn(anySet(), anyList()))
                .thenReturn(Collections.emptyList());

        batchService.processPendingReservations();

        ArgumentCaptor<List<Reservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).saveAll(captor.capture());
        List<Reservation> updated = captor.getValue();

        Reservation updated1 = updated.stream().filter(r -> r.getId().equals("R1")).findFirst().orElseThrow();
        Reservation updated2 = updated.stream().filter(r -> r.getId().equals("R2")).findFirst().orElseThrow();

        assertEquals(ReservationStatus.RESERVED, updated1.getStatus());
        assertEquals(ReservationStatus.FAILED, updated2.getStatus());

        verify(notificationService).noticeSuccessfulReservation(List.of(updated1));
        verify(notificationService).noticeFailedReservation(List.of(updated2));
    }

    /**
     * Test case for adjacent (touching) time slots - should not conflict
     * Scenario: R1 ends at 10:00, R2 starts at 10:00 (no overlap)
     */
    @Test
    void testProcessPendingReservations_whenAdjacentTimeSlots_shouldBothSucceed() {
        User user2 = new User();
        user2.setId("U2");

        Reservation reservation2 = new Reservation();
        reservation2.setId("R2");
        reservation2.setUser(user2);
        reservation2.setRoom(room);
        reservation2.setStatus(ReservationStatus.PENDING);
        reservation2.setCreateAt(LocalDateTime.now().minusMinutes(10));
        reservation2.setStartTime(reservation.getEndTime()); // Starts when R1 ends
        reservation2.setEndTime(reservation.getEndTime().plusMinutes(60));

        when(reservationRepository.findByStatus(ReservationStatus.PENDING))
                .thenReturn(List.of(reservation, reservation2));

        when(reservationRepository.countReservationsTodayByUserIds(anySet(), any(), any()))
                .thenReturn(List.of(
                        new Object[]{"U1", 0},
                        new Object[]{"U2", 0}
                ));

        when(reservationRepository.findByRoomIdsAndStatusIn(anySet(), anyList()))
                .thenReturn(Collections.emptyList());

        batchService.processPendingReservations();

        ArgumentCaptor<List<Reservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).saveAll(captor.capture());
        List<Reservation> updated = captor.getValue();

        // Both should succeed since they don't overlap
        assertEquals(2, updated.size());
        assertTrue(updated.stream().allMatch(r -> r.getStatus() == ReservationStatus.RESERVED));
    }

    /**
     * Test case for same user having multiple overlapping reservations
     * Scenario: User tries to book overlapping time slots - only first should succeed
     */
    @Test
    void testProcessPendingReservations_whenSameUserHasOverlappingReservations_shouldSelectFirst() {
        Reservation reservation2 = new Reservation();
        reservation2.setId("R2");
        reservation2.setUser(user);
        reservation2.setRoom(room);
        reservation2.setStatus(ReservationStatus.PENDING);
        reservation2.setCreateAt(LocalDateTime.now().minusMinutes(5));
        reservation2.setStartTime(reservation.getStartTime().plusMinutes(15));
        reservation2.setEndTime(reservation.getEndTime().plusMinutes(15));

        when(reservationRepository.findByStatus(ReservationStatus.PENDING))
                .thenReturn(List.of(reservation, reservation2));

        when(reservationRepository.countReservationsTodayByUserIds(anySet(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"U1", 1}));

        when(reservationRepository.findByRoomIdsAndStatusIn(anySet(), anyList()))
                .thenReturn(Collections.emptyList());

        batchService.processPendingReservations();

        ArgumentCaptor<List<Reservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).saveAll(captor.capture());
        List<Reservation> updated = captor.getValue();

        Reservation updated1 = updated.stream().filter(r -> r.getId().equals("R1")).findFirst().orElseThrow();
        Reservation updated2 = updated.stream().filter(r -> r.getId().equals("R2")).findFirst().orElseThrow();

        assertEquals(ReservationStatus.RESERVED, updated1.getStatus());
        assertEquals(ReservationStatus.FAILED, updated2.getStatus());
    }

    /**
     * Test case for multiple rooms processing
     * Scenario: Different rooms should be processed independently
     */
    @Test
    void testProcessPendingReservations_whenMultipleRooms_shouldProcessIndependently() {
        Room room2 = new Room();
        room2.setId("S2");

        User user2 = new User();
        user2.setId("U2");

        Reservation reservation2 = new Reservation();
        reservation2.setId("R2");
        reservation2.setUser(user2);
        reservation2.setRoom(room2);
        reservation2.setStatus(ReservationStatus.PENDING);
        reservation2.setCreateAt(LocalDateTime.now().minusMinutes(10));
        reservation2.setStartTime(reservation.getStartTime());
        reservation2.setEndTime(reservation.getEndTime());

        when(reservationRepository.findByStatus(ReservationStatus.PENDING))
                .thenReturn(List.of(reservation, reservation2));

        when(reservationRepository.countReservationsTodayByUserIds(anySet(), any(), any()))
                .thenReturn(List.of(
                        new Object[]{"U1", 0},
                        new Object[]{"U2", 0}
                ));

        when(reservationRepository.findByRoomIdsAndStatusIn(anySet(), anyList()))
                .thenReturn(Collections.emptyList());

        batchService.processPendingReservations();

        ArgumentCaptor<List<Reservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).saveAll(captor.capture());
        List<Reservation> updated = captor.getValue();

        assertEquals(2, updated.size());
        assertTrue(updated.stream().allMatch(r -> r.getStatus() == ReservationStatus.RESERVED));
    }

    /**
     * Test case for multiple users with different priority levels
     * Scenario: 3 users with reservation counts 0, 1, 2 competing for same slot
     */
    @Test
    void testProcessPendingReservations_whenMultipleUsersWithDifferentPriorities_shouldSelectByPriority() {
        User user2 = new User();
        user2.setId("U2");
        User user3 = new User();
        user3.setId("U3");

        Reservation reservation2 = new Reservation();
        reservation2.setId("R2");
        reservation2.setUser(user2);
        reservation2.setRoom(room);
        reservation2.setStatus(ReservationStatus.PENDING);
        reservation2.setCreateAt(LocalDateTime.now().minusMinutes(8));
        reservation2.setStartTime(reservation.getStartTime());
        reservation2.setEndTime(reservation.getEndTime());

        Reservation reservation3 = new Reservation();
        reservation3.setId("R3");
        reservation3.setUser(user3);
        reservation3.setRoom(room);
        reservation3.setStatus(ReservationStatus.PENDING);
        reservation3.setCreateAt(LocalDateTime.now().minusMinutes(12)); // Earliest
        reservation3.setStartTime(reservation.getStartTime());
        reservation3.setEndTime(reservation.getEndTime());

        when(reservationRepository.findByStatus(ReservationStatus.PENDING))
                .thenReturn(List.of(reservation, reservation2, reservation3));

        when(reservationRepository.countReservationsTodayByUserIds(anySet(), any(), any()))
                .thenReturn(List.of(
                        new Object[]{"U1", 2}, // Lowest priority
                        new Object[]{"U2", 1}, // Medium priority
                        new Object[]{"U3", 0}  // Highest priority
                ));

        when(reservationRepository.findByRoomIdsAndStatusIn(anySet(), anyList()))
                .thenReturn(Collections.emptyList());

        batchService.processPendingReservations();

        ArgumentCaptor<List<Reservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).saveAll(captor.capture());
        List<Reservation> updated = captor.getValue();

        Reservation updated1 = updated.stream().filter(r -> r.getId().equals("R1")).findFirst().orElseThrow();
        Reservation updated2 = updated.stream().filter(r -> r.getId().equals("R2")).findFirst().orElseThrow();
        Reservation updated3 = updated.stream().filter(r -> r.getId().equals("R3")).findFirst().orElseThrow();

        assertEquals(ReservationStatus.FAILED, updated1.getStatus());   // Count = 2 (lowest priority)
        assertEquals(ReservationStatus.FAILED, updated2.getStatus());   // Count = 1 (medium priority)
        assertEquals(ReservationStatus.RESERVED, updated3.getStatus()); // Count = 0 (highest priority)
    }

    /**
     * Test case for large batch processing
     * Scenario: Process many reservations efficiently
     */
    @Test
    void testProcessPendingReservations_whenLargeBatch_shouldProcessEfficiently() {
        List<Reservation> reservations = new ArrayList<>();
        List<Object[]> counts = new ArrayList<>();

        // Create 50 reservations for 10 different rooms
        for (int i = 1; i <= 50; i++) {
            User u = new User();
            u.setId("U" + i);

            Room s = new Room();
            s.setId("S" + ((i - 1) % 10 + 1));

            Reservation r = new Reservation();
            r.setId("R" + i);
            r.setUser(u);
            r.setRoom(s);
            r.setStatus(ReservationStatus.PENDING);
            r.setCreateAt(LocalDateTime.now().minusMinutes(i));
            r.setStartTime(LocalDateTime.now().plusMinutes(30));
            r.setEndTime(LocalDateTime.now().plusMinutes(90));

            reservations.add(r);
            counts.add(new Object[]{"U" + i, i % 3}); // Varying priorities
        }

        when(reservationRepository.findByStatus(ReservationStatus.PENDING))
                .thenReturn(reservations);

        when(reservationRepository.countReservationsTodayByUserIds(anySet(), any(), any()))
                .thenReturn(counts);

        when(reservationRepository.findByRoomIdsAndStatusIn(anySet(), anyList()))
                .thenReturn(Collections.emptyList());

        batchService.processPendingReservations();

        ArgumentCaptor<List<Reservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).saveAll(captor.capture());
        List<Reservation> updated = captor.getValue();

        assertEquals(50, updated.size());

        // Each room should have only 1 reserved (highest priority user)
        Map<String, List<Reservation>> byRoom = updated.stream()
                .collect(Collectors.groupingBy(r -> r.getRoom().getId()));

        assertEquals(10, byRoom.size()); // 10 different rooms

        byRoom.values().forEach(roomReservations -> {
            long reservedCount = roomReservations.stream()
                    .mapToLong(r -> r.getStatus() == ReservationStatus.RESERVED ? 1 : 0)
                    .sum();
            assertEquals(1, reservedCount); // Only 1 should be reserved per room
        });
    }

    /**
     * Test case for large batch processing - 1000 reservations, 200 rooms, 100 users
     * Scenario: Stress test with realistic large volume
     */
    @Test
    void testProcessPendingReservations_whenStressTest1000Reservations_shouldProcessEfficiently() {
        List<Reservation> reservations = new ArrayList<>();
        List<Object[]> counts = new ArrayList<>();

        // Create 1000 reservations for 200 different rooms and 100 users
        for (int i = 1; i <= 1000; i++) {
            User u = new User();
            u.setId("U" + ((i - 1) % 100 + 1)); // 100 users: U1-U100

            Room s = new Room();
            s.setId("S" + ((i - 1) % 200 + 1)); // 200 rooms: S1-S200

            Reservation r = new Reservation();
            r.setId("R" + i);
            r.setUser(u);
            r.setRoom(s);
            r.setStatus(ReservationStatus.PENDING);
            r.setCreateAt(LocalDateTime.now().minusMinutes(i % 120)); // Vary creation time
            r.setStartTime(LocalDateTime.now().plusMinutes(30));
            r.setEndTime(LocalDateTime.now().plusMinutes(90));

            reservations.add(r);
        }

        // Create user counts (each user has 0-9 reservations today for varying priorities)
        for (int userId = 1; userId <= 100; userId++) {
            counts.add(new Object[]{"U" + userId, userId % 10});
        }

        when(reservationRepository.findByStatus(ReservationStatus.PENDING))
                .thenReturn(reservations);

        when(reservationRepository.countReservationsTodayByUserIds(anySet(), any(), any()))
                .thenReturn(counts);

        when(reservationRepository.findByRoomIdsAndStatusIn(anySet(), anyList()))
                .thenReturn(Collections.emptyList());

        // Measure execution time
        long startTime = System.currentTimeMillis();

        batchService.processPendingReservations();

        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        System.out.println("Execution time for 1000 reservations: " + executionTime + "ms");

        // Verify results
        ArgumentCaptor<List<Reservation>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).saveAll(captor.capture());
        List<Reservation> updated = captor.getValue();

        assertEquals(1000, updated.size());

        // Group by room and verify only 1 reservation per room is confirmed
        Map<String, List<Reservation>> byRoom = updated.stream()
                .collect(Collectors.groupingBy(r -> r.getRoom().getId()));

        assertEquals(200, byRoom.size());

        byRoom.forEach((roomId, roomReservations) -> {
            Optional<Reservation> maybeReserved = roomReservations.stream()
                    .filter(r -> r.getStatus() == ReservationStatus.RESERVED)
                    .findFirst();

            if (maybeReserved.isEmpty()) {
                // Không có reservation nào được xác nhận cho room này → bỏ qua
                return;
            }

            Reservation reserved = maybeReserved.get();
            String winnerUserId = reserved.getUser().getId();
            int winnerCount = Integer.parseInt(winnerUserId.substring(1)) % 10;

            roomReservations.stream()
                    .filter(r -> r.getStatus() == ReservationStatus.FAILED)
                    .forEach(failed -> {
                        String failedUserId = failed.getUser().getId();
                        int failedCount = Integer.parseInt(failedUserId.substring(1)) % 10;

                        assertTrue(failedCount >= winnerCount,
                                String.format("User %s (count=%d) should not fail if user %s (count=%d) succeeded",
                                        failedUserId, failedCount, winnerUserId, winnerCount));
                    });
        });

        System.out.println("✅ Successfully processed 1000 reservations across 200 rooms for 100 users");
    }

    @Test
    public void testProcessPendingReservations_ThrowsException_WhenReservationDataIsNull() {
        Reservation reservationWithNullUser = mock(Reservation.class);
        when(reservationWithNullUser.getUser()).thenReturn(null);

        List<Reservation> pendingReservations = Arrays.asList(reservationWithNullUser);

        when(reservationRepository.findByStatus(ReservationStatus.PENDING))
                .thenReturn(pendingReservations);

        assertDoesNotThrow(() -> {
            batchService.processPendingReservations();
        });

        verify(reservationRepository, times(1)).findByStatus(ReservationStatus.PENDING);
        verify(reservationRepository, never()).saveAll(any());
    }

}
