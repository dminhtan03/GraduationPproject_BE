package com.finalProject.BookingMeetingRoom.messaging.producer;

import com.finalProject.BookingMeetingRoom.config.RabbitMQConfig;
import com.finalProject.BookingMeetingRoom.model.request.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendNotifications(List<NotificationRequest> notificationRequests) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                notificationRequests
        );
    }
}