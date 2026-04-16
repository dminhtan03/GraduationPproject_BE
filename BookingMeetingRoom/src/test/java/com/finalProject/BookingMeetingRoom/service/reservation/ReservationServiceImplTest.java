package com.finalProject.BookingMeetingRoom.service.reservation;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.FeedbackMapper;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapper;
import com.finalProject.BookingMeetingRoom.mapper.ReservationMapperFacade;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
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
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationServiceImplTest {

    private ReservationServiceImpl newService(
            ReservationRepository reservationRepository,
            ReservationHistoryRepository reservationHistoryRepository,
            RealTimeService realTimeService,
            UserRepository userRepository,
            UserInfoRepository userInfoRepository,
            ReservationMapper reservationMapper,
            RoomRepository roomRepository,
            ReservationMapperFacade reservationMapperFacade,
            ReservationHistoryService reservationHistoryService,
            FeedbackMapper feedbackMapper,
            EmailService emailService,
            NotificationService notificationService,
            AcademicScheduleService academicScheduleService
    ) {
        return new ReservationServiceImpl(
                reservationRepository,
                reservationHistoryRepository,
                realTimeService,
                userRepository,
                userInfoRepository,
                reservationMapper,
                roomRepository,
                reservationMapperFacade,
                reservationHistoryService,
                feedbackMapper,
                emailService,
                notificationService,
                academicScheduleService
        );
    }

    @Test
    void reserveRoomShouldThrowRoomNotFound_whenRoomMissing() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationHistoryRepository reservationHistoryRepository = mock(ReservationHistoryRepository.class);
        RealTimeService realTimeService = mock(RealTimeService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        ReservationMapper reservationMapper = mock(ReservationMapper.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationMapperFacade reservationMapperFacade = mock(ReservationMapperFacade.class);
        ReservationHistoryService reservationHistoryService = mock(ReservationHistoryService.class);
        FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
        EmailService emailService = mock(EmailService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);

        ReservationServiceImpl service = newService(
                reservationRepository,
                reservationHistoryRepository,
                realTimeService,
                userRepository,
                userInfoRepository,
                reservationMapper,
                roomRepository,
                reservationMapperFacade,
                reservationHistoryService,
                feedbackMapper,
                emailService,
                notificationService,
                academicScheduleService
        );

        when(roomRepository.findByIdForUpdate("R404")).thenReturn(Optional.empty());

        ReservationRequest request = new ReservationRequest();
        request.setRoomId("R404");
        request.setStartTime(LocalDateTime.now().plusHours(1));
        request.setEndTime(LocalDateTime.now().plusHours(2));
        request.setPurpose("Họp nhóm 🚀");

        CustomException ex = assertThrows(CustomException.class,
                () -> service.reserveRoom(request, new TestingAuthenticationToken("u@example.com", "pw")));
        assertEquals(ResponseCode.ROOM_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void reserveRoomShouldThrowBookingFunctionLocked_whenUserLockedUntilFuture() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationHistoryRepository reservationHistoryRepository = mock(ReservationHistoryRepository.class);
        RealTimeService realTimeService = mock(RealTimeService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        ReservationMapper reservationMapper = mock(ReservationMapper.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationMapperFacade reservationMapperFacade = mock(ReservationMapperFacade.class);
        ReservationHistoryService reservationHistoryService = mock(ReservationHistoryService.class);
        FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
        EmailService emailService = mock(EmailService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);

        ReservationServiceImpl service = newService(
                reservationRepository,
                reservationHistoryRepository,
                realTimeService,
                userRepository,
                userInfoRepository,
                reservationMapper,
                roomRepository,
                reservationMapperFacade,
                reservationHistoryService,
                feedbackMapper,
                emailService,
                notificationService,
                academicScheduleService
        );

        Room room = new Room();
        room.setId("R1");
        when(roomRepository.findByIdForUpdate("R1")).thenReturn(Optional.of(room));

        User user = new User();
        user.setId("U1");
        user.setBookingLockedUntil(LocalDateTime.now().plusMinutes(30));
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));

        ReservationRequest request = new ReservationRequest();
        request.setRoomId("R1");
        request.setStartTime(LocalDateTime.now().plusHours(1));
        request.setEndTime(LocalDateTime.now().plusHours(2));
        request.setPurpose("Họp nhóm 🚀");

        CustomException ex = assertThrows(CustomException.class,
                () -> service.reserveRoom(request, new TestingAuthenticationToken("u@example.com", "pw")));
        assertEquals(ResponseCode.BOOKING_FUNCTION_LOCKED, ex.getResponseCode());
    }

    @Test
    void reserveRoomShouldThrowUserTimeOverlap_whenUserHasOverlappingReservation() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationHistoryRepository reservationHistoryRepository = mock(ReservationHistoryRepository.class);
        RealTimeService realTimeService = mock(RealTimeService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        ReservationMapper reservationMapper = mock(ReservationMapper.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationMapperFacade reservationMapperFacade = mock(ReservationMapperFacade.class);
        ReservationHistoryService reservationHistoryService = mock(ReservationHistoryService.class);
        FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
        EmailService emailService = mock(EmailService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);

        ReservationServiceImpl service = newService(
                reservationRepository,
                reservationHistoryRepository,
                realTimeService,
                userRepository,
                userInfoRepository,
                reservationMapper,
                roomRepository,
                reservationMapperFacade,
                reservationHistoryService,
                feedbackMapper,
                emailService,
                notificationService,
                academicScheduleService
        );

        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);

        Room room = new Room();
        room.setId("R1");
        when(roomRepository.findByIdForUpdate("R1")).thenReturn(Optional.of(room));

        User user = new User();
        user.setId("U1");
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));

        when(reservationRepository.checkOverlapByUser(eq("U1"), eq(start), eq(end), anyList()))
                .thenReturn(List.of(new Reservation()));

        ReservationRequest request = new ReservationRequest();
        request.setRoomId("R1");
        request.setStartTime(start);
        request.setEndTime(end);
        request.setPurpose("Họp nhóm 🚀");

        CustomException ex = assertThrows(CustomException.class,
                () -> service.reserveRoom(request, new TestingAuthenticationToken("u@example.com", "pw")));
        assertEquals(ResponseCode.USER_TIME_OVERLAP, ex.getResponseCode());
    }

    @Test
    void reserveRoomShouldThrowCannotReserveRoom_whenRoomHasConflict() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationHistoryRepository reservationHistoryRepository = mock(ReservationHistoryRepository.class);
        RealTimeService realTimeService = mock(RealTimeService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        ReservationMapper reservationMapper = mock(ReservationMapper.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationMapperFacade reservationMapperFacade = mock(ReservationMapperFacade.class);
        ReservationHistoryService reservationHistoryService = mock(ReservationHistoryService.class);
        FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
        EmailService emailService = mock(EmailService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);

        ReservationServiceImpl service = newService(
                reservationRepository,
                reservationHistoryRepository,
                realTimeService,
                userRepository,
                userInfoRepository,
                reservationMapper,
                roomRepository,
                reservationMapperFacade,
                reservationHistoryService,
                feedbackMapper,
                emailService,
                notificationService,
                academicScheduleService
        );

        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);

        Room room = new Room();
        room.setId("R1");
        when(roomRepository.findByIdForUpdate("R1")).thenReturn(Optional.of(room));

        User user = new User();
        user.setId("U1");
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));

        when(reservationRepository.checkOverlapByUser(eq("U1"), eq(start), eq(end), anyList()))
                .thenReturn(List.of());

        Reservation existing = new Reservation();
        existing.setStatus(ReservationStatus.RESERVED);
        when(reservationRepository.checkOverlappingReservationsByRoom("R1", start, end))
                .thenReturn(List.of(existing));

        ReservationRequest request = new ReservationRequest();
        request.setRoomId("R1");
        request.setStartTime(start);
        request.setEndTime(end);
        request.setPurpose("Họp nhóm 🚀");

        CustomException ex = assertThrows(CustomException.class,
                () -> service.reserveRoom(request, new TestingAuthenticationToken("u@example.com", "pw")));
        assertEquals(ResponseCode.CANNOT_RESERVE_ROOM, ex.getResponseCode());
    }

    @Test
    void reserveRoomShouldThrowUserNotFound_whenUserMissing() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationHistoryRepository reservationHistoryRepository = mock(ReservationHistoryRepository.class);
        RealTimeService realTimeService = mock(RealTimeService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        ReservationMapper reservationMapper = mock(ReservationMapper.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationMapperFacade reservationMapperFacade = mock(ReservationMapperFacade.class);
        ReservationHistoryService reservationHistoryService = mock(ReservationHistoryService.class);
        FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
        EmailService emailService = mock(EmailService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);

        ReservationServiceImpl service = newService(
                reservationRepository,
                reservationHistoryRepository,
                realTimeService,
                userRepository,
                userInfoRepository,
                reservationMapper,
                roomRepository,
                reservationMapperFacade,
                reservationHistoryService,
                feedbackMapper,
                emailService,
                notificationService,
                academicScheduleService
        );

        Room room = new Room();
        room.setId("R1");
        when(roomRepository.findByIdForUpdate("R1")).thenReturn(Optional.of(room));
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        ReservationRequest request = new ReservationRequest();
        request.setRoomId("R1");
        request.setStartTime(LocalDateTime.now().plusHours(1));
        request.setEndTime(LocalDateTime.now().plusHours(2));
        request.setPurpose("Họp nhóm 🚀");

        CustomException ex = assertThrows(CustomException.class,
                () -> service.reserveRoom(request, new TestingAuthenticationToken("missing@example.com", "pw")));
        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void reserveRoomShouldThrowRoomInAcademicSchedule_whenBusyWithLearning() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationHistoryRepository reservationHistoryRepository = mock(ReservationHistoryRepository.class);
        RealTimeService realTimeService = mock(RealTimeService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        ReservationMapper reservationMapper = mock(ReservationMapper.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationMapperFacade reservationMapperFacade = mock(ReservationMapperFacade.class);
        ReservationHistoryService reservationHistoryService = mock(ReservationHistoryService.class);
        FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
        EmailService emailService = mock(EmailService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);

        ReservationServiceImpl service = newService(
                reservationRepository,
                reservationHistoryRepository,
                realTimeService,
                userRepository,
                userInfoRepository,
                reservationMapper,
                roomRepository,
                reservationMapperFacade,
                reservationHistoryService,
                feedbackMapper,
                emailService,
                notificationService,
                academicScheduleService
        );

        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);

        Room room = new Room();
        room.setId("R1");
        when(roomRepository.findByIdForUpdate("R1")).thenReturn(Optional.of(room));

        User user = new User();
        user.setId("U1");
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));

        when(reservationRepository.checkOverlapByUser(eq("U1"), eq(start), eq(end), anyList()))
                .thenReturn(List.of());
        when(reservationRepository.checkOverlappingReservationsByRoom("R1", start, end))
                .thenReturn(List.of());
        when(academicScheduleService.isRoomBusyWithLearning("R1", start, end)).thenReturn(true);

        ReservationRequest request = new ReservationRequest();
        request.setRoomId("R1");
        request.setStartTime(start);
        request.setEndTime(end);
        request.setPurpose("Họp nhóm 🚀");

        CustomException ex = assertThrows(CustomException.class,
                () -> service.reserveRoom(request, new TestingAuthenticationToken("u@example.com", "pw")));
        assertEquals(ResponseCode.ROOM_IN_ACADEMIC_SCHEDULE, ex.getResponseCode());
    }

    @Test
    void reserveRoomShouldReserveRoomAndReturnResponse_whenValid() {
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReservationHistoryRepository reservationHistoryRepository = mock(ReservationHistoryRepository.class);
        RealTimeService realTimeService = mock(RealTimeService.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserInfoRepository userInfoRepository = mock(UserInfoRepository.class);
        ReservationMapper reservationMapper = mock(ReservationMapper.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationMapperFacade reservationMapperFacade = mock(ReservationMapperFacade.class);
        ReservationHistoryService reservationHistoryService = mock(ReservationHistoryService.class);
        FeedbackMapper feedbackMapper = mock(FeedbackMapper.class);
        EmailService emailService = mock(EmailService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);

        ReservationServiceImpl service = newService(
                reservationRepository,
                reservationHistoryRepository,
                realTimeService,
                userRepository,
                userInfoRepository,
                reservationMapper,
                roomRepository,
                reservationMapperFacade,
                reservationHistoryService,
                feedbackMapper,
                emailService,
                notificationService,
                academicScheduleService
        );

        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(2);

        Room room = new Room();
        room.setId("R1");
        when(roomRepository.findByIdForUpdate("R1")).thenReturn(Optional.of(room));

        User user = new User();
        user.setId("U1");
        user.setBookingLockedUntil(null);
        when(userRepository.findByEmail("u@example.com")).thenReturn(Optional.of(user));

        when(reservationRepository.checkOverlapByUser(eq("U1"), eq(start), eq(end), anyList()))
                .thenReturn(List.of());
        when(reservationRepository.checkOverlappingReservationsByRoom("R1", start, end))
                .thenReturn(List.of());
        when(academicScheduleService.isRoomBusyWithLearning("R1", start, end)).thenReturn(false);

        Reservation mapped = new Reservation();
        mapped.setStartTime(start);
        mapped.setEndTime(end);
        when(reservationMapper.toEntity(any())).thenReturn(mapped);

        ReservationResponse response = new ReservationResponse();
        response.setStatus(ReservationStatus.RESERVED);
        when(reservationMapperFacade.toResponse(any())).thenReturn(response);

        ReservationRequest request = new ReservationRequest();
        request.setRoomId("R1");
        request.setStartTime(start);
        request.setEndTime(end);
        request.setPurpose("Họp nhóm 🚀");

        ReservationResponse res = service.reserveRoom(
                request,
                new TestingAuthenticationToken("u@example.com", "pw")
        );

        assertNotNull(res);
        verify(reservationRepository).save(any(Reservation.class));
        verify(reservationHistoryService).saveHistory(any(), eq("U1"), eq(ReservationStatus.RESERVED), isNull(), any());
        verify(realTimeService).addReservation(any(Reservation.class));
    }
}
