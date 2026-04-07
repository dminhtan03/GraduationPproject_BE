package com.finalProject.BookingMeetingRoom.service.auth;


import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.common.utils.JwtUtils;
import com.finalProject.BookingMeetingRoom.mapper.UserMapper;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.response.UserResponse;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.impl.AuthServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest_getProfile {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private HttpServletRequest request;

    private User user;
    private UserInfo userInfo;
    private UserResponse userResponse;

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
        user.setLocked(false);
        user.setLoginCount(0);
        user.setUserInfo(userInfo);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>());
        user.setRefreshTokens(new ArrayList<>());
        user.setReservations(new ArrayList<>());

        // Initialize UserResponse
        userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setEmail(userInfo.getEmail());
        userResponse.setFirstName(userInfo.getFirstName());
        userResponse.setLastName(userInfo.getLastName());
        userResponse.setPhoneNumber(userInfo.getPhoneNumber());
        userResponse.setAddress(userInfo.getAddress());
        userResponse.setGender(userInfo.getGender());
        userResponse.setDepartment(userInfo.getDepartment());
    }

    @Test
    void getProfile_Success() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer accessToken");
        when(jwtUtils.extractUsername("accessToken")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userMapper.toUserResponse(userInfo)).thenReturn(userResponse);

        // Act
        UserResponse result = authService.getProfile(request);

        // Assert
        verify(request).getHeader("Authorization");
        verify(jwtUtils).extractUsername("accessToken");
        verify(userRepository).findByEmail("test@example.com");
        verify(userMapper).toUserResponse(userInfo);
        assertEquals(userResponse.getId(), result.getId());
        assertEquals(userResponse.getEmail(), result.getEmail());
        assertEquals(userResponse.getFirstName(), result.getFirstName());
        assertEquals(userResponse.getLastName(), result.getLastName());
    }

    @Test
    void getProfile_NullAuthorizationHeader_ThrowsInternalServerError() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.getProfile(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(request).getHeader("Authorization");
        verify(jwtUtils, never()).extractUsername(anyString());
        verify(userRepository, never()).findByEmail(anyString());
        verify(userMapper, never()).toUserResponse(any());
    }

    @Test
    void getProfile_InvalidAuthorizationHeader_ThrowsUserNotFoundException() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("InvalidToken");
        when(jwtUtils.extractUsername("Token")).thenReturn("invalid@example.com");
        when(userRepository.findByEmail("invalid@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.getProfile(request));
        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode(), "Should throw USER_NOT_FOUND");
        verify(request).getHeader("Authorization");
        verify(jwtUtils).extractUsername("Token");
        verify(userRepository).findByEmail("invalid@example.com");
        verify(userMapper, never()).toUserResponse(any());
    }

    @Test
    void getProfile_TokenInvalid_ThrowsInternalServerError() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer accessToken");
        when(jwtUtils.extractUsername("accessToken")).thenThrow(new RuntimeException("Invalid token"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.getProfile(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(request).getHeader("Authorization");
        verify(jwtUtils).extractUsername("accessToken");
        verify(userRepository, never()).findByEmail(anyString());
        verify(userMapper, never()).toUserResponse(any());
    }

    @Test
    void getProfile_UserNotFound_ThrowsUserNotFoundException() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer accessToken");
        when(jwtUtils.extractUsername("accessToken")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.getProfile(request));
        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
        verify(request).getHeader("Authorization");
        verify(jwtUtils).extractUsername("accessToken");
        verify(userRepository).findByEmail("test@example.com");
        verify(userMapper, never()).toUserResponse(any());
    }

    @Test
    void getProfile_NullUserInfo_ThrowsInternalServerError() {
        // Arrange
        User userWithoutInfo = new User();
        userWithoutInfo.setId(UUID.randomUUID().toString());
        userWithoutInfo.setUserInfo(null);
        userWithoutInfo.setRoles(new HashSet<>());
        userWithoutInfo.setRefreshTokens(new ArrayList<>()); // Sử dụng ArrayList như trong bài kiểm tra gốc
        userWithoutInfo.setReservations(new ArrayList<>()); // Sử dụng ArrayList như trong bài kiểm tra gốc

        when(request.getHeader("Authorization")).thenReturn("Bearer accessToken");
        when(jwtUtils.extractUsername("accessToken")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(userWithoutInfo));
        when(userMapper.toUserResponse(null)).thenReturn(null); // Mock userMapper trả về null khi userInfo là null

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.getProfile(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode(), "Should throw INTERNAL_SERVER_ERROR");
        verify(request).getHeader("Authorization");
        verify(jwtUtils).extractUsername("accessToken");
        verify(userRepository).findByEmail("test@example.com");
        verify(userMapper).toUserResponse(null); // Xác minh userMapper được gọi với null
    }
}