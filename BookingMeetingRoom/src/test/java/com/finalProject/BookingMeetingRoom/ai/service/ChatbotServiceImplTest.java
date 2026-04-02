package com.finalProject.BookingMeetingRoom.ai.service;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Building;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.ChatbotMessageRequest;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomImageRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.ChatHistoryService;
import com.finalProject.BookingMeetingRoom.service.ReservationService;
import com.finalProject.BookingMeetingRoom.service.impl.ChatbotRoomSuggestionEngine;
import com.finalProject.BookingMeetingRoom.service.impl.ChatbotServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class ChatbotServiceImplTest {

    @Test
    void shouldLogUserAndBotMessagesToChatHistory() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationService reservationService = mock(ReservationService.class);
        ChatbotRoomSuggestionEngine suggestionEngine = mock(ChatbotRoomSuggestionEngine.class);
        UserRepository userRepository = mock(UserRepository.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);

        when(chatHistoryService.ensureSessionId(any())).thenReturn("S1");
        when(chatHistoryService.getRecentMessages(anyString(), any(), anyInt())).thenReturn(List.of());

        ChatbotServiceImpl svc = new ChatbotServiceImpl(
                roomRepository,
                roomImageRepository,
                reservationRepository,
                reservationService,
                suggestionEngine,
                userRepository,
                chatHistoryService
        );

        var auth = new TestingAuthenticationToken("user@example.com", "pw");
        svc.handleMessage(new ChatbotMessageRequest("Today available rooms?", null), auth);

        verify(chatHistoryService, times(2)).log(any(), eq("S1"), any(), any());
    }

    @Test
    void bookingConflictShouldReturnAlternatives() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationService reservationService = mock(ReservationService.class);
        ChatbotRoomSuggestionEngine suggestionEngine = mock(ChatbotRoomSuggestionEngine.class);
        UserRepository userRepository = mock(UserRepository.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);

        when(chatHistoryService.ensureSessionId(any())).thenReturn("S1");
        when(chatHistoryService.getRecentMessages(anyString(), any(), anyInt())).thenReturn(List.of());

        ChatbotServiceImpl svc = new ChatbotServiceImpl(
            roomRepository,
            roomImageRepository,
            reservationRepository,
            reservationService,
            suggestionEngine,
            userRepository,
            chatHistoryService
        );

        Building b = new Building(); b.setId("B1"); b.setName("Alpha");
        Floor f = new Floor(); f.setId("F1"); f.setName("1"); f.setBuilding(b);

        Room room = new Room();
        room.setId("R0");
        room.setLocationCode("AL-102");
        room.setCapacity(8);
        room.setFloor(f);

        Room alt = new Room();
        alt.setId("R1");
        alt.setLocationCode("AL-105");
        alt.setCapacity(8);
        alt.setFloor(f);

        when(roomRepository.findByLocationCodeIgnoreCase("AL-102")).thenReturn(Optional.of(room));
        when(reservationService.reserveRoom(any(), any())).thenThrow(new CustomException(ResponseCode.CANNOT_RESERVE_ROOM));
        when(suggestionEngine.suggest(eq(room), any(LocalDateTime.class), any(LocalDateTime.class), eq(5)))
                .thenReturn(List.of(alt));

        var auth = new TestingAuthenticationToken("user@example.com", "pw");
        var res = svc.handleMessage(new ChatbotMessageRequest("Book AL-102 from 14:00 to 15:00 today", null), auth);

        assertNotNull(res);
        assertNull(res.getReservation());
        assertNotNull(res.getAlternativeRooms());
        assertEquals(1, res.getAlternativeRooms().size());
        assertEquals("AL-105", res.getAlternativeRooms().get(0).getRoomCode());
    }

        @Test
        void shouldAutoBookByCapacityTomorrowAt10am() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationService reservationService = mock(ReservationService.class);
        ChatbotRoomSuggestionEngine suggestionEngine = mock(ChatbotRoomSuggestionEngine.class);
        UserRepository userRepository = mock(UserRepository.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);

        when(chatHistoryService.ensureSessionId(any())).thenReturn("S1");
        when(chatHistoryService.getRecentMessages(anyString(), any(), anyInt())).thenReturn(List.of());

        ChatbotServiceImpl svc = new ChatbotServiceImpl(
            roomRepository,
            roomImageRepository,
            reservationRepository,
            reservationService,
            suggestionEngine,
            userRepository,
            chatHistoryService
        );

        Room r10 = new Room();
        r10.setId("R10");
        r10.setLocationCode("AL-010");
        r10.setCapacity(10);

        Room r20 = new Room();
        r20.setId("R20");
        r20.setLocationCode("AL-020");
        r20.setCapacity(20);

        Room r25 = new Room();
        r25.setId("R25");
        r25.setLocationCode("AL-025");
        r25.setCapacity(25);

        when(roomRepository.findAllWithDetails()).thenReturn(List.of(r10, r20, r25));
        when(reservationRepository.findOverlappingReservationsForRooms(anyList(), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of());

        var reservationResponse = mock(com.finalProject.BookingMeetingRoom.model.response.ReservationResponse.class);
        when(reservationService.reserveRoom(any(), any())).thenReturn(reservationResponse);

        var auth = new TestingAuthenticationToken("user@example.com", "pw");
        var res = svc.handleMessage(new ChatbotMessageRequest(
            "Book a room with a capacity of 20 people for tomorrow at 10AM.",
            null
        ), auth);

        assertNotNull(res);
        assertNotNull(res.getReservation());
        assertTrue(res.getReply().contains("AL-020"));

        ArgumentCaptor<com.finalProject.BookingMeetingRoom.model.request.ReservationRequest> cap = ArgumentCaptor.forClass(com.finalProject.BookingMeetingRoom.model.request.ReservationRequest.class);
        verify(reservationService).reserveRoom(cap.capture(), any());

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        assertEquals("R20", cap.getValue().getRoomId());
        assertEquals(LocalDateTime.of(tomorrow, LocalTime.of(10, 0)), cap.getValue().getStartTime());
        assertEquals(LocalDateTime.of(tomorrow, LocalTime.of(11, 0)), cap.getValue().getEndTime());
        }

            @Test
            void shouldRememberContext_capacityThenTime() {
            RoomRepository roomRepository = mock(RoomRepository.class);
            RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
            ReservationRepository reservationRepository = mock(ReservationRepository.class);
            ReservationService reservationService = mock(ReservationService.class);
            ChatbotRoomSuggestionEngine suggestionEngine = mock(ChatbotRoomSuggestionEngine.class);
            UserRepository userRepository = mock(UserRepository.class);
            ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);

            when(chatHistoryService.ensureSessionId(any())).thenReturn("S1");
            // Previous message (context) contains the capacity booking intent.
            when(chatHistoryService.getRecentMessages(eq("S1"), any(), anyInt()))
                .thenReturn(List.of("Book a room with a capacity of 20 people"));

            ChatbotServiceImpl svc = new ChatbotServiceImpl(
                roomRepository,
                roomImageRepository,
                reservationRepository,
                reservationService,
                suggestionEngine,
                userRepository,
                chatHistoryService
            );

            Room r20 = new Room();
            r20.setId("R20");
            r20.setLocationCode("AL-020");
            r20.setCapacity(20);

            when(roomRepository.findAllWithDetails()).thenReturn(List.of(r20));
            when(reservationRepository.findOverlappingReservationsForRooms(anyList(), anyList(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

            var reservationResponse = mock(com.finalProject.BookingMeetingRoom.model.response.ReservationResponse.class);
            when(reservationService.reserveRoom(any(), any())).thenReturn(reservationResponse);

            var auth = new TestingAuthenticationToken("user@example.com", "pw");
            // Current message is missing "book" + capacity, but has date/time.
            var res = svc.handleMessage(new ChatbotMessageRequest("Tomorrow at 10AM", "S1"), auth);

            assertNotNull(res);
            assertNotNull(res.getReservation());
            verify(reservationService, times(1)).reserveRoom(any(), any());
            }
}
