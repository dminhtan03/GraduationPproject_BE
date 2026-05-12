package com.finalProject.BookingMeetingRoom.service.batch;

import com.finalProject.BookingMeetingRoom.service.impl.BatchServiceImpl;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.NotificationService;
import com.finalProject.BookingMeetingRoom.service.RealTimeService;
import com.finalProject.BookingMeetingRoom.service.ReservationHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchServiceImplTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private RealTimeService realTimeService;

    @Mock
    private ReservationHistoryService reservationHistoryService;

    @InjectMocks
    private BatchServiceImpl batchService;

    @Test
    void updateUserStatuses_shouldRunAllSubProcesses() {
        when(reservationRepository.findReservationsOverStartTime(any(LocalDateTime.class))).thenReturn(List.of());
        when(reservationRepository.findReservationsOverEndTime(any(LocalDateTime.class))).thenReturn(List.of());
        when(reservationRepository.findByStatus(ReservationStatus.PENDING)).thenReturn(List.of());

        batchService.updateUserStatuses();

        verify(reservationRepository).findReservationsOverStartTime(any(LocalDateTime.class));
        verify(reservationRepository).findReservationsOverEndTime(any(LocalDateTime.class));
        verify(reservationRepository).findByStatus(ReservationStatus.PENDING);
        verify(notificationService).remindCheckIn();
    }

    @Test
    void updateUserStatuses_shouldThrowInternalServerError_whenUnexpectedExceptionOccurs() {
        doThrow(new RuntimeException("boom"))
                .when(reservationRepository).findReservationsOverStartTime(any(LocalDateTime.class));

        CustomException ex = assertThrows(CustomException.class, () -> batchService.updateUserStatuses());
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void processReservation_shouldUpdateNoShowReservations_andNotify() {
        User user = new User();
        user.setId("u1");

        Room room = new Room();
        room.setId("room-1");
        room.setStatus(RoomStatus.UNAVAILABLE);

        Reservation reservation = new Reservation();
        reservation.setId("res-1");
        reservation.setUser(user);
        reservation.setRoom(room);
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setUpdatedAt(LocalDateTime.of(2026, 4, 20, 8, 0));

        when(reservationRepository.findReservationsOverStartTime(any(LocalDateTime.class)))
                .thenReturn(List.of(reservation));

        batchService.processReservation(ReservationStatus.NO_SHOW);

        assertEquals(ReservationStatus.NO_SHOW, reservation.getStatus());
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());

        verify(notificationService).noticeNoCheckInReservation(List.of(reservation));
        verify(notificationService, never()).noticeOverTimeReservation(anyList());
        verify(roomRepository).save(room);
        verify(reservationHistoryService)
                .saveHistory(eq(reservation), eq("u1"), eq(ReservationStatus.RESERVED), eq(null), any(LocalDateTime.class));
        verify(reservationRepository).save(reservation);
        verify(realTimeService).sendRoomStatus(room);
        verify(realTimeService).sendReservationStatus(reservation);
        verify(realTimeService).deleteReservation(reservation);
    }

    @Test
    void processReservation_shouldSkipRoomUpdate_whenRoomIsNull() {
        Reservation reservation = new Reservation();
        reservation.setId("res-x");
        reservation.setRoom(null);
        reservation.setStatus(ReservationStatus.RESERVED);

        when(reservationRepository.findReservationsOverEndTime(any(LocalDateTime.class)))
                .thenReturn(List.of(reservation));

        batchService.processReservation(ReservationStatus.COMPLETED);

        verify(notificationService).noticeOverTimeReservation(List.of(reservation));
        verify(roomRepository, never()).save(any(Room.class));
        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(realTimeService, never()).sendRoomStatus(any(Room.class));
    }

    @Test
    void processPendingReservations_shouldReturn_whenNoPendingReservations() {
        when(reservationRepository.findByStatus(ReservationStatus.PENDING)).thenReturn(List.of());

        batchService.processPendingReservations();

        verify(reservationRepository).findByStatus(ReservationStatus.PENDING);
        verify(reservationRepository, never()).saveAll(anyList());
        verify(notificationService, never()).noticeSuccessfulReservation(anyList());
        verify(notificationService, never()).noticeFailedReservation(anyList());
    }

    @Test
    void processPendingReservations_shouldSetReservedAndFailed_andNotifyBothLists() {
        User user1 = new User();
        user1.setId("u1");

        User user2 = new User();
        user2.setId("u2");

        Room room = new Room();
        room.setId("room-1");

        Reservation first = new Reservation();
        first.setId("p1");
        first.setUser(user1);
        first.setRoom(room);
        first.setStatus(ReservationStatus.PENDING);
        first.setCreateAt(LocalDateTime.of(2026, 4, 20, 7, 0));
        first.setStartTime(LocalDateTime.of(2026, 4, 20, 10, 0));
        first.setEndTime(LocalDateTime.of(2026, 4, 20, 11, 0));

        Reservation second = new Reservation();
        second.setId("p2");
        second.setUser(user2);
        second.setRoom(room);
        second.setStatus(ReservationStatus.PENDING);
        second.setCreateAt(LocalDateTime.of(2026, 4, 20, 8, 0));
        second.setStartTime(LocalDateTime.of(2026, 4, 20, 10, 30));
        second.setEndTime(LocalDateTime.of(2026, 4, 20, 11, 30));

        when(reservationRepository.findByStatus(ReservationStatus.PENDING)).thenReturn(List.of(first, second));
        when(reservationRepository.countReservationsTodayByUserIds(anySet(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(new Object[]{"u1", 0}, new Object[]{"u2", 0}));
        when(reservationRepository.findByRoomIdsAndStatusIn(anySet(), anyList())).thenReturn(List.of());
        when(reservationRepository.findByUserIdsAndStatusIn(anySet(), anyList())).thenReturn(List.of());

        batchService.processPendingReservations();

        assertEquals(ReservationStatus.RESERVED, first.getStatus());
        assertEquals(ReservationStatus.FAILED, second.getStatus());

        verify(reservationRepository).saveAll(anyList());
        verify(reservationRepository).flush();

        verify(realTimeService).sendReservationStatus(first);
        verify(realTimeService).sendReservationStatus(second);
        verify(realTimeService).addReservation(first);
        verify(realTimeService, never()).addReservation(second);

        verify(notificationService).noticeSuccessfulReservation(anyList());
        verify(notificationService).noticeFailedReservation(anyList());
    }
}


