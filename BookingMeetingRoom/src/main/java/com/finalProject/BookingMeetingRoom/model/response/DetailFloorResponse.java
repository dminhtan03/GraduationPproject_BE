package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.model.dto.RoomDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DetailFloorResponse {

    private String floorId;

    private String floorName;

    List<RoomDto> rooms;
}