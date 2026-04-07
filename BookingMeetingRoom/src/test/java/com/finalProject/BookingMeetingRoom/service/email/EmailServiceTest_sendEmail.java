package com.finalProject.BookingMeetingRoom.service.email;

import com.finalProject.BookingMeetingRoom.common.enums.EmailTemplateName;
import com.finalProject.BookingMeetingRoom.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest_sendEmail {

    @InjectMocks
    private EmailService emailService;

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private SpringTemplateEngine emailTemplateEngine;

    @Mock
    private MimeMessage mimeMessage;

    @Mock
    private MimeMessageHelper mimeMessageHelper;

    @Test
    void sendEmail_Success_WithNullTemplateName() throws MessagingException {
        // Arrange
        String to = "test@example.com";
        String username = "JohnDoe";
        String confirmationUrl = "http://example.com/confirm";
        String activateCode = "123456";
        String subject = "Confirm Your Email";

        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(emailTemplateEngine.process(eq("confirm-email"), any(Context.class)))
                .thenReturn("<html>Email Content</html>");

        // Act
        emailService.sendEmail(to, username, null, confirmationUrl, activateCode, subject);

        // Assert
        verify(javaMailSender).createMimeMessage();
        verify(emailTemplateEngine).process(eq("confirm-email"), any(Context.class));
        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_Success_WithActivateAccountTemplate() throws MessagingException {
        // Arrange
        String to = "test@example.com";
        String username = "JohnDoe";
        String confirmationUrl = "http://example.com/confirm";
        String activateCode = "123456";
        String subject = "Activate Your Account";

        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(emailTemplateEngine.process(eq("activate_account"), any(Context.class)))
                .thenReturn("<html>Email Content</html>");

        // Act
        emailService.sendEmail(to, username, EmailTemplateName.ACTIVATE_ACCOUNT,
                confirmationUrl, activateCode, subject);

        // Assert
        verify(javaMailSender).createMimeMessage();
        verify(emailTemplateEngine).process(eq("activate_account"), any(Context.class));
        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_CreateMimeMessageThrowsMessagingException_ThrowsException() {
        // Arrange
        String to = "test@example.com";
        String username = "JohnDoe";
        String confirmationUrl = "http://example.com/confirm";
        String activateCode = "123456";
        String subject = "Subject";

        RuntimeException cause = new RuntimeException("Failed to create MimeMessage");
        when(javaMailSender.createMimeMessage()).thenThrow(cause);

        // Act & Assert
        MessagingException exception = assertThrows(MessagingException.class, () ->
                emailService.sendEmail(to, username, null, confirmationUrl, activateCode, subject));

        verify(javaMailSender).createMimeMessage();
        verify(emailTemplateEngine, never()).process(anyString(), any(Context.class));
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_ProcessTemplateThrowsException_ThrowsMessagingException() throws MessagingException {
        // Arrange
        String to = "test@example.com";
        String username = "JohnDoe";
        String confirmationUrl = "http://example.com/confirm";
        String activateCode = "123456";
        String subject = "Subject";

        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(emailTemplateEngine.process(eq("confirm-email"), any(Context.class)))
                .thenThrow(new RuntimeException("Template processing failed"));

        // Act & Assert
        MessagingException exception = assertThrows(MessagingException.class, () ->
                emailService.sendEmail(to, username, null, confirmationUrl, activateCode, subject));
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertEquals("Template processing failed", exception.getCause().getMessage());
        verify(javaMailSender).createMimeMessage();
        verify(emailTemplateEngine).process(eq("confirm-email"), any(Context.class));
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_SendMimeMessageThrowsMessagingException_ThrowsException() throws MessagingException {
        // Arrange
        String to = "test@example.com";
        String username = "JohnDoe";
        String confirmationUrl = "http://example.com/confirm";
        String activateCode = "123456";
        String subject = "Subject";

        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(emailTemplateEngine.process(eq("confirm-email"), any(Context.class)))
                .thenReturn("<html>Email Content</html>");
        doThrow(new MailSendException("Failed to send email"))
                .when(javaMailSender).send(any(MimeMessage.class));

        // Act & Assert
        MessagingException exception = assertThrows(MessagingException.class, () ->
                emailService.sendEmail(to, username, null, confirmationUrl, activateCode, subject));

        assertEquals("Failed to send email", exception.getMessage());
        verify(javaMailSender).createMimeMessage();
        verify(emailTemplateEngine).process(eq("confirm-email"), any(Context.class));
        verify(javaMailSender).send(any(MimeMessage.class));
    }

}