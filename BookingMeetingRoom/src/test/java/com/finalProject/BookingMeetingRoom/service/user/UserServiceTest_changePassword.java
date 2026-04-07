package com.finalProject.BookingMeetingRoom.service.user;

import com.finalProject.BookingMeetingRoom.common.enums.EmailTemplateName;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.request.ChangePasswordRequest;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.EmailService;
import com.finalProject.BookingMeetingRoom.service.impl.UserServiceImpl;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest_changePassword {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private Authentication authentication;

    private ChangePasswordRequest request;
    private User user;
    private final String activationUrl = "http://example.com/activate";

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

        // Initialize ChangePasswordRequest
        request = new ChangePasswordRequest();
        request.setCurrentPassword("currentPassword");
        request.setNewPassword("newPassword");
        request.setConfirmPassword("newPassword");
    }

    @Test
    void changePassword_Success() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(user);
        when(passwordEncoder.matches("currentPassword", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act
        userService.changePassword(request, authentication);

        // Assert
        verify(authentication).getPrincipal();
        verify(passwordEncoder).matches("currentPassword", "encodedPassword");
        verify(passwordEncoder).encode("newPassword");
        verify(userRepository).save(user);
        assertEquals("newEncodedPassword", user.getPassword());
    }

    @Test
    void changePassword_InvalidCurrentPassword_ThrowsInvalidCurrentPasswordException() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(user);
        when(passwordEncoder.matches("currentPassword", "encodedPassword")).thenReturn(false);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () ->
                userService.changePassword(request, authentication));
        assertEquals(ResponseCode.INVALID_CURRENT_PASSWORD, exception.getResponseCode());
        verify(authentication).getPrincipal();
        verify(passwordEncoder).matches("currentPassword", "encodedPassword");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_PasswordConfirmNotMatch_ThrowsPasswordConfirmNotMatchException() {
        // Arrange
        request.setConfirmPassword("differentPassword");
        when(authentication.getPrincipal()).thenReturn(user);
        when(passwordEncoder.matches("currentPassword", "encodedPassword")).thenReturn(true);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () ->
                userService.changePassword(request, authentication));
        assertEquals(ResponseCode.PASSWORD_CONFIRM_NOT_MATCH, exception.getResponseCode());
        verify(authentication).getPrincipal();
        verify(passwordEncoder).matches("currentPassword", "encodedPassword");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_NullPrincipal_ThrowsInternalServerError() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () ->
                userService.changePassword(request, authentication));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(authentication).getPrincipal();
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_NullUserInfo_ThrowsInternalServerError() {
        // Arrange
        user.setUserInfo(null);
        when(authentication.getPrincipal()).thenReturn(user);
        when(passwordEncoder.matches("currentPassword", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () ->
                userService.changePassword(request, authentication));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        // Verify what actually gets called before the exception
        verify(authentication).getPrincipal();
        verify(passwordEncoder).matches("currentPassword", "encodedPassword");
        verify(passwordEncoder).encode("newPassword");
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_PasswordEncoderThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(user);
        when(passwordEncoder.matches("currentPassword", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenThrow(new RuntimeException("Password encoding failed"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () ->
                userService.changePassword(request, authentication));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(authentication).getPrincipal();
        verify(passwordEncoder).matches("currentPassword", "encodedPassword");
        verify(passwordEncoder).encode("newPassword");
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_UserRepositorySaveThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(user);
        when(passwordEncoder.matches("currentPassword", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Failed to save user"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () ->
                userService.changePassword(request, authentication));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(authentication).getPrincipal();
        verify(passwordEncoder).matches("currentPassword", "encodedPassword");
        verify(passwordEncoder).encode("newPassword");
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_SendEmailThrowsMessagingException_ThrowsInternalServerError() throws MessagingException {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(user);
        when(passwordEncoder.matches("currentPassword", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Make emailService throw an exception - note the isNull() for the 4th parameter
        doThrow(new MessagingException("Email sending failed"))
                .when(emailService).sendEmail(
                        anyString(),
                        anyString(),
                        any(EmailTemplateName.class),
                        isNull(),
                        anyString(),
                        anyString()
                );

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () ->
                userService.changePassword(request, authentication));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        // Verify the interactions
        verify(authentication).getPrincipal();
        verify(passwordEncoder).matches("currentPassword", "encodedPassword");
        verify(passwordEncoder).encode("newPassword");
        verify(userRepository).save(user);
        verify(emailService).sendEmail(
                anyString(),
                anyString(),
                any(EmailTemplateName.class),
                isNull(),
                anyString(),
                anyString()
        );
    }
}
