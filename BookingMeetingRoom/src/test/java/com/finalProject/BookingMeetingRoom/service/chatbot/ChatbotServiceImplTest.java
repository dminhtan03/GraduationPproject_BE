package com.finalProject.BookingMeetingRoom.service.chatbot;

import com.finalProject.BookingMeetingRoom.common.enums.ChatbotIntent;
import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import com.finalProject.BookingMeetingRoom.model.request.ChatbotMessageRequest;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotMessageResponse;
import com.finalProject.BookingMeetingRoom.repository.BuildingRepository;
import com.finalProject.BookingMeetingRoom.repository.FloorRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomImageRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.ChatHistoryService;
import com.finalProject.BookingMeetingRoom.service.ChatbotLlmService;
import com.finalProject.BookingMeetingRoom.service.ReservationService;
import com.finalProject.BookingMeetingRoom.service.RoomService;
import com.finalProject.BookingMeetingRoom.service.impl.ChatbotRoomSuggestionEngine;
import com.finalProject.BookingMeetingRoom.service.impl.ChatbotServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceImplTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private FloorRepository floorRepository;
    @Mock
    private RoomImageRepository roomImageRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationService reservationService;
    @Mock
    private ChatbotRoomSuggestionEngine suggestionEngine;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatHistoryService chatHistoryService;
    @Mock
    private ChatbotLlmService chatbotLlmService;
    @Mock
    private RoomService roomService;

    @InjectMocks
    private ChatbotServiceImpl service;

    @Test
    void handleMessage_shouldReturnFallback_whenMessageIsBlank() {
        ChatbotMessageRequest request = new ChatbotMessageRequest();
        request.setSessionId(null);
        request.setMessage(" ");

        when(chatHistoryService.ensureSessionId(null)).thenReturn("s-1");
        when(chatHistoryService.getRecentMessages("s-1", SenderType.USER, 5)).thenReturn(List.of());

        ChatbotMessageResponse response = service.handleMessage(request, null);

        assertEquals(ChatbotIntent.FALLBACK, response.getIntent());
        assertTrue(response.getReply().contains("Please choose a function"));
        assertTrue(response.getMenuOptions() != null && response.getMenuOptions().size() == 4);
        assertEquals("s-1", response.getSessionId());
    }
}
