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
    private int totalSeats;
    private int brokenSeats;
    private int availableSeats;
    private int occupancyRate;
}