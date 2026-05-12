package com.finalProject.BookingMeetingRoom.model.request;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// start add RoomUpdateRequest
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomUpdateRequest {
    @NotBlank(message = "Room ID is required")
    private String roomId;

    private Integer capacity;

    private RoomStatus status;

    private List<String> amenityIds;
}
// end add RoomUpdateRequest
