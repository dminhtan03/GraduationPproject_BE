package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;

import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.ReservationHistoryService;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapper;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapperFacade;
import com.finalProject.BookingMeetingRoom.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

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

@Service
@Slf4j
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final ReservationMapper reservationMapper;
    private final RoomRepository roomRepository;
    private final ReservationMapperFacade reservationMapperFacade;
    private final ReservationHistoryService reservationHistoryService;

    record ReservationContext(Reservation reservation, Room room) {}


    public ReservationResponse reserveRoom(ReservationRequest request, Authentication connectedUser) {
        try {
            var room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new CustomException(ResponseCode.ROOM_NOT_FOUND));

            var user = userRepository.findByEmail(connectedUser.getName())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

            var startTime = request.getStartTime();
            var endTime = request.getEndTime();

            List<Reservation> checkOverlapByUser = reservationRepository.checkOverlapByUser(user.getId(), startTime,
                    endTime, List.of(ReservationStatus.IN_USE.name(), ReservationStatus.RESERVED.name()));

            if (!checkOverlapByUser.isEmpty()) {
                throw new CustomException(ResponseCode.USER_TIME_OVERLAP);
            }

            boolean conflict = reservationRepository
                    .checkOverlappingReservationsByRoom(room.getId(), startTime, endTime)
                    .stream()
                    .anyMatch(r -> Set.of(ReservationStatus.IN_USE, ReservationStatus.RESERVED)
                            .contains(r.getStatus()));

            if (conflict) {
                throw new CustomException(ResponseCode.CANNOT_RESERVE_ROOM);
            }

            var reservation = reservationMapper.toEntity(request);
            reservation.setId(UUID.randomUUID().toString());
            reservation.setRoom(room);
            reservation.setUser(user);
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);

            reservationHistoryService.saveHistory(reservation, user.getId(),
                    ReservationStatus.PENDING, null, reservation.getUpdatedAt());

            return reservationMapperFacade.toResponse(reservation);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during reserving room", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

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
}