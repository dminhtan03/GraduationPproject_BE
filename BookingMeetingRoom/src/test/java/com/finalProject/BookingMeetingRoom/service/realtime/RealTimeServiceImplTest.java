package com.finalProject.BookingMeetingRoom.service.realtime;

import com.finalProject.BookingMeetingRoom.service.impl.RealTimeServiceImpl;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.request.ReservationStatusUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomReserveStatusUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomStatusUpdateRequest;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealTimeServiceImplTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private RealTimeServiceImpl realTimeService;

    @Test
    void sendRealTimeRoomStatusUpdate_shouldSendToCorrectTopic() {
        RoomStatusUpdateRequest request = RoomStatusUpdateRequest.builder()
                .roomId("room-1")
                .newStatus(RoomStatus.AVAILABLE)
                .build();

        realTimeService.sendRealTimeRoomStatusUpdate(request, "floor-1");

        verify(messagingTemplate).convertAndSend("/topic/rooms/floor-1", request);
    }

    @Test
    void sendRealTimeRoomStatusUpdate_shouldThrowInternalServerError_whenSendFails() {
        RoomStatusUpdateRequest request = RoomStatusUpdateRequest.builder()
                .roomId("room-1")
                .newStatus(RoomStatus.AVAILABLE)
                .build();
        doThrow(new RuntimeException("ws error")).when(messagingTemplate)
            .convertAndSend("/topic/rooms/floor-1", request);

        CustomException ex = assertThrows(
                CustomException.class,
                () -> realTimeService.sendRealTimeRoomStatusUpdate(request, "floor-1")
        );

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void sendRealTimeRoomReserveStatusUpdate_shouldSendToCorrectTopic() {
        RoomReserveStatusUpdateRequest request = RoomReserveStatusUpdateRequest.builder()
                .roomId("room-2")
                .type("ADD")
                .leftTime(LocalDateTime.of(2026, 4, 20, 8, 0))
                .rightTime(LocalDateTime.of(2026, 4, 20, 9, 0))
                .build();

        realTimeService.sendRealTimeRoomReserveStatusUpdate(request, "floor-2", "2026-04-20");

        verify(messagingTemplate).convertAndSend("/topic/room-reserve/floor-2/2026-04-20", request);
    }

    @Test
    void sendRealTimeReservationStatusUpdate_shouldSendToCorrectTopic() {
        ReservationStatusUpdateRequest request = ReservationStatusUpdateRequest.builder()
                .reservationId("res-1")
                .newStatus(ReservationStatus.RESERVED)
                .build();

        realTimeService.sendRealTimeReservationStatusUpdate(request, "user-1");

        verify(messagingTemplate).convertAndSend("/topic/reservations/user-1", request);
    }

    @Test
    void addReservation_shouldBuildAndSendReserveStatusUpdate() {
        Floor floor = new Floor();
        floor.setId("floor-3");

        Room room = new Room();
        room.setId("room-3");
        room.setFloor(floor);

        Reservation reservation = new Reservation();
        reservation.setRoom(room);
        reservation.setStartTime(LocalDateTime.of(2026, 4, 21, 10, 0));
        reservation.setEndTime(LocalDateTime.of(2026, 4, 21, 11, 0));

        realTimeService.addReservation(reservation);

        ArgumentCaptor<RoomReserveStatusUpdateRequest> captor = ArgumentCaptor.forClass(RoomReserveStatusUpdateRequest.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/room-reserve/floor-3/2026-04-21"),
                captor.capture()
        );

        assertEquals("room-3", captor.getValue().getRoomId());
        assertEquals("ADD", captor.getValue().getType());
        assertEquals(reservation.getStartTime(), captor.getValue().getLeftTime());
        assertEquals(reservation.getEndTime(), captor.getValue().getRightTime());
    }

    @Test
    void deleteReservation_shouldUseNeighborTimes_whenFound() {
        Floor floor = new Floor();
        floor.setId("floor-4");

        Room room = new Room();
        room.setId("room-4");
        room.setFloor(floor);

        Reservation current = new Reservation();
        current.setRoom(room);
        current.setStartTime(LocalDateTime.of(2026, 4, 22, 10, 0));
        current.setEndTime(LocalDateTime.of(2026, 4, 22, 11, 0));

        Reservation last = new Reservation();
        last.setEndTime(LocalDateTime.of(2026, 4, 22, 9, 30));

        Reservation next = new Reservation();
        next.setStartTime(LocalDateTime.of(2026, 4, 22, 12, 0));

        when(reservationRepository.findLastReservation(current.getStartTime(), "room-4")).thenReturn(Optional.of(last));
        when(reservationRepository.findNextReservation(current.getEndTime(), "room-4")).thenReturn(Optional.of(next));

        realTimeService.deleteReservation(current);

        ArgumentCaptor<RoomReserveStatusUpdateRequest> captor = ArgumentCaptor.forClass(RoomReserveStatusUpdateRequest.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/room-reserve/floor-4/2026-04-22"),
                captor.capture()
        );

        assertEquals("DELETE", captor.getValue().getType());
        assertEquals(LocalDateTime.of(2026, 4, 22, 9, 30), captor.getValue().getLeftTime());
        assertEquals(LocalDateTime.of(2026, 4, 22, 12, 0), captor.getValue().getRightTime());
    }

    @Test
    void deleteReservation_shouldUseDefaultBoundaries_whenNoNeighbors() {
        Floor floor = new Floor();
        floor.setId("floor-5");

        Room room = new Room();
        room.setId("room-5");
        room.setFloor(floor);

        Reservation current = new Reservation();
        current.setRoom(room);
        current.setStartTime(LocalDateTime.of(2026, 4, 23, 10, 0));
        current.setEndTime(LocalDateTime.of(2026, 4, 23, 11, 0));

        when(reservationRepository.findLastReservation(current.getStartTime(), "room-5")).thenReturn(Optional.empty());
        when(reservationRepository.findNextReservation(current.getEndTime(), "room-5")).thenReturn(Optional.empty());

        realTimeService.deleteReservation(current);

        ArgumentCaptor<RoomReserveStatusUpdateRequest> captor = ArgumentCaptor.forClass(RoomReserveStatusUpdateRequest.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/room-reserve/floor-5/2026-04-23"),
                captor.capture()
        );

        assertEquals(LocalDateTime.of(1970, 1, 1, 0, 0), captor.getValue().getLeftTime());
        assertEquals(LocalDateTime.of(2100, 12, 31, 23, 59), captor.getValue().getRightTime());
    }

    @Test
    void sendReservationStatus_shouldPublishForReservationUser() {
        User user = new User();
        user.setId("user-9");

        Reservation reservation = new Reservation();
        reservation.setId("res-9");
        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.setUser(user);

        realTimeService.sendReservationStatus(reservation);

        ArgumentCaptor<ReservationStatusUpdateRequest> captor = ArgumentCaptor.forClass(ReservationStatusUpdateRequest.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/reservations/user-9"),
                captor.capture()
        );

        assertEquals("res-9", captor.getValue().getReservationId());
        assertEquals(ReservationStatus.COMPLETED, captor.getValue().getNewStatus());
    }

    @Test
    void sendRoomStatus_shouldPublishForFloor() {
        Floor floor = new Floor();
        floor.setId("floor-6");

        Room room = new Room();
        room.setId("room-6");
        room.setStatus(RoomStatus.BROKEN);
        room.setFloor(floor);

        realTimeService.sendRoomStatus(room);

        ArgumentCaptor<RoomStatusUpdateRequest> captor = ArgumentCaptor.forClass(RoomStatusUpdateRequest.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/rooms/floor-6"),
                captor.capture()
        );

        assertEquals("room-6", captor.getValue().getRoomId());
        assertEquals(RoomStatus.BROKEN, captor.getValue().getNewStatus());
    }
}


