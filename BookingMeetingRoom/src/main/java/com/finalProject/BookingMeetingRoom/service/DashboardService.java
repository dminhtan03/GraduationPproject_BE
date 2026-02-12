package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.dto.RoomDto;
import com.finalProject.BookingMeetingRoom.model.response.AmbiguousBuildingResponse;
import com.finalProject.BookingMeetingRoom.model.response.AmbiguousFloorResponse;
import com.finalProject.BookingMeetingRoom.model.response.DashboardOverviewResponse;
import com.finalProject.BookingMeetingRoom.model.response.DashboardSummaryResponse;
import com.finalProject.BookingMeetingRoom.model.response.EmployeeDashboardResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomMapDashboardResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface DashboardService {
    RoomMapDashboardResponse getRoomsMapDashboard();
    List<AmbiguousBuildingResponse> getAllBuildings();
    List<AmbiguousFloorResponse> getAllFloorsByBuildingId(String buildingId);
    List<RoomDto> getAllRoomsByFloorId(String buildingId);
}