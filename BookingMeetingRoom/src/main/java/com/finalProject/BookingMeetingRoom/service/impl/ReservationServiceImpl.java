package com.finalProject.BookingMeetingRoom.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.finalProject.BookingMeetingRoom.common.enums.HistoryAction;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.FeedbackMapper;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapper;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapperFacade;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomReserveStatusUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.response.AdminReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.MyReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationDetailResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationHistoryResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationTimelineResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomImageResponse;
import com.finalProject.BookingMeetingRoom.repository.ReservationHistoryRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserInfoRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;
import com.finalProject.BookingMeetingRoom.service.EmailService;
import com.finalProject.BookingMeetingRoom.service.NotificationService;
import com.finalProject.BookingMeetingRoom.service.RealTimeService;
import com.finalProject.BookingMeetingRoom.service.ReservationHistoryService;
import com.finalProject.BookingMeetingRoom.service.ReservationService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationHistoryRepository reservationHistoryRepository;
    private final RealTimeService realTimeService;
    private final UserRepository userRepository;
    private final UserInfoRepository userInfoRepository;
    private final ReservationMapper reservationMapper;
    private final RoomRepository roomRepository;
    private final ReservationMapperFacade reservationMapperFacade;
    private final ReservationHistoryService reservationHistoryService;
    private final FeedbackMapper feedbackMapper;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AcademicScheduleService academicScheduleService;

    record ReservationContext(Reservation reservation, Room room) {
    }

    /**
     * Return a room after use.
     *
     * @param reservationId  the ID of the reservation to return
     * @param authentication the authentication object containing user details
     */
    @Transactional
    public void returnRoom(String reservationId, Authentication authentication) {
        try {

            var context = validateReservationContext(reservationId, authentication);
            var reservation = context.reservation();
            var room = context.room();

            if (!reservation.getStatus().equals(ReservationStatus.IN_USE)) {
                throw new CustomException(ResponseCode.RESERVATION_NOT_IN_USE);
            }

            if (!room.getStatus().equals(RoomStatus.UNAVAILABLE)) {
                throw new CustomException(ResponseCode.ROOM_NOT_UNAVAILABLE);
            }
            List<Reservation> listReservationReturnRoom = new ArrayList<>();

            reservation.setStatus(ReservationStatus.COMPLETED);
            reservation.setReturnTime(LocalDateTime.now());
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);

            room.setStatus(RoomStatus.AVAILABLE);
            roomRepository.save(room);
            listReservationReturnRoom.add(reservation);
            notificationService.noticeReturnRoomReservation(listReservationReturnRoom);

            realTimeService.sendRoomStatus(room);
            realTimeService.deleteReservation(reservation);


        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Extend a reservation for a specified number of hours.
     *
     * @param reservationId the ID of the reservation to extend
     * @param hour          the number of hours to extend (between 1 and 8)
     * @param connectedUser the authentication object containing user details
     */
    @Transactional
    public void extendReservation(String reservationId, double hour, Authentication connectedUser) {
        try {

            if (hour < 1 || hour > 8) {
                throw new CustomException(ResponseCode.RESERVATION_INVALID_HOUR);
            }

            var reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new CustomException(ResponseCode.RESERVATION_NOT_FOUND));

            Set<ReservationStatus> allowedStatuses = Set.of(ReservationStatus.IN_USE, ReservationStatus.RESERVED);
            if (!allowedStatuses.contains(reservation.getStatus())) {
                throw new CustomException(ResponseCode.RESERVATION_NOT_IN_USE);
            }

            var room = reservation.getRoom();
            if (room.getStatus() == RoomStatus.BROKEN) {
                throw new CustomException(ResponseCode.ROOM_BROKEN);
            }

            var user = userRepository.findByEmail(connectedUser.getName())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

            if (!reservation.getUser().getId().equals(user.getId())) {
                throw new CustomException(ResponseCode.PERMISSION_DENIED);
            }

            LocalDateTime newStartTime = reservation.getEndTime();
            LocalDateTime newEndTime = newStartTime.plusMinutes(convertToMinutes(hour));

            if (newEndTime.toLocalDate().isAfter(newStartTime.toLocalDate())) {
                throw new CustomException(ResponseCode.RESERVATION_NOT_EXTEND_AFTER_MIDNIGHT);
            }

            boolean isOverLap = reservationRepository.checkOverlapReservation(room.getId(),
                    List.of(ReservationStatus.RESERVED, ReservationStatus.IN_USE),
                    newStartTime,
                    newEndTime,
                    reservationId);

            if (isOverLap) {
                throw new CustomException(ResponseCode.RESERVATION_TIME_OVERLAP);
            }

            long totalMinutes = reservationRepository.getTotalReservedMinutesForUser(
                    user.getId(), newStartTime.toLocalDate());

            if ((totalMinutes + convertToMinutes(hour)) > convertToMinutes(8)) {
                throw new CustomException(ResponseCode.USER_TIME_EXCEEDED);
            }

            List<Reservation> listReservationExtend = new ArrayList<>();
            reservationHistoryService.saveHistory(reservation, user.getId(), null,
                    HistoryAction.EXTEND, reservation.getUpdatedAt());

            reservation.setEndTime(newEndTime);
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);
            listReservationExtend.add(reservation);
            notificationService.noticeExtendReservation(listReservationExtend);

            realTimeService.addReservation(reservation);

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during extending", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Check-in a reservation.
     *
     * @param reservationId  the ID of the reservation to check in
     * @param authentication the authentication object containing user details
     */
    @Transactional
    public void checkIn(String reservationId, Authentication authentication) {
        try {

            var context = validateReservationContext(reservationId, authentication);
            var reservation = context.reservation();
            var room = context.room();

            if (!reservation.getStatus().equals(ReservationStatus.RESERVED)) {
                throw new CustomException(ResponseCode.RESERVATION_NOT_RESERVED);
            }

            if (!room.getStatus().equals(RoomStatus.AVAILABLE)) {
                throw new CustomException(ResponseCode.ROOM_NOT_AVAILABLE);
            }

            if (LocalDateTime.now().isBefore(reservation.getStartTime())) {
                throw new CustomException(ResponseCode.RESERVATION_NOT_TIME_CHECK_IN);
            }

            // start add check for 15 minutes limit
            if (LocalDateTime.now().isAfter(reservation.getStartTime().plusMinutes(15))) {
                throw new CustomException(ResponseCode.RESERVATION_CHECK_IN_EXPIRED);
            }
            // end add check for 15 minutes limit

            reservationHistoryService.saveHistory(reservation, reservation.getUser().getId(),
                    ReservationStatus.RESERVED, HistoryAction.CHECK_IN, reservation.getUpdatedAt());

            reservation.setStatus(ReservationStatus.IN_USE);
            reservation.setCheckinTime(LocalDateTime.now());
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);

            room.setStatus(RoomStatus.UNAVAILABLE);
            roomRepository.save(room);

            realTimeService.sendRealTimeRoomReserveStatusUpdate(
                    RoomReserveStatusUpdateRequest.builder()
                            .roomId(room.getId())
                            .type("UPDATE")
                            .leftTime(reservation.getStartTime())
                            .rightTime(reservation.getEndTime())
                            .build(),
                    room.getFloor().getId(),
                    reservation.getStartTime().toLocalDate().toString());

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    // start update reserveRoom with immediate reservation logic
    @Transactional
    public ReservationResponse reserveRoom(ReservationRequest request, Authentication connectedUser) {
        try {
            // Locking the room to prevent race conditions
            var room = roomRepository.findByIdForUpdate(request.getRoomId())
                    .orElseThrow(() -> new CustomException(ResponseCode.ROOM_NOT_FOUND));

            var user = userRepository.findByEmail(connectedUser.getName())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

            // start add check for booking lock
            if (user.getBookingLockedUntil() != null && LocalDateTime.now().isBefore(user.getBookingLockedUntil())) {
                throw new CustomException(ResponseCode.BOOKING_FUNCTION_LOCKED, user.getBookingLockedUntil());
            }
            // end add check for booking lock

            var startTime = request.getStartTime();
            var endTime = request.getEndTime();

            // Check overlap for user (including PENDING, RESERVED, IN_USE)
            List<Reservation> checkOverlapByUser = reservationRepository.checkOverlapByUser(user.getId(), startTime,
                    endTime, List.of(ReservationStatus.IN_USE.name(), ReservationStatus.RESERVED.name(),
                            ReservationStatus.PENDING.name()));

            if (!checkOverlapByUser.isEmpty()) {
                throw new CustomException(ResponseCode.USER_TIME_OVERLAP);
            }

            // Check overlap for room (including PENDING, RESERVED, IN_USE)
            boolean conflict = reservationRepository
                    .checkOverlappingReservationsByRoom(room.getId(), startTime, endTime)
                    .stream()
                    .anyMatch(
                            r -> Set.of(ReservationStatus.IN_USE, ReservationStatus.RESERVED, ReservationStatus.PENDING)
                                    .contains(r.getStatus()));

            if (conflict) {
                throw new CustomException(ResponseCode.CANNOT_RESERVE_ROOM);
            }

            // [HYBRID] Thêm bước kiểm tra lịch học cố định (Academic Schedule)
            if (academicScheduleService.isRoomBusyWithLearning(room.getId(), startTime, endTime)) {
                throw new CustomException(ResponseCode.ROOM_IN_ACADEMIC_SCHEDULE);
            }

            var reservation = reservationMapper.toEntity(request);
            reservation.setId(UUID.randomUUID().toString());
            reservation.setRoom(room);
            reservation.setUser(user);
            reservation.setStatus(ReservationStatus.RESERVED); // Change status to RESERVED directly
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);

            reservationHistoryService.saveHistory(reservation, user.getId(),
                    ReservationStatus.RESERVED, null, reservation.getUpdatedAt());

            // Send real-time update
            realTimeService.addReservation(reservation);

            return reservationMapperFacade.toResponse(reservation);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during reserving room", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
    // end update reserveRoom with immediate reservation logic

    public Page<ReservationResponse> getAllReservations(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);

            return reservationRepository.findAll(pageable)
                    .map(reservationMapperFacade::toResponse);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during get all reservations", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    // [ADDED] Get all reservations for admin with filtering
    @Override
    public Page<AdminReservationResponse> getAllReservationsForAdmin(int page, int size, ReservationStatus status,
            String userName, String userEmail, String roomName, String floorName, String buildingName,
            LocalDateTime startDate, LocalDateTime endDate) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Reservation> reservations = reservationRepository.findAllWithDetailsForAdmin(pageable, status,
                    userName, userEmail, roomName, floorName, buildingName, startDate, endDate);
            return reservations.map(reservationMapperFacade::toAdminResponse);
        } catch (Exception e) {
            log.error("Unexpected error during get all reservations for admin", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    public Page<MyReservationResponse> getReservationStatus(
            int page, int size, Authentication connectedUser,
            String locationCode, String address, List<String> statuses,
            String buildingId, String startTime, String endTime) {
        try {
            var user = userRepository.findByEmail(connectedUser.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Pageable pageable = PageRequest.of(page, size);

            String effectiveStartTime;
            String effectiveEndTime;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

            Set<ReservationStatus> activeStatuses = Set.of(
                    ReservationStatus.PENDING,
                    ReservationStatus.RESERVED,
                    ReservationStatus.IN_USE);

            // start update getReservationStatus to handle invalid status strings safely
            if (statuses != null) {
                statuses = statuses.stream()
                        .map(String::toUpperCase)
                        .map(s -> s.equals("CHECKED_IN") ? "IN_USE" : s) // map CHECKED_IN to IN_USE
                        .filter(s -> {
                            try {
                                ReservationStatus.valueOf(s);
                                return true;
                            } catch (IllegalArgumentException e) {
                                return false;
                            }
                        })
                        .collect(java.util.stream.Collectors.toList());
            }

            boolean containsOnlyActiveStatuses = statuses != null && !statuses.isEmpty() && statuses.stream()
                    .map(ReservationStatus::valueOf)
                    .allMatch(activeStatuses::contains);
            // end update getReservationStatus to handle invalid status strings safely

            boolean isStartTimeProvided = startTime != null && !startTime.isBlank();
            boolean isEndTimeProvided = endTime != null && !endTime.isBlank();

            if (isStartTimeProvided) {
                LocalDateTime parsedStartTime = LocalDateTime.parse(startTime, formatter);
                effectiveStartTime = parsedStartTime.format(formatter);
                effectiveEndTime = isEndTimeProvided ? LocalDateTime.parse(endTime, formatter).format(formatter) : null;
            } else {
                if (!containsOnlyActiveStatuses) {
                    effectiveStartTime = LocalDateTime.now().minusMonths(1).format(formatter);
                    effectiveEndTime = null;
                } else {
                    effectiveStartTime = LocalDate.now().atStartOfDay().format(formatter);
                    effectiveEndTime = null;
                }
            }

            // start add fix statuses null for native query
            if (statuses == null || statuses.isEmpty()) {
                statuses = java.util.Arrays.stream(ReservationStatus.values())
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.toList());
            }
            // end add fix statuses null for native query

            var myReservation = reservationRepository.findMyReservations(
                    user.getId(),
                    locationCode,
                    address,
                    statuses,
                    buildingId,
                    effectiveStartTime,
                    effectiveEndTime,
                    pageable);

            return myReservation.map(reservationMapperFacade::toMyResponse);
        } catch (CustomException e) {
            throw e;
        } catch (DateTimeParseException e) {
            log.warn("Invalid date format for startTime or endTime", e);
            throw new CustomException(ResponseCode.INVALID_DATE_FORMAT);
        } catch (Exception e) {
            log.error("Unexpected error during get reservation status", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Cancel a reservation and save the history of the action.
     *
     * @param reservationId the ID of the reservation to cancel
     * @param reason        the reason for cancellation
     * @param connectedUser the authentication object containing user details
     */
    @Transactional
    public void cancelReservation(String reservationId, String reason, Authentication connectedUser) {
        try {
            var reservation = reservationRepository.findByIdAndStatus(reservationId, ReservationStatus.RESERVED)
                    .orElseThrow(() -> new CustomException(ResponseCode.RESERVATION_INVALID_STATUS));

            var user = userRepository.findByEmail(connectedUser.getName())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

            if (!reservation.getUser().getId().equals(user.getId())) {
                throw new CustomException(ResponseCode.PERMISSION_DENIED);
            }

             List<Reservation> listReservationCancel = new ArrayList<>();

            // start add cancellation limit logic
            LocalDate today = LocalDate.now();
            if (user.getLastCancellationDate() != null && user.getLastCancellationDate().isEqual(today)) {
                user.setCancellationCount(user.getCancellationCount() + 1);
            } else {
                user.setCancellationCount(1);
                user.setLastCancellationDate(today);
            }

            if (user.getCancellationCount() >= 3) {
                user.setBookingLockedUntil(LocalDateTime.now().plusHours(24));
            }
            userRepository.save(user);
            // end add cancellation limit logic

            reservation.setStatus(ReservationStatus.CANCELLED);
            reservation.setReason(reason);
            reservation.setCancelBy(user.getId());
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);
            listReservationCancel.add(reservation);
            notificationService.noticeCancelReservation(listReservationCancel);

            realTimeService.deleteReservation(reservation);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during cancelling", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Force cancel a reservation by admin with reason.
     * Sends email notification to the user about the cancellation.
     *
     * @param reservationId the ID of the reservation to force cancel
     * @param reason        the reason for force cancellation
     * @param adminUser     the authentication object containing admin details
     */
    @Transactional
    public void forceCancelReservation(String reservationId, String reason, Authentication adminUser) {
        try {
            var reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new CustomException(ResponseCode.RESERVATION_NOT_FOUND));

            var admin = userInfoRepository.findByEmail(adminUser.getName())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

            var bookingUser = reservation.getUser();
            if (bookingUser == null) {
                throw new CustomException(ResponseCode.USER_NOT_FOUND);
            }

            var room = reservation.getRoom();
            if (room == null) {
                throw new CustomException(ResponseCode.ROOM_NOT_FOUND);
            }

            List<Reservation> listReservationForceReturned = new ArrayList<>();

            // Update reservation status to cancelled
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservation.setReason(reason);
            reservation.setCancelBy(admin.getEmail()); // Save admin email
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);

            // Update room status to AVAILABLE so it can be booked immediately
            room.setStatus(RoomStatus.AVAILABLE);
            roomRepository.save(room);

            listReservationForceReturned.add(reservation);

            // Send real-time updates
            realTimeService.sendRoomStatus(room); // Notify room is available
            realTimeService.sendReservationStatus(reservation); // Update reservation status
            realTimeService.deleteReservation(reservation); // Remove from booking list

            notificationService.noticeForceCancelReservation(listReservationForceReturned);

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during force cancelling", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

     /**
     * Get the reservation timeline for a specific reservation ID.
     *
     * @param reservationId the ID of the reservation to get the timeline for
     * @return a list of reservation timeline responses
     */
    public List<ReservationTimelineResponse> getReservationTimeline(String reservationId) {
        try {
            final var reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new CustomException(ResponseCode.RESERVATION_NOT_FOUND));

            final String id = reservation.getId();
            final List<ReservationTimelineResponse> timeline = new ArrayList<>();

            timeline.add(new ReservationTimelineResponse(
                    id,
                    reservation.getStatus(),
                    null,
                    reservation.getUpdatedAt()
            ));

            if (reservation.getCheckinTime() != null) {
                timeline.add(new ReservationTimelineResponse(
                        id,
                        null,
                        HistoryAction.CHECK_IN,
                        reservation.getCheckinTime()
                ));
            }

            reservationHistoryRepository.findByReservationId(id).forEach(history -> {
                timeline.add(new ReservationTimelineResponse(
                        id,
                        history.getOldStatus(),
                        history.getAction(),
                        history.getPerformAt()
                ));
            });

            timeline.sort(Comparator.comparing(ReservationTimelineResponse::getPerformAt).reversed());

            return timeline;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during get reservation timeline", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get the reservation history for the connected user within a specified date range.
     *
     * @param startDate     the start date of the history (inclusive)
     * @param endDate       the end date of the history (inclusive)
     * @param page          the page number
     * @param size          the size of each page
     * @param connectedUser the authentication object containing user details
     * @return a paginated list of reservation responses for the user's history
     */
    public Page<ReservationResponse> getReservationHistory(LocalDate startDate, LocalDate endDate, int page, int size,
                                                           Authentication connectedUser) {
        try {
            var user = userRepository.findByEmail(connectedUser.getName())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

            LocalDate now = LocalDate.now();
            if (startDate == null && endDate == null) {
                endDate = now;
                startDate = now.minusMonths(1);
            } else if (startDate == null) {
                startDate = endDate.minusMonths(1);
            } else if (endDate == null) {
                endDate = startDate.plusMonths(1);
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

            return reservationRepository
                    .findByUserAndStatusInAndStartTimeBetween(
                            user,
                            List.of(ReservationStatus.NO_SHOW, ReservationStatus.FAILED,
                                    ReservationStatus.CANCELLED, ReservationStatus.COMPLETED,
                                    ReservationStatus.FORCE_CANCELLED),
                            startDateTime,
                            endDateTime,
                            pageable
                    )
                    .map(reservationMapperFacade::toResponse);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during get reservation history", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    public ReservationDetailResponse getReservationDetail(String reservationId, Authentication authentication) {
        try {
            var currentUser = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

            var reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new CustomException(ResponseCode.RESERVATION_NOT_FOUND));

            if (!reservation.getUser().getId().equals(currentUser.getId())) {
                throw new CustomException(ResponseCode.PERMISSION_DENIED);
            }

            var reservationResponse = reservationMapperFacade.toResponse(reservation);

            var roomImages = reservation.getRoom().getImages().stream()
                    .map(img -> RoomImageResponse.builder()
                            .id(img.getId())
                            .roomId(img.getRoom().getId())
                            .imageUrl(img.getImageUrl())
                            .publicId(img.getPublicId())
                            .createdAt(img.getCreatedAt())
                            .build())
                    .toList();

            var history = reservationHistoryRepository.findByReservationId(reservationId).stream()
                    .map(h -> ReservationHistoryResponse.builder()
                            .id(h.getId())
                            .oldStatus(h.getOldStatus())
                            .action(h.getAction())
                            .oldStartTime(h.getOldStartTime())
                            .oldEndTime(h.getOldEndTime())
                            .performBy(h.getPerformBy())
                            .performAt(h.getPerformAt())
                            .build())
                    .toList();

            var feedback = reservation.getFeedback() != null
                    ? feedbackMapper.toFeedbackResponse(reservation.getFeedback())
                    : null;

            return ReservationDetailResponse.builder()
                    .reservation(reservationResponse)
                    .roomImages(roomImages)
                    .history(history)
                    .feedback(feedback)
                    .build();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during get reservation detail", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Validate the reservation context (Reservation and Room).
     *
     * @param reservationId  the ID of the reservation to validate
     * @param authentication the authentication object containing user details
     * @return a ReservationContext containing the reservation and room information
     */
    private ReservationContext validateReservationContext(String reservationId, Authentication authentication) {
        var currentUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

        var reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ResponseCode.RESERVATION_NOT_FOUND));

        var room = roomRepository.findById(reservation.getRoom().getId())
                .orElseThrow(() -> new CustomException(ResponseCode.ROOM_NOT_FOUND));

        if (!reservation.getUser().getId().equals(currentUser.getId())) {
            throw new CustomException(ResponseCode.RESERVATION_USER_NOT_FOUND);
        }

        return new ReservationContext(reservation, room);
    }

    private long convertToMinutes(double hour) {
        return Math.round(hour * 60);
    }

}