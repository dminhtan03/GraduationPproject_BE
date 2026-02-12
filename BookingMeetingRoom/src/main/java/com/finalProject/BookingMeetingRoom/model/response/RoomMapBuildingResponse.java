package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class RoomMapBuildingResponse {
    private String buildingId;
    private String buildingName;
    private String address;
    private List<DetailFloorResponse> floors;
}