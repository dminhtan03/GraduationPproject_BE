package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.model.dto.AdminFloorDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BuildingResponse {
    private String name;
    private String address;
    private List<AdminFloorDto> floors;
}