package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.RoomSearchRequest;
import com.finalProject.BookingMeetingRoom.model.response.RoomDetailResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomSearchResponse;
import com.finalProject.BookingMeetingRoom.repository.FloorRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final Logger logger = LoggerFactory.getLogger(RoomServiceImpl.class);
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;

    /**
     * Searches for available rooms based on the provided request.
     *
     * @param request the room search request containing floor ID and time range
     * @return a list of available rooms
     */
    @Override
    public List<RoomSearchResponse> searchRooms(RoomSearchRequest request) {
        try {
            Floor floor = floorRepository.findById(request.getFloorId())
                    .orElseThrow(() -> new CustomException(ResponseCode.FLOOR_NOT_FOUND));

            LocalDateTime startTime = request.getStartTime();
            LocalDateTime endTime = request.getEndTime();

            if (request.getStartTime() == null || request.getEndTime() == null) {
                throw new CustomException(ResponseCode.VALIDATION_FAILED);
            }

            if (!request.getStartTime().isBefore(request.getEndTime())) {
                throw new CustomException(ResponseCode.VALIDATION_FAILED);
            }

            List<Room> rooms = roomRepository.findByFloor(floor);

            return rooms.stream()
                    .map(room -> {
                        boolean hasConflict = reservationRepository
                                .findOverlappingReservations(room.getId(), startTime, endTime)
                                .stream()
                                .anyMatch(r ->
                                        r.getStatus() == ReservationStatus.IN_USE ||
                                                r.getStatus() == ReservationStatus.RESERVED
                                );

                        return new RoomSearchResponse(
                                room.getId(),
                                room.getLocationCode(),
                                room.getScore(),
                                hasConflict ? RoomStatus.UNAVAILABLE : RoomStatus.AVAILABLE
                        );
                    })
                    .filter(roomResponse -> roomResponse.getStatus() == RoomStatus.AVAILABLE)
                    .collect(Collectors.toList());
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Page<RoomSearchResponse> getRoomStatus(RoomSearchRequest request, int page, int size) {
        try {
            RoomContext context = validateAndGetContext(request);
            Pageable pageable = PageRequest.of(page, size);

            return roomRepository.findByFloorOrderByLocationCode(context.floor, pageable)
                    .map(room -> mapToRoomSearchResponse(room, context.startTime, context.endTime));
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    private static class RoomContext {
        Floor floor;
        LocalDateTime startTime;
        LocalDateTime endTime;

        public RoomContext(Floor floor, LocalDateTime startTime, LocalDateTime endTime) {
            this.floor = floor;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    private RoomContext validateAndGetContext(RoomSearchRequest request) {
        if (request.getStartTime() == null || request.getEndTime() == null ||
                !request.getStartTime().isBefore(request.getEndTime())) {
            throw new CustomException(ResponseCode.ROOM_NOT_FOUND);
        }

        Floor floor = floorRepository.findById(request.getFloorId())
                .orElseThrow(() -> new CustomException(ResponseCode.FLOOR_NOT_FOUND));

        return new RoomContext(floor, request.getStartTime(), request.getEndTime());
    }

    private RoomSearchResponse mapToRoomSearchResponse(Room room, LocalDateTime startTime, LocalDateTime endTime) {
        boolean hasConflict = (room.getStatus() == RoomStatus.BROKEN) || reservationRepository
                .findOverlappingReservations(room.getId(), startTime, endTime)
                .stream()
                .anyMatch(r ->
                        r.getStatus() == ReservationStatus.IN_USE ||
                                r.getStatus() == ReservationStatus.RESERVED
                );

        return new RoomSearchResponse(
                room.getId(),
                room.getLocationCode(),
                room.getScore(),
                hasConflict ? RoomStatus.UNAVAILABLE : RoomStatus.AVAILABLE
        );
    }

    /**
     * Retrieves detailed information about a specific room.
     *
     * @param roomId the ID of the room to retrieve details for
     * @return RoomDetailResponse containing user information and check-in time
     */
    @Override
    public RoomDetailResponse getRoomDetails(String roomId) {
        try {
            var room = roomRepository.findRoomInMap(roomId);
            if (room == null) {
                throw new CustomException(ResponseCode.ROOM_NOT_FOUND);
            }
            return RoomDetailResponse.builder()
                    .currentUserId(room.getUserId())
                    .currentUserName(room.getUserName())
                    .checkInTime(room.getCheckInTime())
                    .build();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

}
