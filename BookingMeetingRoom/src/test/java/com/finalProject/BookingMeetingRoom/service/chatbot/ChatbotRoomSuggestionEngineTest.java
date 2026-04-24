package com.finalProject.BookingMeetingRoom.service.chatbot;

import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.impl.ChatbotRoomSuggestionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ChatbotRoomSuggestionEngineTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private ChatbotRoomSuggestionEngine engine;

    @Test
    void suggest_shouldReturnEmpty_whenOriginalRoomIsNull() {
        assertTrue(engine.suggest(null, null, null, 5).isEmpty());
    }
}
