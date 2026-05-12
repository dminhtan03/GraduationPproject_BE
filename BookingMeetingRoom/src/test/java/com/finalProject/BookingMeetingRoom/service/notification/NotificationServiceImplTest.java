package com.finalProject.BookingMeetingRoom.service.notification;

import com.finalProject.BookingMeetingRoom.service.impl.NotificationServiceImpl;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.NotificationMapper;
import com.finalProject.BookingMeetingRoom.messaging.producer.NotificationProducer;
import com.finalProject.BookingMeetingRoom.model.dto.NotificationDTO;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Notification;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.request.NotificationRequest;
import com.finalProject.BookingMeetingRoom.repository.NotificationRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private NotificationProducer notificationProducer;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void sendNotification_shouldMapAndSaveAndSendRealtime() {
        NotificationRequest req1 = new NotificationRequest();
        req1.setUserId(" user-1 ");
        req1.setTitle("t1");
        req1.setContent("c1");
        req1.setSendEmail(true);

        NotificationRequest req2 = new NotificationRequest();
        req2.setUserId("user-2");
        req2.setTitle("t2");
        req2.setContent("c2");
        req2.setSendEmail(false);

        Notification noti1 = new Notification();
        Notification noti2 = new Notification();
        User user1 = new User();
        user1.setId("user-1");
        User user2 = new User();
        user2.setId("user-2");

        when(notificationMapper.toEntity(req1)).thenReturn(noti1);
        when(notificationMapper.toEntity(req2)).thenReturn(noti2);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user1));
        when(userRepository.findById("user-2")).thenReturn(Optional.of(user2));

        notificationService.sendNotification(List.of(req1, req2));

        verify(messagingTemplate).convertAndSend("/topic/notifications/ user-1 ", req1);
        verify(messagingTemplate).convertAndSend("/topic/notifications/user-2", req2);
        verify(emailService).sendEmailStatusReservation(req1);
        verify(emailService, never()).sendEmailStatusReservation(req2);

        ArgumentCaptor<List<Notification>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(listCaptor.capture());
        assertEquals(2, listCaptor.getValue().size());
        assertEquals(user1, listCaptor.getValue().get(0).getUser());
        assertEquals(user2, listCaptor.getValue().get(1).getUser());
    }

    @Test
    void sendNotification_shouldThrowUserNotFound_whenUserMissing() {
        NotificationRequest req = new NotificationRequest();
        req.setUserId("u404");

        when(notificationMapper.toEntity(req)).thenReturn(new Notification());
        when(userRepository.findById("u404")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> notificationService.sendNotification(List.of(req)));

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void sendNotification_shouldThrowInternalServerError_whenUnexpectedException() {
        NotificationRequest req = new NotificationRequest();
        req.setUserId("u1");

        when(notificationMapper.toEntity(req)).thenReturn(new Notification());
        doThrow(new RuntimeException("db")).when(userRepository).findById("u1");

        CustomException ex = assertThrows(CustomException.class,
                () -> notificationService.sendNotification(List.of(req)));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void getAllNotifications_shouldMapAndAttachCurrentReservationStatus() {
        User user = new User();
        user.setId("u1");

        Notification notification = new Notification();
        notification.setReservationId("res-1");

        NotificationDTO dto = new NotificationDTO();
        dto.setReservationId("res-1");

        Reservation reservation = new Reservation();
        reservation.setStatus(ReservationStatus.IN_USE);

        when(authentication.getName()).thenReturn("mail@test.com");
        when(userRepository.findByEmail("mail@test.com")).thenReturn(Optional.of(user));
        when(notificationRepository.findAllByUser(eq("u1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification)));
        when(notificationMapper.toDto(notification)).thenReturn(dto);
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));

        Page<NotificationDTO> result = notificationService.getAllNotifications(authentication, 0, 10);

        assertEquals(1, result.getTotalElements());
        assertEquals(ReservationStatus.IN_USE, result.getContent().get(0).getReservationStatusAtNow());
    }

    @Test
    void getAllNotifications_shouldThrowInternalServerError_whenUserNotFound() {
        when(authentication.getName()).thenReturn("missing@mail.com");
        when(userRepository.findByEmail("missing@mail.com")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> notificationService.getAllNotifications(authentication, 0, 10));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void markNotificationAsRead_shouldUpdateReadFlag() {
        Notification notification = new Notification();
        notification.setId("n1");
        notification.setRead(false);

        when(notificationRepository.findById("n1")).thenReturn(Optional.of(notification));

        Map<String, String> result = notificationService.markNotificationAsRead("n1");

        assertEquals("Notification marked as read successfully", result.get("message"));
        assertEquals(true, notification.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markNotificationAsRead_shouldThrowNotificationNotFound() {
        when(notificationRepository.findById("n404")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> notificationService.markNotificationAsRead("n404"));

        assertEquals(ResponseCode.NOTIFICATION_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void createNotification_shouldSaveSuccessfully() {
        User user = new User();
        user.setId("u1");

        NotificationRequest request = new NotificationRequest();
        request.setUserId("u1");
        request.setTitle("hello");
        request.setContent("world");

        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        notificationService.createNotification(request);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void createNotification_shouldThrowUserNotFound_whenMissingUser() {
        NotificationRequest request = new NotificationRequest();
        request.setUserId("u404");

        when(userRepository.findById("u404")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> notificationService.createNotification(request));

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void noticeMethods_shouldSendNotificationsViaProducer() {
        User user = new User();
        user.setId("u1");

        Floor floor = new Floor();
        floor.setName("Floor 1");

        Room room = new Room();
        room.setLocationCode("A1-01");
        room.setFloor(floor);

        Reservation reservation = new Reservation();
        reservation.setId("res-1");
        reservation.setRoom(room);
        reservation.setUser(user);
        reservation.setStartTime(LocalDateTime.of(2026, 4, 20, 10, 0));
        reservation.setEndTime(LocalDateTime.of(2026, 4, 20, 11, 0));

        notificationService.noticeSuccessfulReservation(List.of(reservation));
        notificationService.noticeExtendReservation(List.of(reservation));
        notificationService.noticeCancelReservation(List.of(reservation));
        notificationService.noticeFailedReservation(List.of(reservation));
        notificationService.noticeForceCancelReservation(List.of(reservation));
        notificationService.noticeNoCheckInReservation(List.of(reservation));
        notificationService.noticeOverTimeReservation(List.of(reservation));
        notificationService.noticeReturnRoomReservation(List.of(reservation));

        verify(notificationProducer, org.mockito.Mockito.times(8)).sendNotifications(anyList());
    }

    @Test
    void notifyUsersAboutReservation_shouldSkipWhenEmpty() {
        notificationService.noticeSuccessfulReservation(List.of());
        verify(notificationProducer, never()).sendNotifications(anyList());
    }

    @Test
    void remindCheckIn_shouldLoadReservationsAndSendNotice() {
        User user = new User();
        user.setId("u1");

        Floor floor = new Floor();
        floor.setName("Floor X");

        Room room = new Room();
        room.setLocationCode("X-01");
        room.setFloor(floor);

        Reservation reservation = new Reservation();
        reservation.setId("res-x");
        reservation.setUser(user);
        reservation.setRoom(room);
        reservation.setStartTime(LocalDateTime.of(2026, 4, 20, 9, 0));
        reservation.setEndTime(LocalDateTime.of(2026, 4, 20, 10, 0));

        when(reservationRepository.findReservationsToRemindCheckIn(any(LocalDateTime.class)))
                .thenReturn(List.of(reservation));

        notificationService.remindCheckIn();

        verify(reservationRepository).findReservationsToRemindCheckIn(any(LocalDateTime.class));
        verify(notificationProducer).sendNotifications(anyList());
    }
}


