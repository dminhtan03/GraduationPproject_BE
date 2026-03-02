package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.model.response.RoomSearchResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private String reply;                 // natural language reply from AI
    private List<RoomSearchResponse> suggestions; // suggested rooms if any
    private boolean reservationCreated;   // whether a reservation was created
    private ReservationResponse reservation; // info about created reservation
}

