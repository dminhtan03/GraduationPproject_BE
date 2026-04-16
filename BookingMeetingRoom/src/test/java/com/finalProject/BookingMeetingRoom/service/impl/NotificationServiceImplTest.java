package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.NotificationMapper;
import com.finalProject.BookingMeetingRoom.messaging.producer.NotificationProducer;
import com.finalProject.BookingMeetingRoom.model.dto.NotificationDTO;
import com.finalProject.BookingMeetingRoom.model.entity.*;
import com.finalProject.BookingMeetingRoom.model.request.NotificationRequest;
import com.finalProject.BookingMeetingRoom.model.response.SendNoticeRequest;
import com.finalProject.BookingMeetingRoom.repository.NotificationRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationMapper notificationMapper;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private NotificationProducer notificationProducer;
    @Mock private ReservationRepository reservationRepository;
    @Mock private Authentication authentication;


    @InjectMocks private NotificationServiceImpl notificationService;

    private NotificationRequest request;
    private User user;
    private Notification notification;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId("user-id");

        request = new NotificationRequest();
        request.setUserId("user-id");
        request.setTitle("Test");
        request.setContent("Hello");

        notification = new Notification();
        notification.setId("noti-id");
        notification.setUser(user);
        notification.setRead(false);

    }

    @Test
    void sendNotification_ValidRequest_ShouldSaveAndSend() {
        when(userRepository.findById(anyString())).thenReturn(Optional.of(user));
        when(notificationMapper.toEntity(any(NotificationRequest.class))).thenReturn(notification);

        notificationService.sendNotification(List.of(request));

        verify(messagingTemplate).convertAndSend(contains("/topic/notifications/"), eq(request));
        verify(notificationRepository).saveAll(anyList());
    }


    @Test
    void sendNotification_UserNotFound_ShouldThrow() {
        when(userRepository.findById(anyString())).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> notificationService.sendNotification(List.of(request)));

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void getAllNotifications_ValidAuth_ShouldReturnPage() {
        when(authentication.getName()).thenReturn("mail");
        when(userRepository.findByEmail("mail")).thenReturn(Optional.of(user));

        NotificationDTO dto = new NotificationDTO();
        Page<Notification> page = new PageImpl<>(List.of(notification));
        when(notificationRepository.findAllByUser(eq("user-id"), any())).thenReturn(page);
        when(notificationMapper.toDto(any())).thenReturn(dto);

        Page<NotificationDTO> result = notificationService.getAllNotifications(authentication, 0, 10);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void markNotificationAsRead_ValidId_ShouldUpdateRead() {
        when(notificationRepository.findById(anyString())).thenReturn(Optional.of(notification));

        Map<String, String> result = notificationService.markNotificationAsRead("noti-id");
        assertEquals("Notification marked as read successfully", result.get("message"));
        assertTrue(notification.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markNotificationAsRead_NotFound_ShouldThrow() {
        when(notificationRepository.findById(anyString())).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> notificationService.markNotificationAsRead("noti-id"));

        assertEquals(ResponseCode.NOTIFICATION_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void createNotification_UserNotFound_ShouldThrow() {
        when(userRepository.findById(anyString())).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> notificationService.createNotification(request));

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void createNotification_ValidRequest_ShouldSave() {
        when(userRepository.findById(anyString())).thenReturn(Optional.of(user));

        notificationService.createNotification(request);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void sendNotice_ShouldCallProducer() {
        Reservation reservation = new Reservation();
        reservation.setUser(user);

        Room room = new Room();
        room.setLocationCode("A1");
        Floor floor = new Floor();
        floor.setName("F1");
        room.setFloor(floor);
        reservation.setRoom(room);
        reservation.setStartTime(LocalDateTime.now());
        reservation.setEndTime(LocalDateTime.now().plusMinutes(30));

        SendNoticeRequest sendNoticeRequest = new SendNoticeRequest();
        sendNoticeRequest.setTitle("title");
        sendNoticeRequest.setContent("Your reservation %s at from %s to %s in %s...");
        sendNoticeRequest.setReservationList(List.of(reservation));

        notificationService.sendNotice(sendNoticeRequest);

        verify(notificationProducer).sendNotifications(anyList());
    }

    @Test
    void notifyUsersAboutReservation_ShouldCallSendNotice_WhenReservationsNotEmpty() {
        Reservation reservation = mock(Reservation.class);
        Room room = mock(Room.class);
        Floor floor = mock(Floor.class);
        User user = mock(User.class);

        when(reservation.getRoom()).thenReturn(room);
        when(room.getLocationCode()).thenReturn("A1");
        when(room.getFloor()).thenReturn(floor);
        when(floor.getName()).thenReturn("Floor 1");

        when(reservation.getStartTime()).thenReturn(LocalDateTime.now());
        when(reservation.getEndTime()).thenReturn(LocalDateTime.now().plusHours(1));
        when(reservation.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user-id");

        List<Reservation> reservations = List.of(reservation);

        notificationService.noticeSuccessfulReservation(reservations);

        verify(notificationProducer).sendNotifications(anyList());
    }


    @Test
    void notifyUsersAboutReservation_ShouldNotSend_WhenEmptyReservationList() {
        notificationService.noticeSuccessfulReservation(new ArrayList<>());

        verify(notificationProducer, never()).sendNotifications(any());
    }

    @Test
    void remindCheckIn_ShouldCallNotifyUsers_WhenReservationsExist() {
        LocalDateTime current = LocalDateTime.now();
        Reservation reservation = mock(Reservation.class);
        Room room = mock(Room.class);
        Floor floor = mock(Floor.class);
        User user = mock(User.class);

        when(reservation.getRoom()).thenReturn(room);
        when(room.getLocationCode()).thenReturn("B2");
        when(room.getFloor()).thenReturn(floor);
        when(floor.getName()).thenReturn("Floor 2");

        when(reservation.getStartTime()).thenReturn(LocalDateTime.now());
        when(reservation.getEndTime()).thenReturn(LocalDateTime.now().plusMinutes(30));
        when(reservation.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user-id");

        when(reservationRepository.findReservationsToRemindCheckIn(current))
                .thenReturn(List.of(reservation));

        notificationService.remindCheckIn();

        verify(notificationProducer).sendNotifications(anyList());
    }


    @Test
    void noticeFailedReservation_ShouldSendNotification() {
        Reservation reservation = createMockReservation();
        notificationService.noticeFailedReservation(List.of(reservation));
        verify(notificationProducer).sendNotifications(anyList());
    }

    @Test
    void noticeForceCancelReservation_ShouldSendNotification() {
        Reservation reservation = createMockReservation();
        notificationService.noticeForceCancelReservation(List.of(reservation));
        verify(notificationProducer).sendNotifications(anyList());
    }

    @Test
    void noticeOverTimeReservation_ShouldSendNotification() {
        Reservation reservation = createMockReservation();
        notificationService.noticeOverTimeReservation(List.of(reservation));
        verify(notificationProducer).sendNotifications(anyList());
    }

    @Test
    void noticeNoCheckInReservation_ShouldSendNotification() {
        Reservation reservation = createMockReservation();
        notificationService.noticeNoCheckInReservation(List.of(reservation));
        verify(notificationProducer).sendNotifications(anyList());
    }

    @Test
    void noticeExtendReservation_ShouldSendNotification() {
        Reservation reservation = createMockReservation();
        notificationService.noticeExtendReservation(List.of(reservation));
        verify(notificationProducer).sendNotifications(anyList());
    }


    @Test
    void createNotification_UnexpectedException_ShouldThrowInternalServerError() {
        // Giả lập user hợp lệ
        when(userRepository.findById(anyString())).thenReturn(Optional.of(user));

        // Giả lập lỗi bất ngờ khi gọi save
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("Unexpected DB error"));

        CustomException ex = assertThrows(CustomException.class, () ->
                notificationService.createNotification(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }


    @Test
    void markNotificationAsRead_RuntimeException_ShouldThrowCustomException() {
        // Given
        String notificationId = "abc123";
        Notification mockNotification = new Notification();
        mockNotification.setId(notificationId);

        // Mock: findById trả về notification hợp lệ
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(mockNotification));

        // Mock: save() ném RuntimeException
        when(notificationRepository.save(any(Notification.class))).thenThrow(new RuntimeException("DB error"));

        // When & Then
        CustomException ex = assertThrows(CustomException.class, () ->
                notificationService.markNotificationAsRead(notificationId));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());

        // Verify flow
        verify(notificationRepository).findById(notificationId);
        verify(notificationRepository).save(mockNotification);
    }


    @Test
    void getAllNotifications_WhenUnexpectedException_ShouldThrowCustomException() {
        // Giả lập Authentication với email bất kỳ
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("user@example.com");

        // Giả lập userRepository ném exception khi gọi findByEmail
        when(userRepository.findByEmail(anyString()))
                .thenThrow(new RuntimeException("DB error"));

        CustomException ex = assertThrows(CustomException.class, () -> {
            notificationService.getAllNotifications(auth, 0, 10);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }


    @Test
    void sendNotification_WhenUnexpectedException_ShouldThrowCustomException() {
        // Tạo 1 request list mẫu
        NotificationRequest request = new NotificationRequest();
        request.setUserId("some-user-id");
        List<NotificationRequest> requests = List.of(request);

        // Giả lập notificationMapper.toEntity trả về 1 đối tượng Notification bình thường
        when(notificationMapper.toEntity(any(NotificationRequest.class)))
                .thenReturn(new Notification());

        // Giả lập userRepository ném exception khi findById
        when(userRepository.findById(anyString()))
                .thenThrow(new RuntimeException("DB error"));

        CustomException ex = assertThrows(CustomException.class, () -> {
            notificationService.sendNotification(requests);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }








    private Reservation createMockReservation() {
        Reservation reservation = mock(Reservation.class);
        Room room = mock(Room.class);
        Floor floor = mock(Floor.class);
        User user = mock(User.class);

        when(room.getLocationCode()).thenReturn("A1");
        when(room.getFloor()).thenReturn(floor);
        when(floor.getName()).thenReturn("Floor 1");
        when(user.getId()).thenReturn("user-id");
        when(reservation.getRoom()).thenReturn(room);
        when(reservation.getStartTime()).thenReturn(LocalDateTime.now());
        when(reservation.getEndTime()).thenReturn(LocalDateTime.now().plusHours(1));
        when(reservation.getUser()).thenReturn(user);

        return reservation;
    }




}
