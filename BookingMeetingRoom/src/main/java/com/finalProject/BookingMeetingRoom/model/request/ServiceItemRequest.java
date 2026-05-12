package com.finalProject.BookingMeetingRoom.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// start+ chức năng CRUD dịch vụ đi kèm (request)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceItemRequest {
    @NotBlank(message = "Service item name is required")
    private String name;
    private String description;
    private String unit;
    private Double price;
    private Boolean active;
}
// end+ chức năng CRUD dịch vụ đi kèm (request)
