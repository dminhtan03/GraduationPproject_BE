package com.finalProject.BookingMeetingRoom.service.user;

import com.finalProject.BookingMeetingRoom.common.enums.EmailTemplateName;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Role;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.entity.UserOtp;
import com.finalProject.BookingMeetingRoom.model.request.ResendOtpRequest;
import com.finalProject.BookingMeetingRoom.repository.UserOtpRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.EmailService;
import com.finalProject.BookingMeetingRoom.service.RedisService;
import com.finalProject.BookingMeetingRoom.service.impl.UserServiceImpl;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest_resendOtp {

    @Spy
    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserOtpRepository userOtpRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private EmailService emailService;

    private User user;
    private UserOtp userOtp;
    private ResendOtpRequest resendOtpRequest;
    private final String email = "test@example.com";

    @BeforeEach
    public void setup() {
        // Initialize UserInfo
        UserInfo userInfo = new UserInfo();
        userInfo.setId(UUID.randomUUID().toString());
        userInfo.setEmail(email);
        userInfo.setFirstName("John");
        userInfo.setLastName("Doe");
        userInfo.setPhoneNumber("1234567890");
        userInfo.setAddress("123 Main St");
        userInfo.setGender("Male");
        userInfo.setDepartment("IT");
        userInfo.setCreatedAt(LocalDateTime.now());

        Role role = new Role();
        role.setId(UUID.randomUUID().toString());
        role.setName("USER");

        // Initialize User
        user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setPassword("encodedPassword");
        user.setEnabled(false);
        user.setLocked(false);
        user.setLoginCount(0);
        user.setUserInfo(userInfo);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(Set.of(role));
        user.setRefreshTokens(new ArrayList<>());
        user.setReservations(new ArrayList<>());

        // Initialize UserOtp
        userOtp = UserOtp.builder()
                .id(UUID.randomUUID().toString())
                .otpCode("123456")
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .validatedAt(null)
                .isUsed(false)
                .user(user)
                .build();

        // Initialize ResendOtpRequest
        resendOtpRequest = new ResendOtpRequest();
        resendOtpRequest.setEmail(email);

        // Set character and activationUrl to avoid NullPointerException
        ReflectionTestUtils.setField(userService, "character", "0123456789abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(userService, "activationUrl", "http://example.com/activate");
    }

    @Test
    void resendOtp_Success() throws MessagingException {
        // Arrange
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        doReturn("123456").when(userService).generateActivationCode();
        when(userOtpRepository.save(any(UserOtp.class))).thenReturn(userOtp);
        doNothing().when(redisService).setValue(eq("otp:test@example.com"), eq("123456"), eq(5L), eq(TimeUnit.MINUTES));
        doNothing().when(emailService).sendEmail(
                eq(email),
                eq("John Doe"),
                eq(EmailTemplateName.ACTIVATE_ACCOUNT),
                eq("http://example.com/activate"),
                eq("123456"),
                eq("Activate your account")
        );

        // Act
        userService.resendOtp(resendOtpRequest);

        // Assert
        verify(userRepository).findByEmail(eq(email));
        verify(userService).generateActivationCode();
        verify(userOtpRepository).save(any(UserOtp.class));
        verify(redisService).setValue(eq("otp:test@example.com"), eq("123456"), eq(5L), eq(TimeUnit.MINUTES));
        verify(emailService).sendEmail(
                eq(email),
                eq("John Doe"),
                eq(EmailTemplateName.ACTIVATE_ACCOUNT),
                eq("http://example.com/activate"),
                eq("123456"),
                eq("Activate your account")
        );
    }

    @Test
    void resendOtp_UserNotFound_ThrowsUserNotFoundException() throws MessagingException {
        // Arrange
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.resendOtp(resendOtpRequest));
        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        verify(userRepository).findByEmail(eq(email));
        verify(userService, never()).generateAndActivateCode(any());
        verify(userOtpRepository, never()).save(any());
        verify(redisService, never()).setValue(anyString(), anyString(), anyLong(), any());
        verify(userService, never()).sendValidationEmail(any());
    }

    @Test
    void resendOtp_AlreadyActivated_ThrowsAlreadyActivatedException() throws MessagingException {
        // Arrange
        user.setEnabled(true);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.resendOtp(resendOtpRequest));
        assertEquals(ResponseCode.ALREADY_ACTIVATED, exception.getResponseCode());
        verify(userRepository).findByEmail(eq(email));
        verify(userService, never()).generateAndActivateCode(any());
        verify(userOtpRepository, never()).save(any());
        verify(redisService, never()).setValue(anyString(), anyString(), anyLong(), any());
        verify(userService, never()).sendValidationEmail(any());
    }

    @Test
    void resendOtp_GenerateAndActivateCodeThrowsException_ThrowsInternalServerError() throws MessagingException {
        // Arrange
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("Code generation failed")).when(userService).generateAndActivateCode(eq(user));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.resendOtp(resendOtpRequest));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail(eq(email));
        verify(userService).generateAndActivateCode(eq(user));
        verify(userOtpRepository, never()).save(any());
        verify(redisService, never()).setValue(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void resendOtp_SendValidationEmailThrowsMessagingException_ThrowsInternalServerError() throws MessagingException {
        // Arrange
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        doThrow(new MessagingException("Failed to send email")).when(userService).sendValidationEmail(eq(user));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.resendOtp(resendOtpRequest));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail(eq(email));
        verify(userService).sendValidationEmail(eq(user));
        verify(userOtpRepository, never()).save(any());
        verify(redisService, never()).setValue(anyString(), anyString(), anyLong(), any());
    }
}