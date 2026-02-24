package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomSearchResponse {
    private String seatId;
    private String locationCode;
    private Double score;
    private RoomStatus status;
}
