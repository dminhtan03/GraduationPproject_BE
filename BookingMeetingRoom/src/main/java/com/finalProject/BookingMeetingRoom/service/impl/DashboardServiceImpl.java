package com.finalProject.BookingMeetingRoom.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.finalProject.BookingMeetingRoom.model.dto.AdminBuildingDto;
import com.finalProject.BookingMeetingRoom.model.dto.AdminFloorDto;
import com.finalProject.BookingMeetingRoom.model.response.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.dto.RoomDto;
import com.finalProject.BookingMeetingRoom.model.entity.Building;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.BuildingCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.BuildingUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.request.FloorCreateRequest;
import com.finalProject.BookingMeetingRoom.repository.BuildingRepository;
import com.finalProject.BookingMeetingRoom.repository.FloorRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;
import com.finalProject.BookingMeetingRoom.service.DashboardService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final Logger logger = LoggerFactory.getLogger(DashboardServiceImpl.class);
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final AcademicScheduleService academicScheduleService;

    /**
     * Retrieves the employee dashboard data including active reservations, last checked-in details, and hours worked.
     *
     * @param connectedUser the authenticated user
     * @return EmployeeDashboardResponse containing active reservations, last checked-in details, and hours worked
     */

    /**
     * Retrieves the room map dashboard data, organizing room information by building and floor.
     * Logic:
     * 1. Fetches a flat list of room data from the repository.
     * 2. Iterates through each room, grouping them by buildingId using a LinkedHashMap.
     * 3. For each building, checks if the floor exists; if not, creates a new floor entry.
     * 4. For each room, creates a RoomDto and adds it to the corresponding floor's room list.
     * 5. After grouping, builds and returns a RoomMapDashboardResponse containing the structured building, floor, and room data.
     * 6. Handles exceptions by throwing a CustomException with appropriate response codes.
     */
    @Override
    public RoomMapDashboardResponse getRoomsMapDashboard() {
        try {
            var roomMap = buildingRepository.findRoomMapDashBoard();

            Map<String, RoomMapBuildingResponse> buildingMap = new LinkedHashMap<>();

            for (var room : roomMap) {
                RoomMapBuildingResponse building = buildingMap.computeIfAbsent(
                        room.getBuildingId(),
                        buildingId -> RoomMapBuildingResponse.builder()
                                .buildingId(buildingId)
                                .buildingName(room.getBuildingName())
                                .floors(new ArrayList<>())
                                .build()
                );

                List<DetailFloorResponse> floors = building.getFloors();
                DetailFloorResponse floorResponse = floors.stream()
                        .filter(floor -> floor.getFloorId().equals(room.getFloorId()))
                        .findFirst()
                        .orElseGet(() -> {
                            DetailFloorResponse newFloor = new DetailFloorResponse();
                            newFloor.setFloorId(room.getFloorId());
                            newFloor.setFloorName(room.getFloorName());
                            newFloor.setRooms(new ArrayList<>());
                            floors.add(newFloor);
                            return newFloor;
                        });

                RoomDto roomDto = new RoomDto();
                roomDto.setId(room.getRoomId());
                roomDto.setLocationCode(room.getLocationCode());

                // [HYBRID] Cập nhật trạng thái động cho sơ đồ phòng (Dashboard/Map)
                RoomStatus status = room.getStatus();
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime nextMinute = now.plusMinutes(1);

                // Check for reservation conflict first
                boolean hasReservationConflict = reservationRepository
                        .findOverlappingReservations(room.getRoomId(), now, nextMinute)
                        .stream()
                        .anyMatch(res ->
                                res.getStatus() == com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus.IN_USE ||
                                        res.getStatus() == com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus.RESERVED
                        );

                if (status == RoomStatus.AVAILABLE) {
                    if (hasReservationConflict) {
                        status = RoomStatus.UNAVAILABLE;
                    } else if (academicScheduleService.isRoomBusyWithLearning(room.getRoomId(), now, nextMinute)) {
                        status = RoomStatus.LEARNING;
                    }
                } else if (status == RoomStatus.UNAVAILABLE && !hasReservationConflict) {
                    if (academicScheduleService.isRoomBusyWithLearning(room.getRoomId(), now, nextMinute)) {
                        status = RoomStatus.LEARNING;
                    } else {
                        status = RoomStatus.AVAILABLE;
                    }
                }
                roomDto.setStatus(status);

                roomDto.setScore(room.getScore());
                roomDto.setCapacity(room.getCapacity());
                roomDto.setXPosition(room.getXPosition());
                roomDto.setYPosition(room.getYPosition());
                roomDto.setWidth(room.getWidth());
                roomDto.setHeight(room.getHeight());
                roomDto.setPositioned(room.getPositioned() != null && room.getPositioned());

                floorResponse.getRooms().add(roomDto);
            }

            return RoomMapDashboardResponse.builder()
                    .buildingResponse(new ArrayList<>(buildingMap.values()))
                    .build();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves a summary of the dashboard, including total rooms, occupied rooms, broken rooms, and total users.
     *
     * @return DashboardSummaryResponse containing the summary data
     */

    /**
     * Retrieves an overview of the dashboard, including building occupancy and recent activity.
     *
     * @return DashboardOverviewResponse containing building occupancy and recent activity data
     */
    @Override
    public DashboardOverviewStatsResponse getOverviewStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfThisMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime startOfLastMonth = startOfThisMonth.minusMonths(1);
        LocalDateTime endOfLastMonth  = startOfThisMonth.minusNanos(1);

        // ── Total Bookings ───────────────────────────────────────────────────
        long currentMonthBookings = reservationRepository.countByCreateAtBetween(startOfThisMonth, now);
        long lastMonthBookings    = reservationRepository.countByCreateAtBetween(startOfLastMonth, endOfLastMonth);
        double bookingChange      = calculateChange(currentMonthBookings, lastMonthBookings);

        // ── Active Users ─────────────────────────────────────────────────────
        long currentMonthActiveUsers = reservationRepository.countDistinctUsersByCreateAtBetween(startOfThisMonth, now);
        long lastMonthActiveUsers    = reservationRepository.countDistinctUsersByCreateAtBetween(startOfLastMonth, endOfLastMonth);
        double activeUsersChange     = calculateChange(currentMonthActiveUsers, lastMonthActiveUsers);

        // ── Completed Bookings (thay utilizationRate) ────────────────────────
        List<com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus> completedStatuses =
                List.of(com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus.COMPLETED);
        long completedThisMonth = reservationRepository.countByStatusesAndCreateAtBetween(completedStatuses, startOfThisMonth, now);
        long completedLastMonth = reservationRepository.countByStatusesAndCreateAtBetween(completedStatuses, startOfLastMonth, endOfLastMonth);
        double completedChange  = calculateChange(completedThisMonth, completedLastMonth);

        // ── Cancellation Rate (%) ────────────────────────────────────────────
        List<com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus> cancelStatuses =
                List.of(com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus.CANCELLED,
                        com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus.FORCE_CANCELLED);
        long cancelledThisMonth = reservationRepository.countByStatusesAndCreateAtBetween(cancelStatuses, startOfThisMonth, now);
        long cancelledLastMonth = reservationRepository.countByStatusesAndCreateAtBetween(cancelStatuses, startOfLastMonth, endOfLastMonth);
        long totalThisMonth     = currentMonthBookings > 0 ? currentMonthBookings : 1;
        long totalLastMonth     = lastMonthBookings > 0 ? lastMonthBookings : 1;
        long cancelRatePct      = Math.round((double) cancelledThisMonth / totalThisMonth * 100);
        long cancelRateLastPct  = Math.round((double) cancelledLastMonth / totalLastMonth * 100);
        double cancelRateChange = calculateChange(cancelRatePct, cancelRateLastPct);

        // ── Today's Bookings ─────────────────────────────────────────────────
        long todaysBookings = reservationRepository.countTodaysReservations(now.toLocalDate().atStartOfDay());

        // ── No-Show Bookings ─────────────────────────────────────────────────
        List<com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus> noShowStatuses =
                List.of(com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus.NO_SHOW);
        long noShowThisMonth = reservationRepository.countByStatusesAndCreateAtBetween(noShowStatuses, startOfThisMonth, now);
        long noShowLastMonth = reservationRepository.countByStatusesAndCreateAtBetween(noShowStatuses, startOfLastMonth, endOfLastMonth);
        double noShowChange  = calculateChange(noShowThisMonth, noShowLastMonth);

        // ── Daily Trend (7 ngày gần nhất) — bar chart ────────────────────────
        LocalDateTime sevenDaysAgo = now.minusDays(6).toLocalDate().atStartOfDay();
        List<Object[]> rawDaily = reservationRepository.countByDaySince(sevenDaysAgo);

        // Build full 7-day map (fill zero cho ngày không có data)
        Map<String, Long> dailyMap = new java.util.LinkedHashMap<>();
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 6; i >= 0; i--) {
            dailyMap.put(LocalDate.now().minusDays(i).format(dateFmt), 0L);
        }
        for (Object[] row : rawDaily) {
            String day   = String.valueOf(row[0]).substring(0, 10); // "YYYY-MM-DD"
            long   count = ((Number) row[1]).longValue();
            if (dailyMap.containsKey(day)) {
                dailyMap.put(day, count);
            }
        }
        List<DashboardOverviewStatsResponse.DailyTrend> dailyTrend = dailyMap.entrySet().stream()
                .map(e -> new DashboardOverviewStatsResponse.DailyTrend(e.getKey(), e.getValue()))
                .toList();

        // ── Status Distribution (tháng này) — pie chart ─────────────────────
        List<Object[]> rawStatus = reservationRepository.countGroupByStatus(startOfThisMonth, now);
        long grandTotal = rawStatus.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        long gt = grandTotal > 0 ? grandTotal : 1;
        List<DashboardOverviewStatsResponse.StatusCount> statusDistribution = rawStatus.stream()
                .map(r -> new DashboardOverviewStatsResponse.StatusCount(
                        String.valueOf(r[0]),
                        ((Number) r[1]).longValue(),
                        Math.round((double) ((Number) r[1]).longValue() / gt * 1000) / 10.0))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .toList();

        return DashboardOverviewStatsResponse.builder()
                .totalBookings(new DashboardOverviewStatsResponse.StatItem(currentMonthBookings, bookingChange))
                .activeUsers(new DashboardOverviewStatsResponse.StatItem(currentMonthActiveUsers, activeUsersChange))
                .completedBookings(new DashboardOverviewStatsResponse.StatItem(completedThisMonth, completedChange))
                .cancellationRate(new DashboardOverviewStatsResponse.StatItem(cancelRatePct, cancelRateChange))
                .todaysBookings(new DashboardOverviewStatsResponse.StatItem(todaysBookings, 0))
                .noShowBookings(new DashboardOverviewStatsResponse.StatItem(noShowThisMonth, noShowChange))
                .dailyTrend(dailyTrend)
                .statusDistribution(statusDistribution)
                .build();
    }

    private double calculateChange(long current, long previous) {
        if (previous == 0) {
            return (current > 0) ? 1.0 : 0.0; // 100% increase if previous was 0 and current is > 0
        }
        return (double) (current - previous) / previous;
    }
    /**
     * Retrieves a list of all buildings with their basic information.
     *
     * @return List of AmbiguousBuildingResponse containing building details
     */
    @Override
    public List<AmbiguousBuildingResponse> getAllBuildings() {
        try {
            return buildingRepository.findAllBuildings();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves a list of all floors for a given building ID.
     *
     * @param buildingId the ID of the building to retrieve floors for
     * @return List of AmbiguousFloorResponse containing floor details
     */
    @Override
    public List<AmbiguousFloorResponse> getAllFloorsByBuildingId(String buildingId) {
        try {
            return floorRepository.findAllFloorsByBuildingId(buildingId);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves all rooms for a given floor ID.
     *
     * @param floorId the ID of the floor to retrieve rooms for
     * @return List of RoomDto containing room details
     */
    @Override
    public List<RoomDto> getAllRoomsByFloorId(String floorId) {
        try {
            Floor floor = floorRepository.findById(floorId)
                    .orElseThrow(() -> new CustomException(ResponseCode.FLOOR_NOT_FOUND));

            List<Room> rooms = roomRepository.findByFloorOrderByLocationCode(floor);

            return rooms.stream()
                    .map(room -> {
                         RoomDto dto = new RoomDto();
                         dto.setId(room.getId());
                         dto.setLocationCode(room.getLocationCode());

                        // [HYBRID] Cập nhật trạng thái động cho danh sách phòng của Admin
                        RoomStatus status = room.getStatus();
                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime nextMinute = now.plusMinutes(1);

                        // Check for reservation conflict first
                        boolean hasReservationConflict = reservationRepository
                                .findOverlappingReservations(room.getId(), now, nextMinute)
                                .stream()
                                .anyMatch(res ->
                                        res.getStatus() == com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus.IN_USE ||
                                                res.getStatus() == com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus.RESERVED
                                );

                        if (status == RoomStatus.AVAILABLE) {
                            if (hasReservationConflict) {
                                status = RoomStatus.UNAVAILABLE;
                            } else if (academicScheduleService.isRoomBusyWithLearning(room.getId(), now, nextMinute)) {
                                status = RoomStatus.LEARNING;
                            }
                        } else if (status == RoomStatus.UNAVAILABLE && !hasReservationConflict) {
                            if (academicScheduleService.isRoomBusyWithLearning(room.getId(), now, nextMinute)) {
                                status = RoomStatus.LEARNING;
                            } else {
                                status = RoomStatus.AVAILABLE;
                            }
                        }
                        dto.setStatus(status);

                        dto.setScore(room.getScore());
                        dto.setCapacity(room.getCapacity());
                        dto.setXPosition(room.getXPosition());
                        dto.setYPosition(room.getYPosition());
                        dto.setWidth(room.getWidth());
                        dto.setHeight(room.getHeight());
                        dto.setPositioned(room.getPositioned() != null && room.getPositioned());

                        if (room.getAmenities() != null) {
                            dto.setAmenities(room.getAmenities().stream()
                                    .map(a -> new RoomDto.AmenityDto(a.getId(), a.getName()))
                                    .toList());
                        }

                        return dto;
                    }).toList();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error fetching rooms by floor: " + e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Page<UserDashboardResponse> getAllUsers(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            var users = userRepository.findAll(pageable);

            return users.map(user -> UserDashboardResponse.builder()
                    .id(user.getId())
                    .fullName(user.getUserInfo() != null ? user.getUserInfo().getFullName() : null)
                    .email(user.getUserInfo() != null ? user.getUserInfo().getEmail() : null)
                    .phoneNumber(user.getUserInfo() != null ? user.getUserInfo().getPhoneNumber() : null)
                    .department(user.getUserInfo() != null ? user.getUserInfo().getDepartment() : null)
                    .enabled(user.isEnabled())
                    .isLocked(user.isLocked())
                    .build());
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public void lockUser(String userId) {
        try {
            var user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
            user.setLocked(true);
            userRepository.save(user);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public void unlockUser(String userId) {
        try {
            var user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
            user.setLocked(false);
            userRepository.save(user);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public void createBuilding(BuildingCreateRequest request) {
        try {
            Building building = new Building();
            building.setId(UUID.randomUUID().toString());
            building.setName(request.getName());
            building.setAddress(request.getAddress());
            building.setDeleted(false);
            building.setCreatedAt(LocalDateTime.now());
            building.setUpdatedAt(LocalDateTime.now());
            buildingRepository.save(building);

            for (int i = 1; i <= request.getTotalFloors(); i++) {
                Floor floor = new Floor();
                floor.setId(UUID.randomUUID().toString());
                floor.setName("Floor " + i);
                floor.setBuilding(building);
                floor.setDeleted(false);
                floor.setCreateAt(LocalDateTime.now());
                floor.setUpdatedAt(LocalDateTime.now());
                floorRepository.save(floor);
            }
        } catch (Exception e) {
            logger.error("Error creating building: " + e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public void updateBuilding(String buildingId, BuildingUpdateRequest request) {
        try {
            Building building = buildingRepository.findById(buildingId)
                    .orElseThrow(() -> new CustomException(ResponseCode.BUILDING_NOT_FOUND));
            building.setName(request.getName());
            building.setUpdatedAt(LocalDateTime.now());
            buildingRepository.save(building);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error updating building: " + e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public void createFloor(FloorCreateRequest request) {
        try {
            Building building = buildingRepository.findById(request.getBuildingId())
                    .orElseThrow(() -> new CustomException(ResponseCode.BUILDING_NOT_FOUND));

            // Logic mới: Tự động đếm số tầng hiện có để đặt tên (ví dụ Floor 3 -> Floor 4)
            List<AmbiguousFloorResponse> existingFloors = floorRepository.findAllFloorsByBuildingId(request.getBuildingId());
            int nextFloorNumber = existingFloors.size() + 1;
            String floorName = "Floor " + nextFloorNumber;

            Floor floor = new Floor();
            floor.setId(UUID.randomUUID().toString());
            floor.setName(floorName);
            floor.setBuilding(building);
            floor.setDeleted(false);
            floor.setCreateAt(LocalDateTime.now());
            floor.setUpdatedAt(LocalDateTime.now());
            floorRepository.save(floor);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error creating floor: " + e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
    // end implement createBuilding

    @Override
    public BuildingResponse getBuildingById(String buildingId) {
        try {
            var building = buildingRepository.findByIdAndIsDeleted(buildingId, false);
            if (building == null) {
                throw new CustomException(ResponseCode.BUILDING_NOT_FOUND);
            }

            List<AdminFloorDto> floorDtos = floorRepository.findFloorByBuildingIdAndDeleted(building.getId());
            BuildingResponse response = new BuildingResponse();
            response.setName(building.getName());
            response.setAddress(building.getAddress());
            response.setFloors(floorDtos);

            return response;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Page<AdminBuildingDto> getAllBuilding(int pageNum, int pageSize) {
        try {
            Pageable pageable = PageRequest.of(pageNum, pageSize);

            return buildingRepository.findAllBuilding(pageable);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void deleteBuilding(String buildingId) {
        try {
            var building = buildingRepository.findByIdAndIsDeleted(buildingId, false);
            if (building == null) {
                throw new CustomException(ResponseCode.BUILDING_NOT_FOUND);
            }
            building.setDeleted(true);
            building.setUpdatedAt(LocalDateTime.now());

            buildingRepository.save(building);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

}