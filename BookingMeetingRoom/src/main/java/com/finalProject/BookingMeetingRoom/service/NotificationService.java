package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.dto.NotificationDTO;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.request.NotificationRequest;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

public interface NotificationService {

    Page<NotificationDTO> getAllNotifications(Authentication authentication, int page, int size);

    Map<String, String> markNotificationAsRead(String notificationId);

    void createNotification(NotificationRequest notificationRequest) ;

    void sendNotification(List<NotificationRequest> notificationRequestList) ;

    void noticeSuccessfulReservation(List<Reservation> successfulReservations);

    void noticeFailedReservation(List<Reservation> failedReservations);

    void noticeForceCancelReservation(List<Reservation> forceCancelledReservations);

    void noticeOverTimeReservation(List<Reservation> overTimeReservations);

    void noticeCancelReservation(List<Reservation> cancelledReservations);

    void noticeNoCheckInReservation(List<Reservation> noCheckInReservations);
    
    void noticeReturnRoomReservation(List<Reservation> returnRoomReservations);

    void noticeExtendReservation(List<Reservation> extendedReservations);

    void remindCheckIn();

}
