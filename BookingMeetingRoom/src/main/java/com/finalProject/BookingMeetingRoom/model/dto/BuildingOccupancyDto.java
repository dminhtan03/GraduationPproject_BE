package com.finalProject.BookingMeetingRoom.model.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BuildingOccupancyDto {
    private String buildingName;
    private int occupied;
    private int totalRooms;
    private int brokenRooms;
    private int availableRooms;
    private int occupancyRate;
}