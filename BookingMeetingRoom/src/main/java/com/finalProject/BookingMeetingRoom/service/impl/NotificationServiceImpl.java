package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.NotificationMapper;
import com.finalProject.BookingMeetingRoom.messaging.producer.NotificationProducer;
import com.finalProject.BookingMeetingRoom.model.dto.NotificationDTO;
import com.finalProject.BookingMeetingRoom.model.entity.Notification;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.request.NotificationRequest;
import com.finalProject.BookingMeetingRoom.model.response.SendNoticeRequest;
import com.finalProject.BookingMeetingRoom.repository.NotificationRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.EmailService;
import com.finalProject.BookingMeetingRoom.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationProducer notificationProducer;
    private final ReservationRepository reservationRepository;
    private final EmailService emailService;

    /**
     * Sends notifications based on the provided list of NotificationRequest.
     * It maps each request to a Notification entity, associates it with a User,
     * and sends real-time notifications if applicable.
     *
     * @param notificationRequestList List of NotificationRequest to be processed
     */
    @Override
    public void sendNotification(List<NotificationRequest> notificationRequestList) {
        try {

            List<Notification> notificationList = notificationRequestList.stream()
                    .map(request -> {
                        Notification notification = notificationMapper.toEntity(request);

                        User user = userRepository.findById(request.getUserId())
                                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
                        notification.setUser(user);

                        sendRealtimeNotification(request);

                        if (request.sendEmail) {
                            emailService.sendEmailStatusReservation(request);
                        }

                        return notification;
                    })
                    .toList();

            notificationRepository.saveAll(notificationList);

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while sending notifications", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Sends a real-time notification to a specific user via WebSocket.
     *
     * @param notificationRequest The request containing notification details
     */
    public void sendRealtimeNotification(NotificationRequest notificationRequest) {
        String topic = "/topic/notifications/" + notificationRequest.getUserId();
        log.info("Sending real-time notification to topic: {}", topic);
        messagingTemplate.convertAndSend(topic, notificationRequest);
    }

    /**
     * Retrieves all notifications for the authenticated user with pagination.
     *
     * @param authentication The authentication object containing user details
     * @param page           The page number to retrieve
     * @param size           The number of items per page
     * @return A paginated list of NotificationDTO
     */
    @Override
    public Page<NotificationDTO> getAllNotifications(Authentication authentication, int page, int size) {
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Pageable pageable = PageRequest.of(page, size);

            Page<Notification> notifications = notificationRepository.findAllByUser(user.getId(), pageable);

            return notifications.map(notification -> {
                NotificationDTO dto = notificationMapper.toDto(notification);

                if (dto.getReservationId() != null) {
                    reservationRepository.findById(dto.getReservationId())
                            .ifPresent(reservation -> dto.setReservationStatusAtNow(reservation.getStatus()));
                }

                return dto;
            });
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while get notifications", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Marks a notification as read by its ID.
     *
     * @param notificationId The ID of the notification to mark as read
     * @return A map containing a success message
     */
    @Override
    public Map<String, String> markNotificationAsRead(String notificationId) {
        try {
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new CustomException(ResponseCode.NOTIFICATION_NOT_FOUND));

            notification.setRead(true);
            notificationRepository.save(notification);

            return Map.of("message", "Notification marked as read successfully");
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while mark notifications", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Creates a new notification based on the provided NotificationRequest.
     * It associates the notification with a User and saves it to the repository.
     *
     * @param notificationRequest The request containing notification details
     */
    @Override
    public void createNotification(NotificationRequest notificationRequest) {
        try {
            Notification notification = new Notification();
            notification.setTitle(notificationRequest.getTitle());
            notification.setContent(notificationRequest.getContent());
            notification.setRead(false);
            notification.setUser(userRepository.findById(notificationRequest.getUserId())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND)));

            notificationRepository.save(notification);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while create notifications", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Notifies users about various reservation statuses by sending them notifications.
     * It formats the content of the notification based on the reservation details.
     *
     * @param reservations       List of reservations to notify users about
     * @param title              Title of the notification
     * @param contentTemplate    Template for the notification content
     * @param logMessage         Log message to indicate the notification process
     * @param sendEmail          Whether to send an email notification
     */
    public void notifyUsersAboutReservation(
            List<Reservation> reservations,
            String title,
            String contentTemplate,
            String logMessage,
            boolean sendEmail
    ) {
        if (reservations.isEmpty()) {
            log.info("No reservations to notify.");
            return;
        }

        SendNoticeRequest sendNoticeRequest = new SendNoticeRequest();
        sendNoticeRequest.setReservationList(reservations);
        sendNoticeRequest.setTitle(title);
        sendNoticeRequest.setContent(contentTemplate);
        sendNoticeRequest.setSendEmail(sendEmail);

        sendNotice(sendNoticeRequest);
    }

    /**
     * Notifies users about successful reservations by sending them notifications.
     * It formats the content of the notification based on the reservation details.
     *
     * @param successfulReservations List of successful reservations to notify users about
     */
    public void noticeSuccessfulReservation(List<Reservation> successfulReservations) {
        notifyUsersAboutReservation(
                successfulReservations,
                "Reservation Successful",
                "Your reservation %s at from %s to %s in %s has been successfully processed. Enjoy your time!",
                "Notifying users about successful reservations...",
                true
        );
    }

    /**
     * Notifies users about failed reservations by sending them notifications.
     * It formats the content of the notification based on the reservation details.
     *
     * @param failedReservations List of failed reservations to notify users about
     */
    public void noticeFailedReservation(List<Reservation> failedReservations) {
        notifyUsersAboutReservation(
                failedReservations,
                "Reservation Failed",
                "Your reservation %s at from %s to %s in %s could not be processed. Please try again later.",
                "Notifying users about failed reservations...",
                true
        );
    }

    /**
     * Notifies users about force cancelled reservations by sending them notifications.
     * It formats the content of the notification based on the reservation details.
     *
     * @param forceCancelledReservations List of force cancelled reservations to notify users about
     */
    public void noticeForceCancelReservation(List<Reservation> forceCancelledReservations) {
        notifyUsersAboutReservation(
                forceCancelledReservations,
                "Reservation Force Cancelled",
                "Your reservation %s at from %s to %s in %s has been force cancelled. Please contact support for more details.",
                "Notifying users about force cancelled reservations...",
                true
        );
    }

    /**
     * Notifies users about overtime reservations by sending them notifications.
     * It formats the content of the notification based on the reservation details.
     *
     * @param overTimeReservations List of overtime reservations to notify users about
     */
    public void noticeOverTimeReservation(List<Reservation> overTimeReservations) {
        notifyUsersAboutReservation(
                overTimeReservations,
                "Overtime Reservation",
                "Your reservation %s at from %s to %s in %s has exceeded the allowed time. Please check your room.",
                "Notifying users about overtime reservations...",
                true
        );
    }

    /**
     * Notifies users about no check-in reservations by sending them notifications.
     * It formats the content of the notification based on the reservation details.
     *
     * @param noCheckInReservations List of reservations that have not been checked in
     */
    public void noticeNoCheckInReservation(List<Reservation> noCheckInReservations) {
        notifyUsersAboutReservation(
                noCheckInReservations,
                "No Check-in Reservation",
                "Your reservation %s at from %s to %s in %s has not been checked in. Please check your room.",
                "Notifying users about no check-in reservations...",
                true
        );
    }

    /**
     * Notifies users about extended reservations by sending them notifications.
     * It formats the content of the notification based on the reservation details.
     *
     * @param extendedReservations List of reservations that have been extended
     */
    public void noticeExtendReservation(List<Reservation> extendedReservations) {
        notifyUsersAboutReservation(
                extendedReservations,
                "Reservation Extended",
                "Your reservation %s at from %s to %s in %s has been successfully extended. Enjoy your time!",
                "Notifying users about extended reservations...",
                false
        );
    }

    /**
     * Reminds users to check-in for their reservations that are about to start.
     * It retrieves reservations that need a check-in reminder and sends notifications.
     */
    // start update remindCheckIn to use current time
    @Override
    public void remindCheckIn() {
        log.info("Reminding users to check-in...");

        List<Reservation> reservations = reservationRepository.findReservationsToRemindCheckIn(java.time.LocalDateTime.now());
    // end update remindCheckIn to use current time

        notifyUsersAboutReservation(
                reservations,
                "Reminder: Check-in for your reservation",
                "You have a reservation %s at from %s to %s in %s starting in 15 minutes. Please check-in to confirm your room.",
                "Notifying users about check-in reminders...",
                true
        );
    }

    /**
     * Sends a notice to users based on the provided SendNoticeRequest.
     * It formats the content of the notification based on the reservation details
     * and sends notifications to each user associated with the reservations.
     *
     * @param sendNoticeRequest The request containing reservation list, title, content, and email flag
     */
    public void sendNotice(SendNoticeRequest sendNoticeRequest) {

        List<NotificationRequest> notificationRequestList = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

        for (Reservation reservation : sendNoticeRequest.getReservationList()) {

            String roomName = reservation.getRoom().getLocationCode();
            String startTime = reservation.getStartTime().format(formatter);
            String endTime = reservation.getEndTime().format(formatter);
            String areaName = reservation.getRoom().getFloor().getName();

            String customizedContent = String.format(
                    sendNoticeRequest.getContent(),
                    roomName, startTime, endTime, areaName
            );

            NotificationRequest notificationRequest = new NotificationRequest();
            notificationRequest.setContent(customizedContent);
            notificationRequest.setTitle(sendNoticeRequest.getTitle());
            notificationRequest.setSendEmail(sendNoticeRequest.isSendEmail());
            notificationRequest.setUserId(reservation.getUser().getId());
            notificationRequest.setReservationId(reservation.getId());
            notificationRequestList.add(notificationRequest);

        }

        notificationProducer.sendNotifications(notificationRequestList);

    }

}
