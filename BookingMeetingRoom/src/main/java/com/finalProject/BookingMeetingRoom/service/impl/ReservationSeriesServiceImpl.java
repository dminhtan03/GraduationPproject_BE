package com.finalProject.BookingMeetingRoom.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationSeriesStatus;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.ReservationSeries;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.request.ReservationSeriesCreateRequest;
import com.finalProject.BookingMeetingRoom.model.response.ReservationSeriesPreviewItem;
import com.finalProject.BookingMeetingRoom.model.response.ReservationSeriesResponse;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationSeriesRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;
import com.finalProject.BookingMeetingRoom.service.RealTimeService;
import com.finalProject.BookingMeetingRoom.service.ReservationHistoryService;
import com.finalProject.BookingMeetingRoom.service.ReservationSeriesService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// start+ chức năng đặt phòng lặp lại (weekly recurring + sync job)
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationSeriesServiceImpl implements ReservationSeriesService {

    private static final int DEFAULT_ROLLING_WINDOW_WEEKS = 4;

    private final ReservationSeriesRepository reservationSeriesRepository;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final AcademicScheduleService academicScheduleService;
    private final ReservationHistoryService reservationHistoryService;
    private final RealTimeService realTimeService;

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()));
    }

    private void assertOwnerOrAdmin(ReservationSeries series, Authentication authentication) {
        if (series == null) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Series not found");
        }
        if (authentication == null || authentication.getName() == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }
        if (isAdmin(authentication)) return;
        if (!series.getUser().getUsername().equalsIgnoreCase(authentication.getName())) {
            throw new CustomException(ResponseCode.PERMISSION_DENIED);
        }
    }

    private ReservationSeriesResponse toResponse(ReservationSeries series) {
        var user = series.getUser();
        return ReservationSeriesResponse.builder()
                .id(series.getId())
                .roomId(series.getRoom().getId())
                .roomCode(series.getRoom().getLocationCode())
                .startTimeOfDay(series.getStartTimeOfDay())
                .endTimeOfDay(series.getEndTimeOfDay())
                .daysOfWeek(series.getDaysOfWeek())
                .purpose(series.getPurpose())
                .note(series.getNote())
                .fromDate(series.getFromDate())
                .untilDate(series.getUntilDate())
                .rollingWindowWeeks(series.getRollingWindowWeeks())
                .status(series.getStatus())
                .lastSyncUntil(series.getLastSyncUntil())
                .createdAt(series.getCreatedAt())
                // start+ chức năng admin quản lý recurring series
                .userEmail(user != null ? user.getUsername() : null)
                // end+ chức năng admin quản lý recurring series
                .build();
    }

    private Set<DayOfWeek> parseDaysOfWeek(String daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isBlank()) return Set.of();
        return java.util.Arrays.stream(daysOfWeek.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }

    private LocalDate resolveMaxSyncUntil(ReservationSeries series) {
        int rollingWeeks = series.getRollingWindowWeeks() == null ? DEFAULT_ROLLING_WINDOW_WEEKS : series.getRollingWindowWeeks();
        LocalDate maxUntil = LocalDate.now().plusWeeks(Math.max(1, rollingWeeks));
        if (series.getUntilDate() != null && series.getUntilDate().isBefore(maxUntil)) {
            maxUntil = series.getUntilDate();
        }
        return maxUntil;
    }

    private LocalDateTime buildStartDateTime(LocalDate date, LocalTime startTimeOfDay) {
        return date.atTime(startTimeOfDay);
    }

    private LocalDateTime buildEndDateTime(LocalDate date, LocalTime startTimeOfDay, LocalTime endTimeOfDay) {
        if (endTimeOfDay.isAfter(startTimeOfDay)) {
            return date.atTime(endTimeOfDay);
        }
        return date.plusDays(1).atTime(endTimeOfDay);
    }

    @Override
    @Transactional
    public ReservationSeriesResponse createSeries(ReservationSeriesCreateRequest request, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new CustomException(ResponseCode.ROOM_NOT_FOUND));

        if (request.getDaysOfWeek() == null || request.getDaysOfWeek().isEmpty()) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "daysOfWeek is required");
        }

        String daysOfWeek = request.getDaysOfWeek().stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .collect(Collectors.joining(","));
        if (daysOfWeek.isBlank()) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "daysOfWeek is invalid");
        }

        if (request.getFromDate() == null) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "fromDate is required");
        }
        if (request.getUntilDate() != null && request.getFromDate().isAfter(request.getUntilDate())) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "fromDate must be <= untilDate");
        }

        ReservationSeries series = new ReservationSeries();
        series.setId(UUID.randomUUID().toString());
        series.setRoom(room);
        series.setUser(user);
        series.setStartTimeOfDay(request.getStartTimeOfDay());
        series.setEndTimeOfDay(request.getEndTimeOfDay());
        series.setDaysOfWeek(daysOfWeek);
        series.setPurpose(request.getPurpose().trim());
        series.setNote(request.getNote());
        series.setFromDate(request.getFromDate());
        series.setUntilDate(request.getUntilDate());
        series.setRollingWindowWeeks(request.getRollingWindowWeeks() == null ? DEFAULT_ROLLING_WINDOW_WEEKS : request.getRollingWindowWeeks());
        series.setStatus(ReservationSeriesStatus.ACTIVE);
        series.setLastSyncUntil(null);
        series.setCreatedAt(LocalDateTime.now());
        series.setUpdatedAt(LocalDateTime.now());

        reservationSeriesRepository.save(series);
        syncSeries(series);

        return toResponse(series);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationSeriesResponse> getMySeries(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

        return reservationSeriesRepository.findAll().stream()
                .filter(s -> s.getUser() != null && s.getUser().getId().equals(user.getId()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void cancelSeries(String seriesId, Authentication authentication) {
        ReservationSeries series = reservationSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Series not found"));
        assertOwnerOrAdmin(series, authentication);

        series.setStatus(ReservationSeriesStatus.CANCELLED);
        series.setUpdatedAt(LocalDateTime.now());
        reservationSeriesRepository.save(series);

        List<Reservation> futureReservations = reservationRepository.findBySeriesIdAndStartTimeAfter(seriesId, LocalDateTime.now());
        for (Reservation reservation : futureReservations) {
            if (reservation.getStatus() == ReservationStatus.RESERVED || reservation.getStatus() == ReservationStatus.PENDING) {
                reservation.setStatus(ReservationStatus.CANCELLED);
                reservation.setReason("Cancelled due to recurring series cancellation.");
                reservation.setCancelBy(authentication != null ? authentication.getName() : null);
                reservation.setUpdatedAt(LocalDateTime.now());
                reservationRepository.save(reservation);
                realTimeService.deleteReservation(reservation);
            }
        }
    }

    @Override
    @Transactional
    public void syncSeriesNow(String seriesId, Authentication authentication) {
        ReservationSeries series = reservationSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Series not found"));
        assertOwnerOrAdmin(series, authentication);
        syncSeries(series);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void syncAllActiveSeriesJob() {
        List<ReservationSeries> activeSeries = reservationSeriesRepository.findByStatus(ReservationSeriesStatus.ACTIVE);
        for (ReservationSeries series : activeSeries) {
            try {
                syncSeries(series);
            } catch (Exception e) {
                log.error("Failed syncing reservation series {}", series.getId(), e);
            }
        }
    }

    private void syncSeries(ReservationSeries series) {
        if (series.getStatus() != ReservationSeriesStatus.ACTIVE) return;

        Set<DayOfWeek> targetDays = parseDaysOfWeek(series.getDaysOfWeek());
        if (targetDays.isEmpty()) return;

        LocalDate maxUntil = resolveMaxSyncUntil(series);
        LocalDate cursor = series.getLastSyncUntil() != null ? series.getLastSyncUntil().plusDays(1) : series.getFromDate();
        if (cursor.isBefore(series.getFromDate())) {
            cursor = series.getFromDate();
        }
        if (cursor.isAfter(maxUntil)) return;

        while (!cursor.isAfter(maxUntil)) {
            if (targetDays.contains(cursor.getDayOfWeek())) {
                if (!reservationRepository.existsBySeriesIdAndSeriesDate(series.getId(), cursor)) {
                    tryCreateReservationForOccurrence(series, cursor);
                }
            }
            cursor = cursor.plusDays(1);
        }

        series.setLastSyncUntil(maxUntil);
        series.setUpdatedAt(LocalDateTime.now());
        reservationSeriesRepository.save(series);
    }

    // start+ chức năng admin quản lý recurring series
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ReservationSeriesResponse> getAllSeriesForAdmin() {
        return reservationSeriesRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }
    // end+ chức năng admin quản lý recurring series

    // start+ chức năng xem trước lịch đặt định kỳ (preview trước khi tạo series)
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ReservationSeriesPreviewItem> previewSeries(ReservationSeriesCreateRequest request, Authentication authentication) {
        com.finalProject.BookingMeetingRoom.model.entity.User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
        com.finalProject.BookingMeetingRoom.model.entity.Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new CustomException(ResponseCode.ROOM_NOT_FOUND));

        if (request.getDaysOfWeek() == null || request.getDaysOfWeek().isEmpty()) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "daysOfWeek is required");
        }

        Set<DayOfWeek> targetDays = request.getDaysOfWeek().stream()
                .map(String::trim).map(String::toUpperCase)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());

        int rollingWeeks = request.getRollingWindowWeeks() == null ? DEFAULT_ROLLING_WINDOW_WEEKS : request.getRollingWindowWeeks();
        LocalDate maxUntil = LocalDate.now().plusWeeks(Math.max(1, rollingWeeks));
        if (request.getUntilDate() != null && request.getUntilDate().isBefore(maxUntil)) {
            maxUntil = request.getUntilDate();
        }

        LocalTime startT = request.getStartTimeOfDay();
        LocalTime endT = request.getEndTimeOfDay();

        List<ReservationSeriesPreviewItem> results = new ArrayList<>();
        LocalDate cursor = request.getFromDate();

        while (!cursor.isAfter(maxUntil)) {
            if (targetDays.contains(cursor.getDayOfWeek())) {
                LocalDateTime startTime = cursor.atTime(startT);
                LocalDateTime endTime = endT.isAfter(startT) ? cursor.atTime(endT) : cursor.plusDays(1).atTime(endT);

                boolean canBook = true;
                String conflictReason = null;

                List<com.finalProject.BookingMeetingRoom.model.entity.Reservation> userOverlap =
                        reservationRepository.checkOverlapByUser(user.getId(), startTime, endTime,
                                List.of(ReservationStatus.IN_USE.name(), ReservationStatus.RESERVED.name(), ReservationStatus.PENDING.name()));
                if (!userOverlap.isEmpty()) {
                    canBook = false;
                    conflictReason = "USER_CONFLICT";
                }

                if (canBook) {
                    boolean roomConflict = reservationRepository
                            .checkOverlappingReservationsByRoom(room.getId(), startTime, endTime)
                            .stream()
                            .anyMatch(r -> Set.of(ReservationStatus.IN_USE, ReservationStatus.RESERVED, ReservationStatus.PENDING).contains(r.getStatus()));
                    if (roomConflict) {
                        canBook = false;
                        conflictReason = "ROOM_CONFLICT";
                    }
                }

                if (canBook && academicScheduleService.isRoomBusyWithLearning(room.getId(), startTime, endTime)) {
                    canBook = false;
                    conflictReason = "ACADEMIC_SCHEDULE";
                }

                results.add(ReservationSeriesPreviewItem.builder()
                        .date(cursor)
                        .startTime(startTime)
                        .endTime(endTime)
                        .canBook(canBook)
                        .conflictReason(conflictReason)
                        .build());
            }
            cursor = cursor.plusDays(1);
        }

        return results;
    }
    // end+ chức năng xem trước lịch đặt định kỳ

    private void tryCreateReservationForOccurrence(ReservationSeries series, LocalDate occurrenceDate) {
        User user = series.getUser();
        Room room = series.getRoom();

        if (user.getBookingLockedUntil() != null && LocalDateTime.now().isBefore(user.getBookingLockedUntil())) {
            return;
        }

        LocalDateTime startTime = buildStartDateTime(occurrenceDate, series.getStartTimeOfDay());
        LocalDateTime endTime = buildEndDateTime(occurrenceDate, series.getStartTimeOfDay(), series.getEndTimeOfDay());

        if (academicScheduleService.isRoomBusyWithLearning(room.getId(), startTime, endTime)) {
            return;
        }

        List<Reservation> checkOverlapByUser = reservationRepository.checkOverlapByUser(
                user.getId(),
                startTime,
                endTime,
                List.of(ReservationStatus.IN_USE.name(), ReservationStatus.RESERVED.name(), ReservationStatus.PENDING.name()));
        if (!checkOverlapByUser.isEmpty()) {
            return;
        }

        boolean conflict = reservationRepository
                .checkOverlappingReservationsByRoom(room.getId(), startTime, endTime)
                .stream()
                .anyMatch(r -> Set.of(ReservationStatus.IN_USE, ReservationStatus.RESERVED, ReservationStatus.PENDING).contains(r.getStatus()));
        if (conflict) {
            return;
        }

        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID().toString());
        reservation.setRoom(room);
        reservation.setUser(user);
        reservation.setStartTime(startTime);
        reservation.setEndTime(endTime);
        reservation.setPurpose(series.getPurpose());
        reservation.setNote(series.getNote());
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setCreateAt(LocalDateTime.now());
        reservation.setUpdatedAt(LocalDateTime.now());
        reservation.setSeriesId(series.getId());
        reservation.setSeriesDate(occurrenceDate);

        reservationRepository.save(reservation);
        reservationHistoryService.saveHistory(reservation, user.getId(),
                ReservationStatus.RESERVED, null, reservation.getUpdatedAt());
        realTimeService.addReservation(reservation);
    }
}
// end+ chức năng đặt phòng lặp lại (weekly recurring + sync job)
