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
}