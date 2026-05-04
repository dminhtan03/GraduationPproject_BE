package com.finalProject.BookingMeetingRoom.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// start+ chức năng đặt thêm dịch vụ đi kèm khi đặt phòng (response)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationServiceItemResponse {
    private String id;
    private String serviceItemId;
    private String name;
    private String unit;
    private Double priceSnapshot;
    private Integer quantity;
    private String note;
    // start+ chức năng service item status
    private String status;
    // end+ chức năng service item status
}
// end+ chức năng đặt thêm dịch vụ đi kèm khi đặt phòng (response)
