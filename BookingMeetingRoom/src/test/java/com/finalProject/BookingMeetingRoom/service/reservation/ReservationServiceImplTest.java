package com.finalProject.BookingMeetingRoom.service.reservation;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.FeedbackMapper;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapper;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapperFacade;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.repository.ReservationHistoryRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserInfoRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;
import com.finalProject.BookingMeetingRoom.service.EmailService;
import com.finalProject.BookingMeetingRoom.service.NotificationService;
import com.finalProject.BookingMeetingRoom.service.RealTimeService;
import com.finalProject.BookingMeetingRoom.service.ReservationHistoryService;
import com.finalProject.BookingMeetingRoom.service.impl.ReservationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationHistoryRepository reservationHistoryRepository;
    @Mock
    private RealTimeService realTimeService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserInfoRepository userInfoRepository;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private ReservationMapperFacade reservationMapperFacade;
    @Mock
    private ReservationHistoryService reservationHistoryService;
    @Mock
    private FeedbackMapper feedbackMapper;
    @Mock
    private EmailService emailService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AcademicScheduleService academicScheduleService;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private ReservationServiceImpl service;

    private Reservation buildReservation(String reservationOwnerId) {
        Room room = new Room();
        room.setId("room-1");
        room.setStatus(RoomStatus.AVAILABLE);

        User owner = new User();
        owner.setId(reservationOwnerId);
        UserInfo info = new UserInfo();
        info.setEmail("owner@test.com");
        owner.setUserInfo(info);

        Reservation reservation = new Reservation();
        reservation.setId("res-1");
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setRoom(room);
        reservation.setUser(owner);
        reservation.setStartTime(LocalDateTime.of(2026, 4, 24, 8, 0));
        reservation.setEndTime(LocalDateTime.of(2026, 4, 24, 9, 0));
        return reservation;
    }

    @Test
    void extendReservation_shouldThrowInvalidHour_whenHourOutOfRange() {
        CustomException ex = assertThrows(CustomException.class,
                () -> service.extendReservation("res-1", 0.5, authentication));

        assertEquals(ResponseCode.RESERVATION_INVALID_HOUR, ex.getResponseCode());
    }

        @Test
        void extendReservation_shouldThrowReservationNotFound_whenIdMissing() {
        when(reservationRepository.findById("res-404")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
            () -> service.extendReservation("res-404", 1, authentication));

        assertEquals(ResponseCode.RESERVATION_NOT_FOUND, ex.getResponseCode());
        }

        @Test
        void extendReservation_shouldThrowReservationNotInUse_whenStatusInvalid() {
        Reservation reservation = buildReservation("u-1");
        reservation.setStatus(ReservationStatus.CANCELLED);
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));

        CustomException ex = assertThrows(CustomException.class,
            () -> service.extendReservation("res-1", 1, authentication));

        assertEquals(ResponseCode.RESERVATION_NOT_IN_USE, ex.getResponseCode());
        }

        @Test
        void extendReservation_shouldThrowRoomBroken_whenRoomIsBroken() {
        Reservation reservation = buildReservation("u-1");
        reservation.getRoom().setStatus(RoomStatus.BROKEN);
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));

        CustomException ex = assertThrows(CustomException.class,
            () -> service.extendReservation("res-1", 1, authentication));

        assertEquals(ResponseCode.ROOM_BROKEN, ex.getResponseCode());
        }

        @Test
        void extendReservation_shouldThrowUserNotFound_whenConnectedUserMissing() {
        Reservation reservation = buildReservation("u-1");
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));
        when(authentication.getName()).thenReturn("x@test.com");
        when(userRepository.findByEmail("x@test.com")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
            () -> service.extendReservation("res-1", 1, authentication));

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
        }

        @Test
        void extendReservation_shouldThrowPermissionDenied_whenOwnerMismatch() {
        Reservation reservation = buildReservation("owner-1");
        User requester = new User();
        requester.setId("requester-1");

        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));
        when(authentication.getName()).thenReturn("req@test.com");
        when(userRepository.findByEmail("req@test.com")).thenReturn(Optional.of(requester));

        CustomException ex = assertThrows(CustomException.class,
            () -> service.extendReservation("res-1", 1, authentication));

        assertEquals(ResponseCode.PERMISSION_DENIED, ex.getResponseCode());
        }

        @Test
        void extendReservation_shouldThrowTimeOverlap_whenRoomConflictExists() {
        Reservation reservation = buildReservation("u-1");
        User user = new User();
        user.setId("u-1");

        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));
        when(authentication.getName()).thenReturn("u@test.com");
        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
        when(reservationRepository.checkOverlapReservation(
            eq("room-1"), any(), any(), any(), eq("res-1"))).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class,
            () -> service.extendReservation("res-1", 1, authentication));

        assertEquals(ResponseCode.RESERVATION_TIME_OVERLAP, ex.getResponseCode());
        }

        @Test
        void extendReservation_shouldThrowUserTimeExceeded_whenDailyLimitExceeded() {
        Reservation reservation = buildReservation("u-1");
        User user = new User();
        user.setId("u-1");

        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));
        when(authentication.getName()).thenReturn("u@test.com");
        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
        when(reservationRepository.checkOverlapReservation(
            eq("room-1"), any(), any(), any(), eq("res-1"))).thenReturn(false);
        when(reservationRepository.getTotalReservedMinutesForUser(eq("u-1"), any())).thenReturn(480L);

        CustomException ex = assertThrows(CustomException.class,
            () -> service.extendReservation("res-1", 1, authentication));

        assertEquals(ResponseCode.USER_TIME_EXCEEDED, ex.getResponseCode());
        }

        @Test
        void extendReservation_shouldUpdateReservation_whenValidRequest() {
        Reservation reservation = buildReservation("u-1");
        User user = new User();
        user.setId("u-1");

        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));
        when(authentication.getName()).thenReturn("u@test.com");
        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
        when(reservationRepository.checkOverlapReservation(
            eq("room-1"), any(), any(), any(), eq("res-1"))).thenReturn(false);
        when(reservationRepository.getTotalReservedMinutesForUser(eq("u-1"), any())).thenReturn(60L);

        service.extendReservation("res-1", 1, authentication);

        assertEquals(LocalDateTime.of(2026, 4, 24, 10, 0), reservation.getEndTime());
        verify(reservationRepository).save(reservation);
        verify(notificationService).noticeExtendReservation(any(List.class));
        verify(realTimeService).addReservation(reservation);
        }
}
