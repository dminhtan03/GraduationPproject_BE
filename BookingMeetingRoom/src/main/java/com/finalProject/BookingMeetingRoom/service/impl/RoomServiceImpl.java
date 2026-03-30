package com.finalProject.BookingMeetingRoom.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.finalProject.BookingMeetingRoom.repository.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Amenity;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.RoomImage;
import com.finalProject.BookingMeetingRoom.model.request.FeedbackInfoRequest;
import com.finalProject.BookingMeetingRoom.model.request.FloorLayoutRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomSearchRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.response.RoomDetailResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomImageResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomSearchResponse;
import com.finalProject.BookingMeetingRoom.service.RoomService;

import lombok.RequiredArgsConstructor;

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
    private final RoomImageRepository roomImageRepository;
    private final Cloudinary cloudinary;
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
    @Transactional(readOnly = true)
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

            var images = room.getImages() == null
                    ? List.<RoomImageResponse>of()
                    : room.getImages().stream()
                    .map(image -> RoomImageResponse.builder()
                            .id(image.getId())
                            .imageUrl(image.getImageUrl())
                            .publicId(image.getPublicId())
                            .createdAt(image.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());

            return RoomDetailResponse.builder()
                    .id(room.getId())
                    .locationCode(room.getLocationCode())
                    .status(room.getStatus())
                    .capacity(room.getCapacity())
                    .amenities(room.getAmenities())
                    .images(images)
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

    @Override
    public List<Amenity> getAllAmenities() {
        return amenityRepository.findAll();
    }

    // start implement addRoom
    @Override
    @Transactional
    public void addRoom(RoomCreateRequest request, MultipartFile image) {
        try {
            Floor floor = floorRepository.findById(request.getFloorId())
                    .orElseThrow(() -> new CustomException(ResponseCode.FLOOR_NOT_FOUND));

            // [ADDED] Check if room name already exists on this floor
            if (roomRepository.existsByFloorIdAndLocationCode(request.getFloorId(), request.getLocationCode())) {
                throw new CustomException(ResponseCode.ROOM_ALREADY_EXISTS);
            }

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

            // Handle Image if provided (from MultipartFile)
            if (image != null && !image.isEmpty()) {
                try {
                    Map<?, ?> uploadResult = cloudinary.uploader().upload(
                            image.getBytes(),
                            ObjectUtils.asMap(
                                    "folder", "meeting-room/" + room.getId(),
                                    "resource_type", "image"
                            )
                    );

                    RoomImage roomImage = new RoomImage();
                    roomImage.setId(UUID.randomUUID().toString());
                    roomImage.setImageUrl(uploadResult.get("secure_url").toString());
                    roomImage.setPublicId(uploadResult.get("public_id").toString());
                    roomImage.setCreatedAt(LocalDateTime.now());
                    roomImage.setRoom(room);
                    roomImageRepository.save(roomImage);
                } catch (Exception e) {
                    logger.error("Upload image to Cloudinary failed: " + e.getMessage());
                }
            }
            // Handle Image if provided (from URL - for Excel import fallback or direct URL)
            else if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
                RoomImage roomImage = new RoomImage();
                roomImage.setId(UUID.randomUUID().toString());
                roomImage.setImageUrl(request.getImageUrl());
                roomImage.setPublicId(request.getPublicId());
                roomImage.setCreatedAt(LocalDateTime.now());
                roomImage.setRoom(room);
                roomImageRepository.save(roomImage);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error adding room: " + e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
    // end implement addRoom

    // start implement updateRoom
    @Override
    @Transactional
    public void updateRoom(RoomUpdateRequest request) {
        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new CustomException(ResponseCode.ROOM_NOT_FOUND));

            if (request.getCapacity() != null) {
                room.setCapacity(request.getCapacity());
            }

            if (request.getStatus() != null) {
                room.setStatus(request.getStatus());
            }

            if (request.getAmenityIds() != null) {
                // To easily add/remove/change amenities, we replace the whole list
                room.setAmenities(amenityRepository.findAllById(request.getAmenityIds()));
            }

            room.setUpdatedAt(LocalDateTime.now());
            roomRepository.save(room);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error updating room: " + e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
    // end implement updateRoom

    // start implement importRoomsFromExcel
    @Override
    public void importRoomsFromExcel(MultipartFile file, String floorId) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<String> skippedRooms = new ArrayList<>();
            List<String> existingRoomNames = (floorId != null && !floorId.isEmpty())
                    ? roomRepository.findLocationCodesByFloorId(floorId)
                    : new ArrayList<>();

            for (Row row : sheet) {
                // Skip header row
                if (row.getRowNum() == 0) continue;

                try {
                    String locationCode = getCellValue(row.getCell(0));
                    String statusStr = getCellValue(row.getCell(1));
                    String capacityStr = getCellValue(row.getCell(2));
                    String scoreStr = getCellValue(row.getCell(3));
                    String excelFloorId = getCellValue(row.getCell(4));
                    String amenityIdsStr = getCellValue(row.getCell(5));

                    String targetFloorId = (floorId != null && !floorId.isEmpty()) ? floorId : excelFloorId;

                    // Image from excel (Cột 6 và 7)
                    String imageUrl = getCellValue(row.getCell(6));
                    String publicId = getCellValue(row.getCell(7));

                    if (locationCode.isEmpty() || targetFloorId.isEmpty()) {
                        logger.warn("Skipping row " + row.getRowNum() + ": locationCode or floorId is missing.");
                        continue;
                    }

                    // [ADDED] Skip if room already exists on this floor
                    if (existingRoomNames.contains(locationCode)) {
                        logger.warn("Skipping duplicate room: " + locationCode);
                        skippedRooms.add(locationCode);
                        continue;
                    }

                    RoomCreateRequest request = RoomCreateRequest.builder()
                            .locationCode(locationCode)
                            .status(statusStr.isEmpty() ? RoomStatus.AVAILABLE : RoomStatus.valueOf(statusStr.toUpperCase()))
                            .capacity(capacityStr.isEmpty() ? 0 : (int) Double.parseDouble(capacityStr))
                            .score(scoreStr.isEmpty() ? 0.0 : Double.parseDouble(scoreStr))
                            .floorId(targetFloorId)
                            .amenityIds(amenityIdsStr.isEmpty() ? new ArrayList<>() :
                                    Arrays.asList(amenityIdsStr.split(",")))
                            .build();

                    // Handle local image upload if provided in Excel
                    if (!imageUrl.isEmpty()) {
                        try {
                            // Check if imageUrl is actually a local file path
                            File localFile = new File(imageUrl);
                            if (localFile.exists() && localFile.isFile()) {
                                try (FileInputStream fis = new FileInputStream(localFile)) {
                                    Map<?, ?> uploadResult = cloudinary.uploader().upload(
                                            fis.readAllBytes(),
                                            ObjectUtils.asMap(
                                                    "folder", "meeting-room/imported",
                                                    "resource_type", "image"
                                            )
                                    );
                                    request.setImageUrl(uploadResult.get("secure_url").toString());
                                    request.setPublicId(uploadResult.get("public_id").toString());
                                }
                            } else {
                                // Assume it's already a URL
                                request.setImageUrl(imageUrl);
                                request.setPublicId(publicId);
                            }
                        } catch (Exception e) {
                            logger.error("Error processing image from excel for room " + locationCode + ": " + e.getMessage());
                        }
                    }

                    addRoom(request, null);
                    // Add new room name to the list to prevent duplicates within the same Excel file
                    existingRoomNames.add(locationCode);
                } catch (Exception e) {
                    logger.error("Error processing row " + row.getRowNum() + ": " + e.getMessage());
                }
            }

            // If there were any skipped rooms, notify the user
            if (!skippedRooms.isEmpty()) {
                String message = "Import completed, but the following rooms were skipped because they already exist: " +
                        String.join(", ", skippedRooms);
                throw new CustomException(ResponseCode.ROOM_ALREADY_EXISTS, message);
            }

        } catch (CustomException e) {
            throw e;
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

    // start implement updateFloorLayout
    @Override
    @Transactional
    public void updateFloorLayout(String floorId, FloorLayoutRequest request) {
        try {
            if (request.getItems() == null) return;

            for (FloorLayoutRequest.RoomLayoutItem item : request.getItems()) {
                roomRepository.findById(item.getRoomId()).ifPresent(room -> {
                    // Always update if it belongs to the floor or we trust the frontend
                    // Let's add a check to be safe
                    if (room.getFloor() != null && room.getFloor().getId().equals(floorId)) {
                        room.setXPosition(item.getX());
                        room.setYPosition(item.getY());
                        room.setWidth(item.getWidth());
                        room.setHeight(item.getHeight());
                        room.setPositioned(true);
                        room.setUpdatedAt(LocalDateTime.now());
                        roomRepository.save(room);
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Error updating floor layout: " + e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
    // end implement updateFloorLayout
}
