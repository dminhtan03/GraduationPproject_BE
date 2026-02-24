package com.finalProject.BookingMeetingRoom.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomSearchRequest {
    @NotNull
    private String buildingId;
    @NotNull private String floorId;
    @NotNull private LocalDateTime startTime;
    @NotNull private LocalDateTime endTime;
}
