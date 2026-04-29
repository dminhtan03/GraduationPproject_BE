package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.common.enums.EmailTemplateName;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;

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
    private final UserRepository userRepository;
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
        User user = userRepository.findById(notificationRequest.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + notificationRequest.getUserId()));

        if (user.getUserInfo() == null || user.getUserInfo().getEmail() == null) {
            log.warn("User info or email not found for user: {}", user.getId());
            return;
        }

        String firstName = Optional.ofNullable(user.getUserInfo().getFirstName()).orElse("");
        String lastName = Optional.ofNullable(user.getUserInfo().getLastName()).orElse("");
        String fullName = (firstName + " " + lastName).trim();
        if (fullName.isEmpty()) fullName = user.getUsername();

        try {
            sendEmail(
                    user.getUserInfo().getEmail(),
                    fullName,
                    EmailTemplateName.RESERVATION_STATUS,
                    "http://localhost:5173/login",
                    notificationRequest.getContent(),
                    notificationRequest.getTitle()
            );
        } catch (MessagingException e) {
            log.error("Failed to send notification email for user: {}", user.getId(), e);
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
     /**
     * NEW METHOD: Sends an invitation email to a specific user by their ID.
     */
    public void sendInviteEmail(NotificationRequest request) {
        try {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));

            if (user.getUserInfo() == null || user.getUserInfo().getEmail() == null) {
                log.warn("User email not found for invitation: {}", user.getId());
                return;
            }

            String firstName = Optional.ofNullable(user.getUserInfo().getFirstName()).orElse("");
            String lastName = Optional.ofNullable(user.getUserInfo().getLastName()).orElse("");
            String fullName = (firstName + " " + lastName).trim();
            if (fullName.isEmpty()) fullName = user.getUsername();

            sendEmail(
                    user.getUserInfo().getEmail(),
                    fullName,
                    EmailTemplateName.RESERVATION_STATUS,
                    "http://localhost:5173/login",
                    request.getContent(),
                    request.getTitle()
            );
        } catch (Exception e) {
            log.error("Failed to send invitation email: {}", e.getMessage());
        }
    }

}
