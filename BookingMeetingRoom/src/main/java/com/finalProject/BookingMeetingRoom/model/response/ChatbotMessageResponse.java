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
    private ChatbotIntent intent;

    // When intent is CHECK_AVAILABLE_ROOMS_TODAY
    private List<ChatbotRoomItemResponse> availableRooms;

    // When booking conflicts
    private List<ChatbotRoomItemResponse> alternativeRooms;

    // If a booking is created successfully
    private ReservationResponse reservation;
}
