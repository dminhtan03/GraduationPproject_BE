package com.finalProject.BookingMeetingRoom.service.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SeatStatusUpdateServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private SeatStatusUpdateServiceImpl seatStatusUpdateService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void sendRealTimeSeatStatusUpdate_shouldSendMessageToTopic() {
        // Arrange
        SeatStatusUpdateRequest request = SeatStatusUpdateRequest.builder()
                .seatId("seat001")
                .newStatus(SeatStatus.AVAILABLE)
                .build();

        // Act
        assertDoesNotThrow(() -> seatStatusUpdateService.sendRealTimeSeatStatusUpdate(request));

        // Assert
        verify(messagingTemplate, times(1))
                .convertAndSend("/topic/seats", request);
    }

    @Test
    void sendRealTimeSeatStatusUpdate_shouldThrowCustomExceptionOnError() {
        // Arrange
        SeatStatusUpdateRequest request = SeatStatusUpdateRequest.builder()
                .seatId("seat001")
                .newStatus(SeatStatus.AVAILABLE)
                .build();

        doThrow(new RuntimeException("Messaging error"))
                .when(messagingTemplate).convertAndSend(anyString(), (Object) any());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () ->
                seatStatusUpdateService.sendRealTimeSeatStatusUpdate(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
    }
}