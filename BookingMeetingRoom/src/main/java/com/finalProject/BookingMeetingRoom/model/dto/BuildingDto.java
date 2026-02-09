package com.finalProject.BookingMeetingRoom.model.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class BuildingDto {
    private String id;
    private String name;
    private String address;
    private boolean isDeleted;
}