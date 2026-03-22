package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.common.enums.EmailTemplateName;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import com.finalProject.BookingMeetingRoom.model.request.NotificationRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine emailTemplateEngine;
    private final ReservationRepository reservationRepository;
    @Value("${spring.mail.username}")
    private String fromAddress;

    @Async
    public void sendEmail(
            String to,
            String username,
            EmailTemplateName emailTemplateName,
            String confirmationUrl,
            String activateCode,
            String subject
    ) throws MessagingException {
        String templateName;

        if(emailTemplateName == null) {
            templateName = "confirm-email";
        } else {
            templateName = emailTemplateName.getName();
        }

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                MimeMessageHelper.MULTIPART_MODE_MIXED,
                UTF_8.name()
        );

        Map<String, Object> properties = new HashMap<>();
        properties.put("username", username);
        properties.put("confirmationUrl", confirmationUrl);
        properties.put("activateCode", activateCode);

        Context context = new Context();
        context.setVariables(properties);

        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);

        String template = emailTemplateEngine.process(templateName, context);
        helper.setText(template, true);

        javaMailSender.send(mimeMessage);
    }

    public void sendEmailStatusReservation(NotificationRequest notificationRequest) {
        Reservation reservation = reservationRepository.findById(notificationRequest.getReservationId())
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        User user = reservation.getUser();
        if (user == null || user.getUserInfo().getEmail() == null) {
            log.warn("User or email not found for reservation: {}", reservation.getId());
            return;
        }

        String firstName = Optional.ofNullable(user.getUserInfo().getFirstName()).orElse("");
        String lastName = Optional.ofNullable(user.getUserInfo().getLastName()).orElse("");
        String fullName = (firstName + " " + lastName).trim();

        try {
            sendEmail(
                    user.getUserInfo().getEmail(),
                    fullName,
                    EmailTemplateName.RESERVATION_STATUS,
                    "",
                    notificationRequest.getContent(),
                    notificationRequest.getTitle()
            );
        } catch (MessagingException e) {
            log.error("Failed to send reservation status email for reservation: {}", reservation.getId(), e);
        }
    }

    @Async
    public void sendForceCancelEmail(String to, String username, String reason, String subject) throws MessagingException {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                MimeMessageHelper.MULTIPART_MODE_MIXED,
                UTF_8.name()
        );

        Map<String, Object> properties = new HashMap<>();
        properties.put("username", username);
        properties.put("reason", reason);

        Context context = new Context();
        context.setVariables(properties);

        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);

        String template = emailTemplateEngine.process(EmailTemplateName.FORCE_CANCEL.getName(), context);
        helper.setText(template, true);

        javaMailSender.send(mimeMessage);
    }

}
