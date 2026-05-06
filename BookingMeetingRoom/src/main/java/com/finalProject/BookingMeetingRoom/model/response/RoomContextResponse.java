package com.finalProject.BookingMeetingRoom.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomContextResponse {
    private String roomId;
    private String locationCode;
    private String floorId;
    private String buildingId;
}