package com.finalProject.BookingMeetingRoom.service.ai;

import com.finalProject.BookingMeetingRoom.common.enums.ChatbotIntent;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Building;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.ChatbotMessageRequest;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotMessageResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.repository.BuildingRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
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
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    @Mock
    private RoomService roomService;
    @Mock
    private ReservationService reservationService;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatHistoryService chatHistoryService;
    @Mock
    private ChatbotLlmService chatbotLlmService;
    @Mock
    private ChatbotRoomSuggestionEngine suggestionEngine;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private ChatbotServiceImpl service;

    @Test
    void reserveViaChatbot_shouldReturnReservation_whenRoomIsAvailable() {
        Building building = buildBuilding("B1", "Building A");
        Floor floor = buildFloor("F1", "Floor 1", building);
        Room room = buildRoom("room-1", "A-101", floor, 10);

        ReservationResponse reservationResponse = new ReservationResponse();
        reservationResponse.setId("res-1");

        when(chatHistoryService.ensureSessionId(null)).thenReturn("s-1");
        when(chatHistoryService.ensureSessionId("s-1")).thenReturn("s-1");
        when(buildingRepository.findAll()).thenReturn(java.util.List.of(building));
        when(roomRepository.findAllWithDetails()).thenReturn(java.util.List.of(room));
        when(reservationRepository.findOverlappingReservationsForRooms(anyList(), anyList(), any(), any()))
            .thenReturn(java.util.List.of());
        when(roomRepository.findByLocationCodeIgnoreCase("A-101")).thenReturn(java.util.Optional.of(room));
        when(reservationService.reserveRoom(any(), eq(authentication))).thenReturn(reservationResponse);

        ChatbotMessageRequest start = new ChatbotMessageRequest();
        start.setSessionId(null);
        start.setMessage("đặt phòng");
        service.handleMessage(start, authentication);

        ChatbotMessageRequest selectBuilding = new ChatbotMessageRequest();
        selectBuilding.setSessionId("s-1");
        selectBuilding.setMessage("B1");
        service.handleMessage(selectBuilding, authentication);

        ChatbotMessageRequest timeMessage = new ChatbotMessageRequest();
        timeMessage.setSessionId("s-1");
        timeMessage.setMessage("ngày mai lúc 10h");
        service.handleMessage(timeMessage, authentication);

        ChatbotMessageRequest durationMessage = new ChatbotMessageRequest();
        durationMessage.setSessionId("s-1");
        durationMessage.setMessage("1 tiếng");
        service.handleMessage(durationMessage, authentication);

        ChatbotMessageRequest capacityMessage = new ChatbotMessageRequest();
        capacityMessage.setSessionId("s-1");
        capacityMessage.setMessage("10 người");
        service.handleMessage(capacityMessage, authentication);

        ChatbotMessageRequest bookSelectedRoom = new ChatbotMessageRequest();
        bookSelectedRoom.setSessionId("s-1");
        bookSelectedRoom.setMessage("Đặt phòng A-101");

        ChatbotMessageResponse result = service.handleMessage(bookSelectedRoom, authentication);

        assertEquals(ChatbotIntent.BOOK_ROOM, result.getIntent());
        assertNotNull(result.getReservation());
        assertEquals("res-1", result.getReservation().getId());
    }

    private Building buildBuilding(String id, String name) {
        Building building = new Building();
        building.setId(id);
        building.setName(name);
        building.setDeleted(false);
        return building;
    }

    private Floor buildFloor(String id, String name, Building building) {
        Floor floor = new Floor();
        floor.setId(id);
        floor.setName(name);
        floor.setDeleted(false);
        floor.setBuilding(building);
        return floor;
    }

    private Room buildRoom(String id, String code, Floor floor, int capacity) {
        Room room = new Room();
        room.setId(id);
        room.setLocationCode(code);
        room.setFloor(floor);
        room.setCapacity(capacity);
        room.setStatus(RoomStatus.AVAILABLE);
        return room;
    }
}
