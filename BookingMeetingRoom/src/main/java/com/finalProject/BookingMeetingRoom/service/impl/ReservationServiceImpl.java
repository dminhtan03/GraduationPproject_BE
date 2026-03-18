package com.finalProject.BookingMeetingRoom.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.finalProject.BookingMeetingRoom.common.enums.HistoryAction;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapper;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapperFacade;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomReserveStatusUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.response.MyReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
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
    private final RealTimeService realTimeService;
    private final UserRepository userRepository;
    private final ReservationMapper reservationMapper;
    private final RoomRepository roomRepository;
    private final ReservationMapperFacade reservationMapperFacade;
    private final ReservationHistoryService reservationHistoryService;

    record ReservationContext(Reservation reservation, Room room) {}


     /**
     * Return a seat after use.
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

            reservation.setStatus(ReservationStatus.COMPLETED);
            reservation.setReturnTime(LocalDateTime.now());
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);

            room.setStatus(RoomStatus.AVAILABLE);
            roomRepository.save(room);

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
                    user.getId(), newStartTime.toLocalDate()
            );

            if ((totalMinutes + convertToMinutes(hour)) > convertToMinutes(8)) {
                throw new CustomException(ResponseCode.USER_TIME_EXCEEDED);
            }

            reservationHistoryService.saveHistory(reservation, user.getId(), null,
                    HistoryAction.EXTEND, reservation.getUpdatedAt());

            reservation.setEndTime(newEndTime);
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);

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

            reservationHistoryService.saveHistory(reservation, reservation.getUser().getId(),
                    ReservationStatus.RESERVED, null, reservation.getUpdatedAt());

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
                    endTime, List.of(ReservationStatus.IN_USE.name(), ReservationStatus.RESERVED.name(), ReservationStatus.PENDING.name()));

            if (!checkOverlapByUser.isEmpty()) {
                throw new CustomException(ResponseCode.USER_TIME_OVERLAP);
            }

            // Check overlap for room (including PENDING, RESERVED, IN_USE)
            boolean conflict = reservationRepository
                    .checkOverlappingReservationsByRoom(room.getId(), startTime, endTime)
                    .stream()
                    .anyMatch(r -> Set.of(ReservationStatus.IN_USE, ReservationStatus.RESERVED, ReservationStatus.PENDING)
                            .contains(r.getStatus()));

            if (conflict) {
                throw new CustomException(ResponseCode.CANNOT_RESERVE_ROOM);
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

    public Page<MyReservationResponse> getReservationStatus(
            int page, int size, Authentication connectedUser,
            String locationCode, String address, List<String> statuses,
            String buildingId, String startTime, String endTime
    ) {
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
                    ReservationStatus.IN_USE
            );

            // start update getReservationStatus to handle invalid status strings safely
            boolean containsOnlyActiveStatuses = statuses != null && statuses.stream()
                    .map(String::toUpperCase)
                    .filter(s -> {
                        try {
                            ReservationStatus.valueOf(s);
                            return true;
                        } catch (IllegalArgumentException e) {
                            return false;
                        }
                    })
                    .map(ReservationStatus::valueOf)
                    .allMatch(activeStatuses::contains);
            // end update getReservationStatus to handle invalid status strings safely

            boolean isStartTimeProvided = startTime != null && !startTime.isBlank();
            boolean isEndTimeProvided = endTime != null && !endTime.isBlank();

            if (isStartTimeProvided) {
                LocalDateTime parsedStartTime = LocalDateTime.parse(startTime, formatter);
                effectiveStartTime = parsedStartTime.format(formatter);
                effectiveEndTime = isEndTimeProvided ?
                        LocalDateTime.parse(endTime, formatter).format(formatter) :
                        null;
            } else {
                if (!containsOnlyActiveStatuses) {
                    effectiveStartTime = LocalDateTime.now().minusMonths(1).format(formatter);
                    effectiveEndTime = null;
                } else {
                    effectiveStartTime = LocalDate.now().atStartOfDay().format(formatter);
                    effectiveEndTime = null;
                }
            }

            var myReservation = reservationRepository.findMyReservations(
                    user.getId(),
                    locationCode,
                    address,
                    statuses,
                    buildingId,
                    effectiveStartTime,
                    effectiveEndTime,
                    pageable
            );

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
     * @param connectedUser the authentication object containing user details
     */
    @Transactional
    public void cancelReservation(String reservationId, Authentication connectedUser) {
        try {
            var reservation = reservationRepository.findByIdAndStatus(reservationId, ReservationStatus.RESERVED)
                    .orElseThrow(() -> new CustomException(ResponseCode.RESERVATION_INVALID_STATUS));

            var user = userRepository.findByEmail(connectedUser.getName())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

            if (!reservation.getUser().getId().equals(user.getId())) {
                throw new CustomException(ResponseCode.PERMISSION_DENIED);
            }

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

            reservationHistoryService.saveHistory(reservation, user.getId(),
                    ReservationStatus.RESERVED, null, reservation.getUpdatedAt());

            reservation.setStatus(ReservationStatus.CANCELLED);
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);

            realTimeService.deleteReservation(reservation);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during cancelling", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Validate the reservation context (Reservation and Seat).
     *
     * @param reservationId  the ID of the reservation to validate
     * @param authentication the authentication object containing user details
     * @return a ReservationContext containing the reservation and seat information
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