package com.finalProject.BookingMeetingRoom.service.user;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.entity.UserOtp;
import com.finalProject.BookingMeetingRoom.model.request.ForgotPasswordRequest;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest_handleForgotPassword {

    @InjectMocks
    @Spy
    private UserServiceImpl userService;

    @Mock
    private EmailService emailService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private UserOtpRepository userOtpRepository;

    private ForgotPasswordRequest request;
    private ResendOtpRequest resendOtpRequest;
    private User user;
    private String email = "<EMAIL>";
    @BeforeEach
    public void setup() {
        // Initialize UserInfo
        UserInfo userInfo = new UserInfo();
        userInfo.setId(UUID.randomUUID().toString());
        userInfo.setEmail("test@example.com");
        userInfo.setFirstName("John");
        userInfo.setLastName("Doe");
        userInfo.setPhoneNumber("1234567890");
        userInfo.setAddress("123 Main St");
        userInfo.setGender("Male");
        userInfo.setDepartment("IT");
        userInfo.setCreatedAt(LocalDateTime.now());

        // Initialize User
        user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setPassword("encodedPassword");
        user.setEnabled(true);
        user.setLocked(false);
        user.setLoginCount(0);
        user.setUserInfo(userInfo);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>());
        user.setRefreshTokens(new ArrayList<>());
        user.setReservations(new ArrayList<>());

        // Initialize ForgotPasswordRequest
        request = new ForgotPasswordRequest();
        request.setEmail("test@example.com");
    }

    @Test
    void handleForgotPassword_Success() throws MessagingException {
        // Arrange
        User user = mock(User.class);
        UserInfo userInfo = mock(UserInfo.class);

        when(user.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getEmail()).thenReturn("test@example.com");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userService.generateAndActivateCode(user)).thenReturn("123456");

        doNothing().when(redisService).setValue(anyString(), any(), anyLong(), any());
        doNothing().when(userService).sendValidationEmail(user);

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("test@example.com");

        // Act
        userService.handleForgotPassword(request);

        // Assert
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).setValue("otp:test@example.com", "123456", 5L, TimeUnit.MINUTES);
        verify(userService).sendValidationEmail(user);
        verify(userOtpRepository).save(any(UserOtp.class));

    }

    @Test
    void handleForgotPassword_UserNotFound_ThrowsUserNotFoundException() throws MessagingException {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.handleForgotPassword(request));
        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(userService, never()).generateAndActivateCode(any());
        verify(redisService, never()).setValue(anyString(), anyString(), anyLong(), any());
        verify(userService, never()).sendValidationEmail(any());
    }

    @Test
    void handleForgotPassword_GenerateAndActivateCodeThrowsException_ThrowsInternalServerError() throws MessagingException {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("Failed to generate OTP"))
                .when(userService).generateAndActivateCode(any(User.class));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> userService.handleForgotPassword(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(userRepository).findByEmail("test@example.com");
        verify(userService).generateAndActivateCode(any(User.class));
        verify(userService, never()).sendValidationEmail(any(User.class));
    }


    @Test
    void handleForgotPassword_SendValidationEmailThrowsMessagingException_ThrowsInternalServerError() throws MessagingException {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userService.generateAndActivateCode(user)).thenReturn("123456");
        doNothing().when(redisService).setValue(eq("otp:test@example.com"), eq("123456"), eq(5L), eq(TimeUnit.MINUTES));
        doThrow(new MessagingException("Failed to send email")).when(userService).sendValidationEmail(user);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.handleForgotPassword(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).setValue("otp:test@example.com", "123456", 5L, TimeUnit.MINUTES);
        verify(userService).sendValidationEmail(user);
    }
}
