package com.finalProject.BookingMeetingRoom.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AdminFloorDto {
    private String id;
    private String floorName;
    private String buildingId;
}