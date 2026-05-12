package com.finalProject.BookingMeetingRoom.model.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRequest {
    @NotNull
    private String roomId;

    @NotNull
    private LocalDateTime startTime;

    @NotNull
    private LocalDateTime endTime;

    @NotNull
    private String purpose;

    private String note;

    // start+ chức năng đặt thêm dịch vụ đi kèm khi đặt phòng
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceItemLineRequest {
        @NotNull
        private String serviceItemId;
        @NotNull
        private Integer quantity;
        private String note;
    }

    @Valid
    private List<ServiceItemLineRequest> serviceItems;
    // end+ chức năng đặt thêm dịch vụ đi kèm khi đặt phòng
}
