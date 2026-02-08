package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;

import com.finalProject.BookingMeetingRoom.model.dto.BuildingDto;
import com.finalProject.BookingMeetingRoom.model.dto.FloorDto;
import com.finalProject.BookingMeetingRoom.model.dto.RoomDto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReservationResponse {
    private String id;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private ReservationStatus status;

    private LocalDateTime checkinTime;

    private LocalDateTime returnTime;

    private LocalDateTime createAt;

    private LocalDateTime updatedAt;

    private RoomDto room;

    private FloorDto floor;

    private BuildingDto building;

    private boolean feedbacked;
}
