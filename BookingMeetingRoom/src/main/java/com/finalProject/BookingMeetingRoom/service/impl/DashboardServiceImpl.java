package com.finalProject.BookingMeetingRoom.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.finalProject.BookingMeetingRoom.model.projection.RoomDtoProjection;
import com.finalProject.BookingMeetingRoom.model.response.AmbiguousBuildingResponse;
import com.finalProject.BookingMeetingRoom.model.response.AmbiguousFloorResponse;
import com.finalProject.BookingMeetingRoom.model.response.DetailFloorResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomMapBuildingResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomMapDashboardResponse;
import com.finalProject.BookingMeetingRoom.model.response.UserDashboardResponse;
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
                roomDto.setRoomId(room.getRoomId());
                roomDto.setLocationCode(room.getLocationCode());
                roomDto.setStatus(room.getStatus());
                roomDto.setScore(room.getScore());

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
            List<RoomDtoProjection> seats = roomRepository.findRooms(floorId);

            return seats.stream()
                    .map(seat -> new RoomDto(
                            seat.getRoomId(),
                            seat.getLocationCode(),
                            seat.getStatus(),
                            seat.getScore()
                    )).toList();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
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

}