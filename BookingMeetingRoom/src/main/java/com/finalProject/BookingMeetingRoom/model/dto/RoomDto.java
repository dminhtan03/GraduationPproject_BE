package com.finalProject.BookingMeetingRoom.model.dto;

import java.util.List;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RoomDto {
    private String id;
    private String locationCode;
    private RoomStatus status;
    private Double score;
    private Integer capacity;
    private List<AmenityDto> amenities;

    @Setter
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AmenityDto {
        private String id;
        private String name;
    }

    // start add layout fields
    private Double xPosition;
    private Double yPosition;
    private Double width;
    private Double height;
    private boolean positioned;
    // end add layout fields
}