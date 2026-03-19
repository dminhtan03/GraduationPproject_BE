package com.finalProject.BookingMeetingRoom.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorLayoutRequest {
    private List<RoomLayoutItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomLayoutItem {
        private String roomId;
        private Double x;
        private Double y;
        private Double width;
        private Double height;
    }
}
