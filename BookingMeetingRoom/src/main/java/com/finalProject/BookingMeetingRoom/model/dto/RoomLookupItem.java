package com.finalProject.BookingMeetingRoom.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomLookupItem {
    private String roomId;
    private String locationCode;
    private String buildingId;
    private String buildingName;
    private String floorId;
    private String floorName;
    private Integer capacity;
}
