package com.finalProject.BookingMeetingRoom.service.reservationhistory;

import com.finalProject.BookingMeetingRoom.service.impl.ReservationHistoryServiceImpl;
import com.finalProject.BookingMeetingRoom.common.enums.HistoryAction;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.ReservationHistory;
import com.finalProject.BookingMeetingRoom.repository.ReservationHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationHistoryServiceImplTest {

    @Mock
    private ReservationHistoryRepository reservationHistoryRepository;

    @InjectMocks
    private ReservationHistoryServiceImpl reservationHistoryService;

    @Test
    void saveHistory_shouldSaveSuccessfully_whenActionProvidedAndPerformAtNull() {
        Reservation reservation = new Reservation();
        reservation.setId("res-1");

        reservationHistoryService.saveHistory(
                reservation,
                "user-1",
                ReservationStatus.RESERVED,
                HistoryAction.CHECK_IN,
                null
        );

        ArgumentCaptor<ReservationHistory> captor = ArgumentCaptor.forClass(ReservationHistory.class);
        verify(reservationHistoryRepository).save(captor.capture());

        ReservationHistory saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals(reservation, saved.getReservation());
        assertEquals("user-1", saved.getPerformBy());
        assertEquals(ReservationStatus.RESERVED, saved.getOldStatus());
        assertEquals(HistoryAction.CHECK_IN, saved.getAction());
        assertNotNull(saved.getPerformAt());
    }

    @Test
    void saveHistory_shouldSaveWithoutAction_whenActionIsNull() {
        Reservation reservation = new Reservation();
        LocalDateTime performAt = LocalDateTime.of(2026, 4, 20, 10, 0);

        reservationHistoryService.saveHistory(
                reservation,
                "user-2",
                ReservationStatus.IN_USE,
                null,
                performAt
        );

        ArgumentCaptor<ReservationHistory> captor = ArgumentCaptor.forClass(ReservationHistory.class);
        verify(reservationHistoryRepository).save(captor.capture());

        ReservationHistory saved = captor.getValue();
        assertEquals(performAt, saved.getPerformAt());
        assertNull(saved.getAction());
    }

    @Test
    void saveHistory_shouldThrowCustomException_whenRepositoryThrows() {
        doThrow(new RuntimeException("db error")).when(reservationHistoryRepository).save(any(ReservationHistory.class));

        CustomException ex = assertThrows(
                CustomException.class,
                () -> reservationHistoryService.saveHistory(new Reservation(), "u1", ReservationStatus.RESERVED,
                HistoryAction.EXTEND, LocalDateTime.now())
        );

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void saveAllHistories_shouldReturnImmediately_whenReservationsNullOrEmpty() {
        reservationHistoryService.saveAllHistories(null, "user-1", HistoryAction.EXTEND);
        reservationHistoryService.saveAllHistories(List.of(), "user-1", HistoryAction.EXTEND);

        verify(reservationHistoryRepository, never()).saveAll(any());
    }

    @Test
    void saveAllHistories_shouldSaveAll_whenActionProvided() {
        Reservation reservation1 = new Reservation();
        reservation1.setStatus(ReservationStatus.RESERVED);
        reservation1.setUpdatedAt(LocalDateTime.of(2026, 4, 20, 9, 0));

        Reservation reservation2 = new Reservation();
        reservation2.setStatus(ReservationStatus.IN_USE);
        reservation2.setUpdatedAt(LocalDateTime.of(2026, 4, 20, 11, 0));

        reservationHistoryService.saveAllHistories(
                List.of(reservation1, reservation2),
                "user-3",
            HistoryAction.EXTEND
        );

        ArgumentCaptor<List<ReservationHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationHistoryRepository).saveAll(captor.capture());

        List<ReservationHistory> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(HistoryAction.EXTEND, saved.get(0).getAction());
        assertEquals("user-3", saved.get(1).getPerformBy());
        assertNotNull(saved.get(0).getId());
        assertNotNull(saved.get(1).getId());
    }

    @Test
    void saveAllHistories_shouldSaveAllWithoutAction_whenActionIsNull() {
        Reservation reservation = new Reservation();
        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.setUpdatedAt(LocalDateTime.of(2026, 4, 20, 12, 0));

        reservationHistoryService.saveAllHistories(List.of(reservation), "user-4", null);

        ArgumentCaptor<List<ReservationHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationHistoryRepository).saveAll(captor.capture());

        List<ReservationHistory> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertNull(saved.get(0).getAction());
    }

    @Test
    void saveAllHistories_shouldThrowCustomException_whenRepositoryThrows() {
        doThrow(new RuntimeException("db error")).when(reservationHistoryRepository).saveAll(any());

        Reservation reservation = new Reservation();
        reservation.setStatus(ReservationStatus.RESERVED);

        CustomException ex = assertThrows(
                CustomException.class,
            () -> reservationHistoryService.saveAllHistories(List.of(reservation), "u9", HistoryAction.EXTEND)
        );

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }
}


