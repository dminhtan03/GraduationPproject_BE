package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import lombok.Data;

import java.util.List;

@Data
public class SendNoticeRequest {
    private List<Reservation> reservationList;

    private String title;

    private String content;

    private boolean sendEmail;
}
