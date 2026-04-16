package com.finalProject.BookingMeetingRoom.service.room;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.request.RoomStatusUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomStatusUpdateServiceTest {

    private SimpMessagingTemplate messagingTemplate;
    private RoomStatusUpdateServiceImpl roomStatusUpdateService;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        roomStatusUpdateService = new RoomStatusUpdateServiceImpl(messagingTemplate);
    }

    @Test
    void sendRealTimeRoomStatusUpdate_shouldSendMessageSuccessfully() {
        RoomStatusUpdateRequest request = RoomStatusUpdateRequest.builder()
                .roomId("S001")
                .newStatus(RoomStatus.AVAILABLE)
                .build();

        roomStatusUpdateService.sendRealTimeRoomStatusUpdate(request);

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/rooms"), eq(request));
    }

    @Test
    void sendRealTimeRoomStatusUpdate_whenGenericExceptionThrown_shouldWrapInCustomException() {
        RoomStatusUpdateRequest request = RoomStatusUpdateRequest.builder()
                .roomId("S002")
                .newStatus(RoomStatus.BROKEN)
                .build();

        doThrow(new RuntimeException("Unexpected error"))
                .when(messagingTemplate).convertAndSend(anyString(), any(RoomStatusUpdateRequest.class));

        CustomException exception = assertThrows(CustomException.class,
                () -> roomStatusUpdateService.sendRealTimeRoomStatusUpdate(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
    }

    @Test
    void sendRealTimeRoomStatusUpdate_whenCustomExceptionThrown_shouldRethrowIt() {
        RoomStatusUpdateRequest request = RoomStatusUpdateRequest.builder()
                .roomId("S003")
                .newStatus(RoomStatus.UNAVAILABLE)
                .build();

        doThrow(new CustomException(ResponseCode.ROOM_NOT_FOUND))
                .when(messagingTemplate).convertAndSend(anyString(), any(RoomStatusUpdateRequest.class));

        CustomException exception = assertThrows(CustomException.class,
                () -> roomStatusUpdateService.sendRealTimeRoomStatusUpdate(request));

        assertEquals(ResponseCode.ROOM_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    void sendRealTimeRoomStatusUpdate_withNullRoomId_shouldStillAttemptToSend() {
        RoomStatusUpdateRequest request = RoomStatusUpdateRequest.builder()
                .roomId(null)
                .newStatus(RoomStatus.BROKEN)
                .build();
        roomStatusUpdateService.sendRealTimeRoomStatusUpdate(request);
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/rooms"), eq(request));
    }

    @Test
    void sendRealTimeRoomStatusUpdate_withNullStatus_shouldStillAttemptToSend() {
        RoomStatusUpdateRequest request = RoomStatusUpdateRequest.builder()
                .roomId("S004")
                .newStatus(null)
                .build();

        roomStatusUpdateService.sendRealTimeRoomStatusUpdate(request);

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/rooms"), eq(request));
    }
}

