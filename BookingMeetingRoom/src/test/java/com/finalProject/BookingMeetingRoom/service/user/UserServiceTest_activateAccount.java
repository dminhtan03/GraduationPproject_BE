package com.finalProject.BookingMeetingRoom.service.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest_activateAccount {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserOtpRepository userOtpRepository;

    private User user;
    private UserInfo userInfo;
    private UserOtp userOtp;
    private String validOtp = "123456";

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
        user.setEnabled(false);
        user.setLocked(false);
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
                .otpCode(validOtp)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .validatedAt(null)
                .isUsed(false)
                .user(user)
                .build();

        // Set activationUrl and character to avoid NullPointerException in other methods
        ReflectionTestUtils.setField(userService, "activationUrl", "http://example.com/activate");
        ReflectionTestUtils.setField(userService, "character", "0123456789abcdefghijklmnopqrstuvwxyz");
    }

    @Test
    void activateAccount_Success() {
        // Arrange
        when(userOtpRepository.findByOtpCode(validOtp)).thenReturn(Optional.of(userOtp));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userOtpRepository.save(any(UserOtp.class))).thenReturn(userOtp);

        // Act
        userService.activateAccount(validOtp);

        // Assert
        verify(userOtpRepository).findByOtpCode(validOtp);
        verify(userRepository).save(user);
        verify(userOtpRepository).save(userOtp);
        assertTrue(user.isEnabled());
        assertTrue(userOtp.isUsed());
        assertNotNull(userOtp.getValidatedAt());
    }

    @Test
    void activateAccount_InvalidOtp_ThrowsInvalidOtpException() {
        // Arrange
        when(userOtpRepository.findByOtpCode(validOtp)).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.activateAccount(validOtp));
        assertEquals(ResponseCode.INVALID_OTP, exception.getResponseCode());
        verify(userOtpRepository).findByOtpCode(validOtp);
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
    }

    @Test
    void activateAccount_ExpiredOtp_ThrowsExpiredOtpException() {
        // Arrange
        userOtp.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userOtpRepository.findByOtpCode(validOtp)).thenReturn(Optional.of(userOtp));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.activateAccount(validOtp));
        assertEquals(ResponseCode.EXPIRED_OTP, exception.getResponseCode());
        verify(userOtpRepository).findByOtpCode(validOtp);
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
    }

    @Test
    void activateAccount_UserNotFound_ThrowsUserNotFoundException() {
        // Arrange
        userOtp.setUser(null);
        when(userOtpRepository.findByOtpCode(validOtp)).thenReturn(Optional.of(userOtp));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.activateAccount(validOtp));
        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        verify(userOtpRepository).findByOtpCode(validOtp);
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
    }

    @Test
    void activateAccount_FindByOtpCodeThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(userOtpRepository.findByOtpCode(validOtp)).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.activateAccount(validOtp));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userOtpRepository).findByOtpCode(validOtp);
        verify(userRepository, never()).save(any());
        verify(userOtpRepository, never()).save(any());
    }

    @Test
    void activateAccount_UserRepositorySaveThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(userOtpRepository.findByOtpCode(validOtp)).thenReturn(Optional.of(userOtp));
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Failed to save user"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.activateAccount(validOtp));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userOtpRepository).findByOtpCode(validOtp);
        verify(userRepository).save(user);
        verify(userOtpRepository, never()).save(any());
    }

    @Test
    void activateAccount_UserOtpRepositorySaveThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(userOtpRepository.findByOtpCode(validOtp)).thenReturn(Optional.of(userOtp));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userOtpRepository.save(any(UserOtp.class))).thenThrow(new RuntimeException("Failed to save OTP"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> userService.activateAccount(validOtp));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(userOtpRepository).findByOtpCode(validOtp);
        verify(userRepository).save(user);
        verify(userOtpRepository).save(userOtp);
    }
}
