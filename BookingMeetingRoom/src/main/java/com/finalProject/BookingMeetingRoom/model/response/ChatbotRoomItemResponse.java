package com.finalProject.BookingMeetingRoom.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotRoomItemResponse {
    private String roomId;
    private String roomCode;
    private String building;
    private String floor;
    private Integer capacity;
    private List<String> amenities;
    private String imageUrl;

    // Free slots (for today) or suggested slot (for alternatives)
    private List<String> availableTimeSlots;
}
