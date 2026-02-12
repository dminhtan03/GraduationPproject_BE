package com.finalProject.BookingMeetingRoom.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AmbiguousFloorResponse {
    private String id;
    private String name;
}