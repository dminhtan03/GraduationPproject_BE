package com.finalProject.BookingMeetingRoom.service.realtime;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.request.RoomStatusUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomStatusUpdateServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RoomStatusUpdateServiceImpl roomStatusUpdateService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void sendRealTimeRoomStatusUpdate_shouldSendMessageToTopic() {
        // Arrange
        RoomStatusUpdateRequest request = RoomStatusUpdateRequest.builder()
                .roomId("room001")
                .newStatus(RoomStatus.AVAILABLE)
                .build();

        // Act
        assertDoesNotThrow(() -> roomStatusUpdateService.sendRealTimeRoomStatusUpdate(request));

        // Assert
        verify(messagingTemplate, times(1))
                .convertAndSend("/topic/rooms", request);
    }

    @Test
    void sendRealTimeRoomStatusUpdate_shouldThrowCustomExceptionOnError() {
        // Arrange
        RoomStatusUpdateRequest request = RoomStatusUpdateRequest.builder()
                .roomId("room001")
                .newStatus(RoomStatus.AVAILABLE)
                .build();

        doThrow(new RuntimeException("Messaging error"))
                .when(messagingTemplate).convertAndSend(anyString(), (Object) any());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () ->
                roomStatusUpdateService.sendRealTimeRoomStatusUpdate(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
    }
}