package com.finalProject.BookingMeetingRoom.service;

import java.util.List;

import com.finalProject.BookingMeetingRoom.model.dto.AdminBuildingDto;
import com.finalProject.BookingMeetingRoom.model.response.*;
import org.springframework.data.domain.Page;

import com.finalProject.BookingMeetingRoom.model.dto.RoomDto;
import com.finalProject.BookingMeetingRoom.model.request.BuildingCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.BuildingUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.request.FloorCreateRequest;

public interface DashboardService {
    DashboardOverviewStatsResponse getOverviewStats();
    RoomMapDashboardResponse getRoomsMapDashboard();
    List<AmbiguousBuildingResponse> getAllBuildings();
    List<AmbiguousFloorResponse> getAllFloorsByBuildingId(String buildingId);
    List<RoomDto> getAllRoomsByFloorId(String buildingId);

    Page<UserDashboardResponse> getAllUsers(int page, int size);
    void lockUser(String userId);
    void unlockUser(String userId);

    // start add createBuilding method
    void createBuilding(BuildingCreateRequest request);
    void updateBuilding(String buildingId, BuildingUpdateRequest request);
    void createFloor(FloorCreateRequest request);
    // end add createBuilding method

    Page<AdminBuildingDto> getAllBuilding(int pageNum, int pageSize);
    BuildingResponse getBuildingById(String buildingId);
    void deleteBuilding(String buildingId);
}