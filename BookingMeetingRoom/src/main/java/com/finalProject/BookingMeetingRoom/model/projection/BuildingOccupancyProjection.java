package com.finalProject.BookingMeetingRoom.model.projection;

public interface BuildingOccupancyProjection {
    String getBuildingName();
    int getOccupied();
    int getTotalRooms();
    int getBrokenRooms();
    int getAvailableRooms();
    int getOccupancyRate();
}