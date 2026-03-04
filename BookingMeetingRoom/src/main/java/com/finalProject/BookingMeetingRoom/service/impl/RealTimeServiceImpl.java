package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.ReservationStatusUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomReserveStatusUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomStatusUpdateRequest;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.service.RealTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeServiceImpl implements RealTimeService {
    private final SimpMessagingTemplate messagingTemplate;
    private final ReservationRepository reservationRepository;

    /**
     * Sends real-time updates about room status to the specified floor.
     *
     * @param roomStatusUpdateRequest the request containing room status update information
     * @param floorId                 the ID of the floor to which the update should be sent
     */
    @Override
    public void sendRealTimeRoomStatusUpdate(RoomStatusUpdateRequest roomStatusUpdateRequest, String floorId) {
        try {
            String topic = "/topic/rooms/" + floorId;
            log.info("Sending real-time information about room status updating to topic: {}", topic);
            messagingTemplate.convertAndSend(topic, roomStatusUpdateRequest);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while sending information about room status updating", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Sends real-time updates about room reservation status to the specified floor and date.
     *
     * @param roomReserveStatusUpdateRequest the request containing room reservation status update information
     * @param floorId                        the ID of the floor to which the update should be sent
     * @param date                           the date for which the update is relevant
     */
    @Override
    public void sendRealTimeRoomReserveStatusUpdate(RoomReserveStatusUpdateRequest roomReserveStatusUpdateRequest, String floorId, String date) {
        try {
            String topic = "/topic/room-reserve/" + floorId + "/" + date;
            log.info("Sending real-time information about room status updating for reserving to topic: {}", topic);
            messagingTemplate.convertAndSend(topic, roomReserveStatusUpdateRequest);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while sending information about room status updating for reserving", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Sends real-time updates about reservation status to the specified user.
     *
     * @param reservationStatusUpdateRequest the request containing reservation status update information
     * @param userId                         the ID of the user to whom the update should be sent
     */
    @Override
    public void sendRealTimeReservationStatusUpdate(ReservationStatusUpdateRequest reservationStatusUpdateRequest, String userId) {
        try {
            String topic = "/topic/reservations/" + userId;
            log.info("Sending real-time information about reservation status updating to topic: {}", topic);
            messagingTemplate.convertAndSend(topic, reservationStatusUpdateRequest);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while sending information about reservation status updating", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Adds a reservation and sends real-time updates about the room reservation status.
     *
     * @param reservation the reservation to be added
     */
    @Override
    public void addReservation(Reservation reservation) {
        RoomReserveStatusUpdateRequest roomReserveStatusUpdateRequest = RoomReserveStatusUpdateRequest.builder()
                .roomId(reservation.getRoom().getId()).type("ADD").leftTime(reservation.getStartTime())
                .rightTime(reservation.getEndTime()).build();
        sendRealTimeRoomReserveStatusUpdate(roomReserveStatusUpdateRequest, reservation.getRoom().getFloor().getId(),
                reservation.getStartTime().toLocalDate().toString());
    }

    /**
     * Deletes a reservation and sends real-time updates about the room reservation status.
     *
     * @param reservation the reservation to be deleted
     */
    @Override
    public void deleteReservation(Reservation reservation) {
        Optional<Reservation> LastOptional = reservationRepository
                .findLastReservation(reservation.getStartTime(), reservation.getRoom().getId());
        Optional<Reservation> NextOptional = reservationRepository
                .findNextReservation(reservation.getEndTime(), reservation.getRoom().getId());
        LocalDateTime leftTime = (LastOptional.isPresent() ? LastOptional.get().getEndTime()
                : LocalDateTime.of(1970, 1, 1, 0, 0));
        LocalDateTime rightTime = (NextOptional.isPresent() ? NextOptional.get().getStartTime()
                : LocalDateTime.of(2100, 12, 31, 23, 59));
        RoomReserveStatusUpdateRequest roomReserveStatusUpdateRequest = RoomReserveStatusUpdateRequest.builder()
                .roomId(reservation.getRoom().getId()).type("DELETE").leftTime(leftTime).rightTime(rightTime).build();
        sendRealTimeRoomReserveStatusUpdate(roomReserveStatusUpdateRequest, reservation.getRoom().getFloor().getId(),
                reservation.getStartTime().toLocalDate().toString());
    }

    /**
     * Sends real-time updates about reservation status to the user associated with the reservation.
     *
     * @param reservation the reservation for which the status update is to be sent
     */
    @Override
    public void sendReservationStatus(Reservation reservation) {
        ReservationStatusUpdateRequest reservationStatusUpdateRequest = ReservationStatusUpdateRequest.builder()
                .reservationId(reservation.getId())
                .newStatus(reservation.getStatus()).build();
        sendRealTimeReservationStatusUpdate(reservationStatusUpdateRequest, reservation.getUser().getId());
    }

    /**
     * Sends real-time updates about room status to the specified room.
     *
     * @param room the room for which the status update is to be sent
     */
    @Override
    public void sendRoomStatus(Room room) {
        RoomStatusUpdateRequest roomStatusUpdateRequest = RoomStatusUpdateRequest.builder()
                .roomId(room.getId())
                .newStatus(room.getStatus()).build();

        sendRealTimeRoomStatusUpdate(roomStatusUpdateRequest, room.getFloor().getId());
    }
}
