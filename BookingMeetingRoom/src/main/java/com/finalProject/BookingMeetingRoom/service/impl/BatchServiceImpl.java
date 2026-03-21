package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.NotificationService;
import com.finalProject.BookingMeetingRoom.service.RealTimeService;
import com.finalProject.BookingMeetingRoom.service.ReservationHistoryService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BatchServiceImpl {

    private final Logger log = LogManager.getLogger(BatchServiceImpl.class);
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final NotificationService notificationService;
    private final RealTimeService realTimeService;
    private final ReservationHistoryService reservationHistoryService;

    /**
     * Scheduled job to update user statuses and process reservations.
     * This method runs every minute to check for reservations that have not been checked in or have ended.
     * It processes reservations with status NO_SHOW and COMPLETED, and also processes pending reservations.
     */
    @Scheduled(cron = "0 */1 * * * *")
    public void updateUserStatuses() {
        try {

            processReservation(ReservationStatus.NO_SHOW);

            processReservation(ReservationStatus.COMPLETED);

            processPendingReservations();

            notificationService.remindCheckIn();

        } catch (Exception e) {
            log.error("Error in scheduled job: {}", e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Process reservations based on the given status.
     * If status is NO_SHOW, it updates reservations that have started but not checked in.
     * If status is COMPLETED, it updates reservations that have ended.
     * All related rooms are set to AVAILABLE.
     *
     * @param status The status to process (NO_SHOW or COMPLETED).
     */

    // start update processReservation to be Transactional and use currentTime
    @Transactional
    public void processReservation(ReservationStatus status) {
        log.info("Processing reservation status: {}", status);

        LocalDateTime now = LocalDateTime.now();
        var reservations = status == ReservationStatus.NO_SHOW
                ? reservationRepository.findReservationsOverStartTime(now)
                : reservationRepository.findReservationsOverEndTime(now);
    // end update processReservation to be Transactional and use currentTime

        if (!reservations.isEmpty()) {
            if (status == ReservationStatus.NO_SHOW) {
                notificationService.noticeNoCheckInReservation(reservations);
            } else {
                notificationService.noticeOverTimeReservation(reservations);
            }
        }

        for (var r : reservations) {
            LocalDateTime previousUpdatedAt = r.getUpdatedAt();
            ReservationStatus oldStatus = r.getStatus();
            r.setStatus(status);
            r.setUpdatedAt(LocalDateTime.now());
            var room = r.getRoom();
            if (room == null) {
                log.warn("Room not found for reservation id: {}", r.getId());
                continue;
            }

            room.setStatus(RoomStatus.AVAILABLE);
            roomRepository.save(room);


            realTimeService.sendRoomStatus(room);
            realTimeService.sendReservationStatus(r);

            if (oldStatus == ReservationStatus.RESERVED) {
                reservationHistoryService.saveHistory(r, r.getUser().getId(), oldStatus, null, previousUpdatedAt);
            }

            reservationRepository.save(r);
            realTimeService.deleteReservation(r);
        }
    }

    /**
     * Process all reservations with status PENDING.
     * Reservations are grouped by room, sorted by user's reservation count today and creation time,
     * then checked for time overlaps with both previously RESERVED and current batch's RESERVED reservations.
     * Updates the reservation status to RESERVED if no overlap, otherwise to FAILED.
     * All updated reservations are saved in batch.
     */
    public void processPendingReservations() {
        try {
            var pendingReservations = reservationRepository.findByStatus(ReservationStatus.PENDING);
            if (pendingReservations.isEmpty()) {
                log.info("No pending reservations to process");
                return;
            }

            var now = LocalDateTime.now();
            Map<String, Integer> userReservationCounts = calculateTodayReservationCounts(pendingReservations);

            Set<String> userIds = pendingReservations.stream()
                    .map(r -> r.getUser().getId())
                    .collect(Collectors.toSet());

            Set<String> roomIds = pendingReservations.stream()
                    .map(r -> r.getRoom().getId())
                    .collect(Collectors.toSet());

            Map<String, List<Reservation>> existingReservationsByRoom = getExistingReservationsByRoom(roomIds);
            Map<String, List<Reservation>> existingReservationsByUser = getExistingReservationsByUser(userIds);

            Map<String, List<Reservation>> reservationsByRoom = pendingReservations.stream()
                    .collect(Collectors.groupingBy(reservation -> reservation.getRoom().getId()));

            List<Reservation> toUpdate = new ArrayList<>();
            List<Reservation> confirmed = new ArrayList<>();
            List<Reservation> failed = new ArrayList<>();

            Map<String, List<Reservation>> confirmedByUser = new HashMap<>();
            Map<String, List<Reservation>> reservedInBatchByRoom = new HashMap<>();

            for (Map.Entry<String, List<Reservation>> entry : reservationsByRoom.entrySet()) {
                var reservations = entry.getValue();

                reservations.sort(
                        Comparator.comparing((Reservation r) -> userReservationCounts.getOrDefault(r.getUser().getId(), 0))
                                .thenComparing(Reservation::getCreateAt)
                );

                for (var current : reservations) {
                    var userId = current.getUser().getId();
                    var currentRoomId = current.getRoom().getId();

                    List<Reservation> userConfirmedList = confirmedByUser.computeIfAbsent(userId, k -> new ArrayList<>());
                    List<Reservation> existingForRoom = existingReservationsByRoom.getOrDefault(currentRoomId, List.of());
                    List<Reservation> existingForUser = existingReservationsByUser.getOrDefault(userId, List.of());
                    List<Reservation> reservedInBatchForRoom = reservedInBatchByRoom.computeIfAbsent(currentRoomId, k -> new ArrayList<>());

                    boolean hasConflict =
                            hasAnyOverlap(current, existingForRoom) ||
                                    hasAnyOverlap(current, reservedInBatchForRoom) ||
                                    hasAnyOverlap(current, userConfirmedList) ||
                                    hasAnyOverlap(current, existingForUser);

                    if (!hasConflict) {
                        current.setStatus(ReservationStatus.RESERVED);
                        insertSortedByStartTime(confirmed, current);
                        insertSortedByStartTime(userConfirmedList, current);
                        insertSortedByStartTime(reservedInBatchForRoom, current);

                        log.info("Reservation {} confirmed for room {}", current.getId(), currentRoomId);
                    } else {
                        current.setStatus(ReservationStatus.FAILED);
                        insertSortedByStartTime(failed, current);
                        log.info("Reservation {} failed for room {} due to conflict", current.getId(), currentRoomId);
                    }

                    current.setUpdatedAt(now);
                    toUpdate.add(current);
                }
            }

            reservationRepository.saveAll(toUpdate);
            reservationRepository.flush();

            log.info("Processed {} pending reservations: {} confirmed, {} failed",
                    toUpdate.size(), confirmed.size(), failed.size());

            for (var current : toUpdate) {
                realTimeService.sendReservationStatus(current);
                if (current.getStatus() == ReservationStatus.RESERVED)
                    realTimeService.addReservation(current);
            }

            if (!confirmed.isEmpty()) notificationService.noticeSuccessfulReservation(confirmed);
            if (!failed.isEmpty()) notificationService.noticeFailedReservation(failed);

        } catch (Exception e) {
            log.error("Error processing pending reservations: {}", e.getMessage(), e);
        }
    }

    /**
     * Insert a new reservation into the list while maintaining sorted order by start time.
     * This method uses binary search to find the correct index for insertion.
     *
     * @param list           The list of reservations to insert into.
     * @param newReservation The new reservation to insert.
     */
    private void insertSortedByStartTime(List<Reservation> list, Reservation newReservation) {
        Comparator<Reservation> comparator = Comparator.comparing(Reservation::getStartTime);
        int index = Collections.binarySearch(list, newReservation, comparator);
        if (index < 0) {
            index = -index - 1;
        }
        list.add(index, newReservation);
    }

    /**
     * Count the number of reservations made by each user today.
     *
     * @param pendingReservations A list of pending reservations to check.
     * @return A map where the key is the user ID and the value is the count of reservations made today.
     */
    private Map<String, Integer> calculateTodayReservationCounts(List<Reservation> pendingReservations) {
        Set<String> userIds = pendingReservations.stream()
                .map(r -> r.getUser().getId())
                .collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            return new HashMap<>();
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<Object[]> counts = reservationRepository.countReservationsTodayByUserIds(
                userIds, startOfDay, endOfDay);

        return counts.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).intValue()
                ));
    }

    /**
     * Get existing reservations by user IDs.
     * This method retrieves all reservations for the given user IDs that are either RESERVED or IN_USE.
     *
     * @param userIds Set of user IDs to check for existing reservations.
     * @return A map where the key is the user ID and the value is a list of reservations for that user.
     */
    private Map<String, List<Reservation>> getExistingReservationsByUser(Set<String> userIds) {
        var existingReservations = reservationRepository.findByUserIdsAndStatusIn(
                userIds,
                List.of(ReservationStatus.RESERVED, ReservationStatus.IN_USE)
        );

        return existingReservations.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getUser().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    list.sort(Comparator.comparing(Reservation::getStartTime));
                                    return list;
                                }
                        )
                ));
    }

    /**
     * Get existing reservations by room IDs.
     * This method retrieves all reservations for the given room IDs that are either RESERVED or IN_USE.
     *
     * @param roomIds Set of room IDs to check for existing reservations.
     * @return A map where the key is the room ID and the value is a list of reservations for that room.
     */
    private Map<String, List<Reservation>> getExistingReservationsByRoom(Set<String> roomIds) {
        var existingReservations = reservationRepository.findByRoomIdsAndStatusIn(
                roomIds,
                List.of(ReservationStatus.RESERVED, ReservationStatus.IN_USE)
        );

        return existingReservations.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getRoom().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    list.sort(Comparator.comparing(Reservation::getStartTime));
                                    return list;
                                }
                        )
                ));
    }

    /**
     * Check if the current reservation overlaps with any other reservations.
     * This method checks if the current reservation's time overlaps with any of the existing reservations.
     *
     * @param current The current reservation to check.
     * @param others  List of existing reservations to check against.
     * @return true if there is an overlap, false otherwise.
     */
    private boolean hasAnyOverlap(Reservation current, List<Reservation> others) {
        var start = current.getStartTime();
        var end = current.getEndTime();
        for (var r : others) {
            if (r.getStartTime().isAfter(end)) break;
            if (isOverlapping(start, end, r.getStartTime(), r.getEndTime())) return true;
        }
        return false;
    }

    /**
     * Check if two time intervals overlap.
     * This method checks if the first interval (start1, end1) overlaps with the second interval (start2, end2).
     *
     * @param start1 Start time of the first interval.
     * @param end1   End time of the first interval.
     * @param start2 Start time of the second interval.
     * @param end2   End time of the second interval.
     * @return true if the intervals overlap, false otherwise.
     */
    private boolean isOverlapping(LocalDateTime start1, LocalDateTime end1,
                                  LocalDateTime start2, LocalDateTime end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }
}