package com.finalProject.BookingMeetingRoom.model.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// start+ chức năng sự kiện (gọi thêm dịch vụ/tiện ích trong lúc diễn ra) - cập nhật service items của reservation
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationServiceItemsUpdateRequest {
    @Valid
    private List<ServiceItemLineRequest> serviceItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceItemLineRequest {
        @NotNull
        private String serviceItemId;
        @NotNull
        private Integer quantity;
        private String note;
    }
}
// end+ chức năng sự kiện (gọi thêm dịch vụ/tiện ích trong lúc diễn ra) - cập nhật service items của reservation

