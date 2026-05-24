package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.common.enums.ChatbotIntent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotMessageResponse {
    private String sessionId;
    private String reply;
    private String message;
    private ChatbotIntent intent;

    // Danh sach menu goi y (tuy chon)
    private List<ChatbotMenuOptionResponse> menuOptions;

    // Khi intent la CHECK_AVAILABLE_ROOMS_TODAY
    private List<ChatbotRoomItemResponse> availableRooms;

    // Khi dat phong bi trung
    private List<ChatbotRoomItemResponse> alternativeRooms;

    // Khi tao dat phong thanh cong
    private ReservationResponse reservation;

    // Danh sach lua chon cho luong huy
    private List<ChatbotBookingItemResponse> items;

    // Khi intent la VIEW_FACILITY_DETAILS cho mot phong cu the
    private RoomDetailResponse roomDetail;
}
