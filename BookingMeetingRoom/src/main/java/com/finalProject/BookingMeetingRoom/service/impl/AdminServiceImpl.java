package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.AdminService;
import com.finalProject.BookingMeetingRoom.service.NotificationService;
import com.finalProject.BookingMeetingRoom.service.RealTimeService;
import jakarta.transaction.Transactional;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Builder
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
    private final Logger logger = LogManager.getLogger(AdminServiceImpl.class);
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final RealTimeService realTimeService;
    private final NotificationService notificationService;
    /**
     * Forcefully returns a seat and cancels the associated reservation.
     *
     * @param roomIds       List ID of seats to be returned.
     * @param connectedUser The authenticated user performing the action.
     */
    @Transactional
    public void forceReturn(List<String> roomIds, Authentication connectedUser) {
        try {
            var rooms = roomRepository.findRoomsByRoomIds(roomIds);
            if (rooms.isEmpty()) {
                throw new CustomException(ResponseCode.ROOM_NOT_FOUND);
            }

            var user = userRepository.findByEmail(connectedUser.getName());
            if (user.isEmpty()) {
                throw new CustomException(ResponseCode.USER_NOT_FOUND);
            }
            List<Reservation> listReservationForceReturned = new ArrayList<>();

            rooms.forEach(room -> {
                var activeReservation = reservationRepository.findByIdAndStatus(
                        room.getId(),
                        ReservationStatus.IN_USE
                );
                if (activeReservation.isEmpty()) {
                    return;
                }

                var reservation = activeReservation.get();
                reservation.setStatus(ReservationStatus.FORCE_CANCELLED);
                reservation.setUpdatedAt(LocalDateTime.now());

                room.setStatus(RoomStatus.AVAILABLE);
                room.setUpdatedAt(LocalDateTime.now());
                roomRepository.save(room);
                reservationRepository.save(reservation);


                realTimeService.sendRoomStatus(room);
                realTimeService.sendReservationStatus(reservation);
                realTimeService.deleteReservation(reservation);


                listReservationForceReturned.add(reservation);
            });
            notificationService.noticeForceCancelReservation(listReservationForceReturned);

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
}
