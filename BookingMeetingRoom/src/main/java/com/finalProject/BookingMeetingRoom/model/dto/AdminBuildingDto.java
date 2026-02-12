package com.finalProject.BookingMeetingRoom.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AdminBuildingDto {
    private String id;
    private String name;
    private String address;
    private int totalFloors;
}