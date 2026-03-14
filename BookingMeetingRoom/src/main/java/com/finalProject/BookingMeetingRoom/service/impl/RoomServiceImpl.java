package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.FeedbackInfoRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomSearchRequest;
// start add import
import com.finalProject.BookingMeetingRoom.model.request.RoomCreateRequest;
import com.finalProject.BookingMeetingRoom.repository.AmenityRepository;
// start add excel imports
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
// end add excel imports
import java.util.UUID;
// end add import
import com.finalProject.BookingMeetingRoom.model.response.RoomDetailResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomSearchResponse;
import com.finalProject.BookingMeetingRoom.repository.FeedbackRepository;
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
    private final FeedbackRepository feedbackRepository;
    // start add repository
    private final AmenityRepository amenityRepository;
    // end add repository

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
    public RoomDetailResponse getRoomDetail(String roomId) {
        try {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new CustomException(ResponseCode.ROOM_NOT_FOUND));

            RoomRepository.CurrentUserProjection currentUser = roomRepository.findCurrentUserByRoomId(roomId);

            var feedbacks = feedbackRepository.findByRoomIdOrderByCreatedAtDesc(roomId)
                    .stream()
                    .map(f -> FeedbackInfoRequest.builder()
                            .id(f.getId())
                            .rating(f.getRating())
                            .description(f.getDescription())
                            .createdAt(f.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());

            return RoomDetailResponse.builder()
                    .roomId(room.getId())
                    .locationCode(room.getLocationCode())
                    .status(room.getStatus())
                    .capacity(room.getCapacity())
                    .amenities(room.getAmenities())
                    .images(room.getImages())
                    .score(room.getScore())
                    .currentUserId(currentUser != null ? currentUser.getUserId() : null)
                    .currentUserName(currentUser != null ? currentUser.getUserName() : null)
                    .checkInTime(currentUser != null ? currentUser.getCheckInTime() : null)
                    .feedbacks(feedbacks)
                    .build();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    // start implement addRoom
    @Override
    public void addRoom(RoomCreateRequest request) {
        try {
            Floor floor = floorRepository.findById(request.getFloorId())
                    .orElseThrow(() -> new CustomException(ResponseCode.FLOOR_NOT_FOUND));

            Room room = new Room();
            room.setId(UUID.randomUUID().toString());
            room.setLocationCode(request.getLocationCode());
            room.setStatus(request.getStatus());
            room.setCapacity(request.getCapacity());
            room.setScore(request.getScore() != null ? request.getScore() : 0.0);
            room.setFloor(floor);
            room.setCreateAt(LocalDateTime.now());
            room.setUpdatedAt(LocalDateTime.now());

            if (request.getAmenityIds() != null && !request.getAmenityIds().isEmpty()) {
                room.setAmenities(amenityRepository.findAllById(request.getAmenityIds()));
            }

            roomRepository.save(room);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error adding room: " + e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
    // end implement addRoom

    // start implement importRoomsFromExcel
    @Override
    public void importRoomsFromExcel(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                // Skip header row
                if (row.getRowNum() == 0) continue;

                try {
                    String locationCode = getCellValue(row.getCell(0));
                    String statusStr = getCellValue(row.getCell(1));
                    String capacityStr = getCellValue(row.getCell(2));
                    String scoreStr = getCellValue(row.getCell(3));
                    String floorId = getCellValue(row.getCell(4));
                    String amenityIdsStr = getCellValue(row.getCell(5));

                    if (locationCode.isEmpty() || floorId.isEmpty()) continue;

                    RoomCreateRequest request = RoomCreateRequest.builder()
                            .locationCode(locationCode)
                            .status(RoomStatus.valueOf(statusStr.toUpperCase()))
                            .capacity((int) Double.parseDouble(capacityStr))
                            .score(scoreStr.isEmpty() ? 0.0 : Double.parseDouble(scoreStr))
                            .floorId(floorId)
                            .amenityIds(amenityIdsStr.isEmpty() ? new ArrayList<>() :
                                    Arrays.asList(amenityIdsStr.split(",")))
                            .build();

                    addRoom(request);
                } catch (Exception e) {
                    logger.error("Error processing row " + row.getRowNum() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error importing rooms from excel: " + e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }
    // end implement importRoomsFromExcel
}
