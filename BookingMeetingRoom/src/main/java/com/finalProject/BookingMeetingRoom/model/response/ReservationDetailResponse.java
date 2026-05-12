package com.finalProject.BookingMeetingRoom.model.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDetailResponse {
    private ReservationResponse reservation;
    private List<RoomImageResponse> roomImages;
    private List<ReservationHistoryResponse> history;
    private FeedbackResponse feedback;

    // start+ chức năng đặt thêm dịch vụ đi kèm khi đặt phòng
    private List<ReservationServiceItemResponse> serviceItems;
    // end+ chức năng đặt thêm dịch vụ đi kèm khi đặt phòng
}
