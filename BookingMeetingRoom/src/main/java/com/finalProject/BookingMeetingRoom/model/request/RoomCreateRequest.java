package com.finalProject.BookingMeetingRoom.model.request;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomCreateRequest {

    @NotBlank(message = "Location code is required")
    private String locationCode;

    @NotNull(message = "Status is required")
    private RoomStatus status;

    @NotNull(message = "Capacity is required")
    private Integer capacity;

    private Double score;

    @NotBlank(message = "Floor ID is required")
    private String floorId;

    private List<String> amenityIds;
}
