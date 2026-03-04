package com.finalProject.BookingMeetingRoom.model.request;


import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReservationStatusUpdateRequest {
    private String userId;
    private String reservationId;
    private ReservationStatus newStatus;
}

