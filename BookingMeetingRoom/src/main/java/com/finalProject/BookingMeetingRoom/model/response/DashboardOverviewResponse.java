package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.model.dto.BuildingOccupancyDto;
import com.finalProject.BookingMeetingRoom.model.dto.RecentActivityDto;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class DashboardOverviewResponse {
    private List<BuildingOccupancyDto> buildingOccupancyDto;
    private List<RecentActivityDto> recentActivityDto;
}