package com.finalProject.BookingMeetingRoom.model.dto;

import lombok.AllArgsConstructor;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RoomDto {
    private String roomId;
    private String locationCode;
    private RoomStatus status;
    private Double score;

    // start add layout fields
    private Double xPosition;
    private Double yPosition;
    private Double width;
    private Double height;
    private boolean positioned;
    // end add layout fields
}