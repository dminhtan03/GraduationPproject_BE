package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.HistoryAction;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.ReservationHistory;
import com.finalProject.BookingMeetingRoom.repository.ReservationHistoryRepository;
import com.finalProject.BookingMeetingRoom.service.ReservationHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationHistoryServiceImpl implements ReservationHistoryService {

    private final ReservationHistoryRepository reservationHistoryRepository;

    /**
     * Save the history of a reservation action.
     *
     * @param reservation the reservation entity
     * @param userId the ID of the user performing the action
     * @param oldStatus the previous status of the reservation
     * @param action the action performed on the reservation
     */
    public void saveHistory(Reservation reservation, String userId, ReservationStatus oldStatus, HistoryAction action
            , LocalDateTime performAt) {
        try {
            var history = new ReservationHistory();
            history.setId(UUID.randomUUID().toString());
            history.setReservation(reservation);
            history.setOldStatus(oldStatus);
            history.setPerformBy(userId);
            history.setPerformAt(performAt != null ? performAt : LocalDateTime.now());

            if (action != null) {
                history.setAction(action);
            }

            reservationHistoryRepository.save(history);
        } catch (Exception e) {
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Save the history of multiple reservation histories.
     *
     * @param reservations the list of reservations
     * @param userId the ID of the user performing the actions
     * @param action the action performed on the reservations
     */
    public void saveAllHistories(List<Reservation> reservations, String userId, HistoryAction action) {
        try {
            if (reservations == null || reservations.isEmpty()) {
                return;
            }

            List<ReservationHistory> histories = reservations.stream()
                    .map(reservation -> {
                        var history = new ReservationHistory();
                        history.setId(UUID.randomUUID().toString());
                        history.setReservation(reservation);
                        history.setOldStatus(reservation.getStatus());
                        history.setPerformBy(userId);
                        history.setPerformAt(reservation.getUpdatedAt());

                        if (action != null) {
                            history.setAction(action);
                        }

                        return history;
                    })
                    .collect(Collectors.toList());

            reservationHistoryRepository.saveAll(histories);
        } catch (Exception e) {
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
}