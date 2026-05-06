package com.finalProject.BookingMeetingRoom.service.ai;

import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.response.AiChatResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.ReservationService;
import com.finalProject.BookingMeetingRoom.service.RoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    @Mock
    private RoomService roomService;
    @Mock
    private ReservationService reservationService;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatHistoryService chatHistoryService;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private AiServiceImpl service;

    @Test
    void reserveViaAi_shouldReturnCreatedResponse_whenReservationSuccess() {
        ReservationRequest request = new ReservationRequest();
        ReservationResponse reservationResponse = new ReservationResponse();
        reservationResponse.setId("res-1");

        when(reservationService.reserveRoom(request, authentication)).thenReturn(reservationResponse);

        AiChatResponse result = service.reserveViaAi(request, authentication);

        assertTrue(result.isReservationCreated());
        assertEquals("res-1", result.getReservation().getId());
    }
}
