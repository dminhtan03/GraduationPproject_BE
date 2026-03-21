package com.finalProject.BookingMeetingRoom.service;

import java.util.List;

import org.springframework.data.domain.Page;

import com.finalProject.BookingMeetingRoom.model.dto.RoomDto;
import com.finalProject.BookingMeetingRoom.model.request.BuildingCreateRequest;
import com.finalProject.BookingMeetingRoom.model.response.AmbiguousBuildingResponse;
import com.finalProject.BookingMeetingRoom.model.response.AmbiguousFloorResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomMapDashboardResponse;
import com.finalProject.BookingMeetingRoom.model.response.UserDashboardResponse;

public interface DashboardService {
    RoomMapDashboardResponse getRoomsMapDashboard();
    List<AmbiguousBuildingResponse> getAllBuildings();
    List<AmbiguousFloorResponse> getAllFloorsByBuildingId(String buildingId);
    List<RoomDto> getAllRoomsByFloorId(String buildingId);

    Page<UserDashboardResponse> getAllUsers(int page, int size);
    void lockUser(String userId);
    void unlockUser(String userId);

    // start add createBuilding method
    void createBuilding(BuildingCreateRequest request);
    // end add createBuilding method
}