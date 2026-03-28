package com.finalProject.BookingMeetingRoom.messaging.consumer;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.finalProject.BookingMeetingRoom.config.RabbitMQConfig;
import com.finalProject.BookingMeetingRoom.model.request.NotificationRequest;
import com.finalProject.BookingMeetingRoom.service.NotificationService;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void handleReservationCreated(NotificationRequest[] notificationRequests) {
        int size = notificationRequests == null ? 0 : notificationRequests.length;
        log.info(">>>> ĐÃ NHẬN ĐƯỢC {} TIN NHẮN TỪ RABBITMQ", size);
        try {
            notificationService.sendNotification(notificationRequests == null ? List.of() : List.of(notificationRequests));
        } catch (Exception e) {
            log.error("Failed to process RabbitMQ notification message. Message will not be requeued.", e);
        }
    }
}
