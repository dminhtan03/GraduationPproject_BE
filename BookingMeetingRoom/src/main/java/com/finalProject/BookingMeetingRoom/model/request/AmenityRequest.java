package com.finalProject.BookingMeetingRoom.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmenityRequest {
    @NotBlank(message = "Amenity name is required")
    private String name;
}
