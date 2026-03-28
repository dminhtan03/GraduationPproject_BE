package com.finalProject.BookingMeetingRoom.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.dto.RoomDto;
import com.finalProject.BookingMeetingRoom.model.entity.Building;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.projection.RoomDtoProjection;
import com.finalProject.BookingMeetingRoom.model.request.BuildingCreateRequest;
import com.finalProject.BookingMeetingRoom.model.response.AmbiguousBuildingResponse;
import com.finalProject.BookingMeetingRoom.model.response.AmbiguousFloorResponse;
import com.finalProject.BookingMeetingRoom.model.response.DetailFloorResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomMapBuildingResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomMapDashboardResponse;
import com.finalProject.BookingMeetingRoom.model.response.UserDashboardResponse;
import com.finalProject.BookingMeetingRoom.model.response.DashboardOverviewStatsResponse;
import com.finalProject.BookingMeetingRoom.repository.BuildingRepository;
import com.finalProject.BookingMeetingRoom.repository.FloorRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
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

    /**
     * Retrieves the employee dashboard data including active reservations, last checked-in details, and hours worked.
     *
     * @param connectedUser the authenticated user
     * @return EmployeeDashboardResponse containing active reservations, last checked-in details, and hours worked
     */

    /**
     * Retrieves the seat map dashboard data, organizing seat information by building and floor.
     * Logic:
     * 1. Fetches a flat list of seat data from the repository.
     * 2. Iterates through each seat, grouping them by buildingId using a LinkedHashMap.
     * 3. For each building, checks if the floor exists; if not, creates a new floor entry.
     * 4. For each seat, creates a SeatDto and adds it to the corresponding floor's seat list.
     * 5. After grouping, builds and returns a SeatMapDashboardResponse containing the structured building, floor, and seat data.
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
                roomDto.setStatus(room.getStatus());
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
     * Retrieves a summary of the dashboard, including total seats, occupied seats, broken seats, and total users.
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
        LocalDateTime endOfLastMonth = startOfThisMonth.minusNanos(1);

        // Total Bookings
        long currentMonthBookings = reservationRepository.countByCreateAtBetween(startOfThisMonth, now);
        long lastMonthBookings = reservationRepository.countByCreateAtBetween(startOfLastMonth, endOfLastMonth);
        double bookingChange = calculateChange(currentMonthBookings, lastMonthBookings);

        // Active Users
        long currentMonthActiveUsers = reservationRepository.countDistinctUsersByCreateAtBetween(startOfThisMonth, now);
        long lastMonthActiveUsers = reservationRepository.countDistinctUsersByCreateAtBetween(startOfLastMonth, endOfLastMonth);
        double activeUsersChange = calculateChange(currentMonthActiveUsers, lastMonthActiveUsers);

        // Utilization Rate
        long totalRooms = roomRepository.count();
        long occupiedRooms = roomRepository.countByStatus(RoomStatus.UNAVAILABLE);
        double utilizationRate = (totalRooms > 0) ? (double) occupiedRooms / totalRooms : 0.0;
        // For simplicity, change for utilization is not calculated in this example.

        // Today's Bookings
        long todaysBookings = reservationRepository.countTodaysReservations(now.toLocalDate().atStartOfDay());

        return DashboardOverviewStatsResponse.builder()
                .totalBookings(new DashboardOverviewStatsResponse.StatItem(currentMonthBookings, bookingChange))
                .activeUsers(new DashboardOverviewStatsResponse.StatItem(currentMonthActiveUsers, activeUsersChange))
                .utilizationRate(new DashboardOverviewStatsResponse.StatItem((long) (utilizationRate * 100), 0))
                .todaysBookings(new DashboardOverviewStatsResponse.StatItem(todaysBookings, 0))
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
     * Retrieves all seats for a given floor ID.
     *
     * @param floorId the ID of the floor to retrieve seats for
     * @return List of SeatDto containing seat details
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
                        dto.setStatus(room.getStatus());
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

    // start implement createBuilding
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
    // end implement createBuilding
}