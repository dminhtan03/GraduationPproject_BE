package com.finalProject.BookingMeetingRoom.service.seat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatStatusUpdateServiceTest {

    private SimpMessagingTemplate messagingTemplate;
    private SeatStatusUpdateServiceImpl seatStatusUpdateService;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        seatStatusUpdateService = new SeatStatusUpdateServiceImpl(messagingTemplate);
    }

    @Test
    void sendRealTimeSeatStatusUpdate_shouldSendMessageSuccessfully() {
        SeatStatusUpdateRequest request = SeatStatusUpdateRequest.builder()
                .seatId("S001")
                .newStatus(SeatStatus.AVAILABLE)
                .build();

        seatStatusUpdateService.sendRealTimeSeatStatusUpdate(request);

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/seats"), eq(request));
    }

    @Test
    void sendRealTimeSeatStatusUpdate_whenGenericExceptionThrown_shouldWrapInCustomException() {
        SeatStatusUpdateRequest request = SeatStatusUpdateRequest.builder()
                .seatId("S002")
                .newStatus(SeatStatus.BROKEN)
                .build();

        doThrow(new RuntimeException("Unexpected error"))
                .when(messagingTemplate).convertAndSend(anyString(), any(SeatStatusUpdateRequest.class));

        CustomException exception = assertThrows(CustomException.class,
                () -> seatStatusUpdateService.sendRealTimeSeatStatusUpdate(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
    }

    @Test
    void sendRealTimeSeatStatusUpdate_whenCustomExceptionThrown_shouldRethrowIt() {
        SeatStatusUpdateRequest request = SeatStatusUpdateRequest.builder()
                .seatId("S003")
                .newStatus(SeatStatus.UNAVAILABLE)
                .build();

        doThrow(new CustomException(ResponseCode.SEAT_NOT_FOUND))
                .when(messagingTemplate).convertAndSend(anyString(), any(SeatStatusUpdateRequest.class));

        CustomException exception = assertThrows(CustomException.class,
                () -> seatStatusUpdateService.sendRealTimeSeatStatusUpdate(request));

        assertEquals(ResponseCode.SEAT_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    void sendRealTimeSeatStatusUpdate_withNullSeatId_shouldStillAttemptToSend() {
        SeatStatusUpdateRequest request = SeatStatusUpdateRequest.builder()
                .seatId(null)
                .newStatus(SeatStatus.BROKEN)
                .build();
        seatStatusUpdateService.sendRealTimeSeatStatusUpdate(request);
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/seats"), eq(request));
    }

    @Test
    void sendRealTimeSeatStatusUpdate_withNullStatus_shouldStillAttemptToSend() {
        SeatStatusUpdateRequest request = SeatStatusUpdateRequest.builder()
                .seatId("S004")
                .newStatus(null)
                .build();

        seatStatusUpdateService.sendRealTimeSeatStatusUpdate(request);

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/seats"), eq(request));
    }
}

