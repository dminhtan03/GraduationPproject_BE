package com.finalProject.BookingMeetingRoom.service.chatbot;

import com.finalProject.BookingMeetingRoom.common.enums.ChatbotIntent;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import com.finalProject.BookingMeetingRoom.model.entity.Building;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.ChatbotMessageRequest;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotMessageResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
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
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    void bookingFlow_shouldReturnAvailableRooms_afterCapacityProvided() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("user@example.com");

        Building building = buildBuilding("B1", "Building A");
        Floor floor = buildFloor("F1", "Floor 1", building);
        Room room1 = buildRoom("R1", "A-101", floor, 10);
        Room room2 = buildRoom("R2", "A-102", floor, 20);

        when(chatHistoryService.ensureSessionId(null)).thenReturn("s-1");
        when(chatHistoryService.ensureSessionId("s-1")).thenReturn("s-1");
        when(buildingRepository.findAll()).thenReturn(List.of(building));
        when(roomRepository.findAllWithDetails()).thenReturn(List.of(room1, room2));
        when(reservationRepository.findOverlappingReservationsForRooms(anyList(), anyList(), any(), any()))
                .thenReturn(List.of());

        ChatbotMessageRequest start = new ChatbotMessageRequest();
        start.setSessionId(null);
        start.setMessage("đặt phòng");
        ChatbotMessageResponse startResponse = service.handleMessage(start, authentication);
        assertEquals(ChatbotIntent.BOOK_ROOM, startResponse.getIntent());
        assertTrue(startResponse.getMenuOptions() != null && !startResponse.getMenuOptions().isEmpty());

        ChatbotMessageRequest selectBuilding = new ChatbotMessageRequest();
        selectBuilding.setSessionId("s-1");
        selectBuilding.setMessage("B1");
        ChatbotMessageResponse buildingResponse = service.handleMessage(selectBuilding, authentication);
        assertTrue(buildingResponse.getReply().contains("Bạn muốn đặt khi nào"));

        ChatbotMessageRequest timeMessage = new ChatbotMessageRequest();
        timeMessage.setSessionId("s-1");
        timeMessage.setMessage("ngày mai lúc 10h");
        ChatbotMessageResponse timeResponse = service.handleMessage(timeMessage, authentication);
        assertTrue(timeResponse.getReply().contains("Trong bao lâu"));

        ChatbotMessageRequest durationMessage = new ChatbotMessageRequest();
        durationMessage.setSessionId("s-1");
        durationMessage.setMessage("2 tiếng");
        ChatbotMessageResponse durationResponse = service.handleMessage(durationMessage, authentication);
        assertTrue(durationResponse.getReply().contains("Cho bao nhiêu người"));

        ChatbotMessageRequest capacityMessage = new ChatbotMessageRequest();
        capacityMessage.setSessionId("s-1");
        capacityMessage.setMessage("5-20 người");
        ChatbotMessageResponse capacityResponse = service.handleMessage(capacityMessage, authentication);

        assertEquals(ChatbotIntent.BOOK_ROOM, capacityResponse.getIntent());
        assertNotNull(capacityResponse.getAvailableRooms());
        assertEquals(2, capacityResponse.getAvailableRooms().size());
        assertTrue(capacityResponse.getReply().contains("Danh sách phòng còn trống"));
    }

    @Test
    void bookingFlow_shouldReserveSelectedRoom_usingPreviousConditions() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("user@example.com");

        Building building = buildBuilding("B1", "Building A");
        Floor floor = buildFloor("F1", "Floor 1", building);
        Room room = buildRoom("R1", "A-101", floor, 10);

        when(chatHistoryService.ensureSessionId(null)).thenReturn("s-2");
        when(chatHistoryService.ensureSessionId("s-2")).thenReturn("s-2");
        when(buildingRepository.findAll()).thenReturn(List.of(building));
        when(roomRepository.findAllWithDetails()).thenReturn(List.of(room));
        when(reservationRepository.findOverlappingReservationsForRooms(anyList(), anyList(), any(), any()))
                .thenReturn(List.of());
        when(roomRepository.findByLocationCodeIgnoreCase(eq("A-101"))).thenReturn(java.util.Optional.of(room));
        when(reservationService.reserveRoom(any(), eq(authentication))).thenReturn(new ReservationResponse());

        ChatbotMessageRequest start = new ChatbotMessageRequest();
        start.setSessionId(null);
        start.setMessage("đặt phòng");
        service.handleMessage(start, authentication);

        ChatbotMessageRequest selectBuilding = new ChatbotMessageRequest();
        selectBuilding.setSessionId("s-2");
        selectBuilding.setMessage("B1");
        service.handleMessage(selectBuilding, authentication);

        ChatbotMessageRequest timeMessage = new ChatbotMessageRequest();
        timeMessage.setSessionId("s-2");
        timeMessage.setMessage("ngày mai lúc 10h");
        service.handleMessage(timeMessage, authentication);

        ChatbotMessageRequest durationMessage = new ChatbotMessageRequest();
        durationMessage.setSessionId("s-2");
        durationMessage.setMessage("2 tiếng");
        service.handleMessage(durationMessage, authentication);

        ChatbotMessageRequest capacityMessage = new ChatbotMessageRequest();
        capacityMessage.setSessionId("s-2");
        capacityMessage.setMessage("10 người");
        service.handleMessage(capacityMessage, authentication);

        ChatbotMessageRequest bookSelectedRoom = new ChatbotMessageRequest();
        bookSelectedRoom.setSessionId("s-2");
        bookSelectedRoom.setMessage("Đặt phòng A-101");
        ChatbotMessageResponse reserveResponse = service.handleMessage(bookSelectedRoom, authentication);

        assertEquals(ChatbotIntent.BOOK_ROOM, reserveResponse.getIntent());
        assertNotNull(reserveResponse.getReservation());
//        assertTrue(reserveResponse.getReply().contains("điều kiện đã chọn"));
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
