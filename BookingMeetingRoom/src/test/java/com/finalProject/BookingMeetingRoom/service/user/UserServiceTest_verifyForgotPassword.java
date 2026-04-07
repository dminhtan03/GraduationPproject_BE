package com.finalProject.BookingMeetingRoom.service.user;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.entity.UserOtp;
import com.finalProject.BookingMeetingRoom.model.request.ForgotPasswordVerifyRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest_verifyForgotPassword {

    @InjectMocks
    @Spy
    private UserServiceImpl userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserOtpRepository userOtpRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    private ForgotPasswordVerifyRequest request;
    private User user;
    private UserInfo userInfo;
    private UserOtp userOtp;
    private String activationUrl = "http://localhost:1080/activate-account";

    @BeforeEach
    public void setup() {
        // Initialize UserInfo
        userInfo = new UserInfo();
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
        user.setLocked(true);
        user.setLoginCount(0);
        user.setUserInfo(userInfo);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>());
        user.setRefreshTokens(new ArrayList<>());
        user.setReservations(new ArrayList<>());

        // Initialize UserOtp
        userOtp = UserOtp.builder()
                .id(UUID.randomUUID().toString())
                .otpCode("123456")
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .validatedAt(null)
                .isUsed(false)
                .user(user)
                .build();

        // Initialize ForgotPasswordVerifyRequest
        request = new ForgotPasswordVerifyRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");

        // Initialize character
        ReflectionTestUtils.setField(userService, "character", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
    }

    @Test
    void verifyForgotPassword_SuccessWithRedis() throws MessagingException {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:test@example.com")).thenReturn("123456");
        when(userService.generateRandomPassword()).thenReturn("newPassword");
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        doNothing().when(redisService).delete("otp:test@example.com");
        doNothing().when(userService).sendChangePasswordEmail(user, "newPassword");

        // Act
        userService.verifyForgotPassword(request);

        // Assert
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).getValue("otp:test@example.com");
        verify(redisService).delete("otp:test@example.com");
        verify(userOtpRepository, never()).findValidOtp(anyString(), anyString());
        verify(userService).generateRandomPassword();
        verify(passwordEncoder).encode("newPassword");
        verify(userRepository).save(user);
        verify(userService).sendChangePasswordEmail(user, "newPassword");
        assertEquals("newEncodedPassword", user.getPassword());
        assertFalse(user.isLocked());
    }

    @Test
    void verifyForgotPassword_SuccessWithDatabase() throws MessagingException {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:test@example.com")).thenReturn(null);
        when(userOtpRepository.findValidOtp("test@example.com", "otp:test@example.com")).thenReturn(Optional.of(userOtp));
        when(userService.generateRandomPassword()).thenReturn("newPassword");
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userOtpRepository.save(any(UserOtp.class))).thenReturn(userOtp);
        doNothing().when(userService).sendChangePasswordEmail(user, "newPassword");

        // Act
        userService.verifyForgotPassword(request);

        // Assert
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).getValue("otp:test@example.com");
        verify(userOtpRepository).findValidOtp("test@example.com", "otp:test@example.com");
        verify(userService).generateRandomPassword();
        verify(passwordEncoder).encode("newPassword");
        verify(userRepository).save(user);
        verify(userOtpRepository).save(userOtp);
        verify(userService).sendChangePasswordEmail(user, "newPassword");
        assertEquals("newEncodedPassword", user.getPassword());
        assertFalse(user.isLocked());
        assertTrue(userOtp.isUsed());
        assertNotNull(userOtp.getValidatedAt());
    }

    @Test
    void verifyForgotPassword_UserNotFound_ThrowsUserNotFoundException() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));
        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService, never()).getValue(anyString());
        verify(userOtpRepository, never()).findValidOtp(anyString(), anyString());
        verify(redisService, never()).delete(anyString());
        verify(userService, never()).generateRandomPassword();
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
        verify(userService, never()).sendChangePasswordEmail(any(), anyString());
    }

    @Test
    void verifyForgotPassword_NullUserInfo_ThrowsInternalServerError() {
        // Arrange
        user.setUserInfo(null);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService, never()).getValue(anyString());
        verify(userOtpRepository, never()).findValidOtp(anyString(), anyString());
        verify(redisService, never()).delete(anyString());
        verify(userService, never()).generateRandomPassword();
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
        verify(userService, never()).sendChangePasswordEmail(any(), anyString());
    }

    @Test
    void verifyForgotPassword_InvalidOtpInRedis_ThrowsInvalidOtpException() {
        // Arrange
        request.setOtp("wrongOtp");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:test@example.com")).thenReturn("123456");

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));
        assertEquals(ResponseCode.INVALID_OTP, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).getValue("otp:test@example.com");
        verify(userOtpRepository, never()).findValidOtp(anyString(), anyString());
        verify(redisService, never()).delete(anyString());
        verify(userService, never()).generateRandomPassword();
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
        verify(userService, never()).sendChangePasswordEmail(any(), anyString());
    }

    @Test
    void verifyForgotPassword_InvalidOtpInDatabase_ThrowsInvalidOtpException() {
        // Arrange
        request.setOtp("wrongOtp");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:test@example.com")).thenReturn(null);
        when(userOtpRepository.findValidOtp("test@example.com", "otp:test@example.com")).thenReturn(Optional.of(userOtp));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));
        assertEquals(ResponseCode.INVALID_OTP, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).getValue("otp:test@example.com");
        verify(userOtpRepository).findValidOtp("test@example.com", "otp:test@example.com");
        verify(redisService, never()).delete(anyString());
        verify(userService, never()).generateRandomPassword();
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
        verify(userService, never()).sendChangePasswordEmail(any(), anyString());
    }

    @Test
    void verifyForgotPassword_OtpNotFoundInDatabase_ThrowsInvalidOtpException() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:test@example.com")).thenReturn(null);
        when(userOtpRepository.findValidOtp("test@example.com", "otp:test@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));
        assertEquals(ResponseCode.INVALID_OTP, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).getValue("otp:test@example.com");
        verify(userOtpRepository).findValidOtp("test@example.com", "otp:test@example.com");
        verify(redisService, never()).delete(anyString());
        verify(userService, never()).generateRandomPassword();
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
        verify(userService, never()).sendChangePasswordEmail(any(), anyString());
    }

    @Test
    void verifyForgotPassword_OtpAlreadyUsed_ThrowsOtpAlreadyUsedException() {
        // Arrange
        userOtp.setUsed(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:test@example.com")).thenReturn(null);
        when(userOtpRepository.findValidOtp("test@example.com", "otp:test@example.com")).thenReturn(Optional.of(userOtp));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));
        assertEquals(ResponseCode.OTP_ALREADY_USED, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).getValue("otp:test@example.com");
        verify(userOtpRepository).findValidOtp("test@example.com", "otp:test@example.com");
        verify(redisService, never()).delete(anyString());
        verify(userService, never()).generateRandomPassword();
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
        verify(userService, never()).sendChangePasswordEmail(any(), anyString());
    }


    @Test
    void verifyForgotPassword_RedisServiceGetValueThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:test@example.com")).thenThrow(new RuntimeException("Redis error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).getValue("otp:test@example.com");
        verify(userOtpRepository, never()).findValidOtp(anyString(), anyString());
        verify(redisService, never()).delete(anyString());
        verify(userService, never()).generateRandomPassword();
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
        verify(userService, never()).sendChangePasswordEmail(any(), anyString());
    }

    @Test
    void verifyForgotPassword_RedisServiceDeleteThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:test@example.com")).thenReturn("123456");
        doThrow(new RuntimeException("Redis delete error")).when(redisService).delete("otp:test@example.com");

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).getValue("otp:test@example.com");
        verify(redisService).delete("otp:test@example.com");
        verify(userOtpRepository, never()).findValidOtp(anyString(), anyString());
        verify(userService, never()).generateRandomPassword();
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
        verify(userService, never()).sendChangePasswordEmail(any(), anyString());
    }

    @Test
    void verifyForgotPassword_GenerateRandomPasswordThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:test@example.com")).thenReturn("123456");
        doNothing().when(redisService).delete("otp:test@example.com");
        when(userService.generateRandomPassword()).thenThrow(new RuntimeException("Password generation failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).getValue("otp:test@example.com");
        verify(redisService).delete("otp:test@example.com");
        verify(userOtpRepository, never()).findValidOtp(anyString(), anyString());
        verify(userService).generateRandomPassword();
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
        verify(userService, never()).sendChangePasswordEmail(any(), anyString());
    }

    @Test
    void verifyForgotPassword_PasswordEncoderThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:test@example.com")).thenReturn("123456");
        doNothing().when(redisService).delete("otp:test@example.com");
        when(userService.generateRandomPassword()).thenReturn("newPassword");
        when(passwordEncoder.encode("newPassword")).thenThrow(new RuntimeException("Password encoding failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).getValue("otp:test@example.com");
        verify(redisService).delete("otp:test@example.com");
        verify(userService).generateRandomPassword();
        verify(passwordEncoder).encode("newPassword");
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
        verify(userService, never()).sendChangePasswordEmail(any(), anyString());
    }

    @Test
    void verifyForgotPassword_UserRepositorySaveThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:test@example.com")).thenReturn("123456");
        doNothing().when(redisService).delete("otp:test@example.com");
        when(userService.generateRandomPassword()).thenReturn("newPassword");
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Failed to save user"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.verifyForgotPassword(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).getValue("otp:test@example.com");
        verify(redisService).delete("otp:test@example.com");
        verify(userService).generateRandomPassword();
        verify(passwordEncoder).encode("newPassword");
        verify(userRepository).save(user);
        verify(userOtpRepository, never()).save(any());
        verify(userService, never()).sendChangePasswordEmail(any(), anyString());
    }

    @Test
    void verifyForgotPassword_SendChangePasswordEmailThrowsMessagingException_ThrowsInternalServerError() throws MessagingException {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(redisService.getValue("otp:test@example.com")).thenReturn("123456");
        doNothing().when(redisService).delete("otp:test@example.com");
        when(userService.generateRandomPassword()).thenReturn("newPassword");
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // ✅ Simulate lỗi gửi email
        doThrow(new MessagingException("Failed to send email"))
                .when(emailService)
                .sendEmail(anyString(), anyString(), any(), nullable(String.class), anyString(), anyString());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class,
                () -> userService.verifyForgotPassword(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(userRepository).findByEmail("test@example.com");
        verify(redisService).getValue("otp:test@example.com");
        verify(redisService).delete("otp:test@example.com");
        verify(userService).generateRandomPassword();
        verify(passwordEncoder).encode("newPassword");
        verify(userRepository).save(user);
        verify(emailService).sendEmail(anyString(), anyString(), any(), nullable(String.class), anyString(), anyString());
    }

}
