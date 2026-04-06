package com.finalProject.BookingMeetingRoom.service.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest_refreshToken {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RedisService redisService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private HttpServletResponse response;

    private RefreshToken refreshToken;
    private User user;
    private final long refreshTokenExpiration = 604800000;

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
        user.setRoles(new HashSet<>()); // Khởi tạo roles để tránh NullPointerException
        user.setRefreshTokens(new ArrayList<>()); // Khởi tạo refreshTokens để tránh NullPointerException
        user.setReservations(new ArrayList<>()); // Khởi tạo reservations để tránh NullPointerException

        // Initialize RefreshToken
        refreshToken = RefreshToken.builder()
                .id("1") // Cố định ID để khớp với verify
                .token("refreshTokenValue")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .isRevoked(false)
                .user(user)
                .build();
    }

    @Test
    void refreshToken_Success_FromRedisCache() {
        // Arrange
        when(redisService.getCacheRefreshToken("refreshTokenValue")).thenReturn(refreshToken);
        when(jwtUtils.generateToken(anyMap(), eq(user))).thenReturn("newAccessToken");
        when(jwtUtils.generateRefreshToken(eq(user))).thenReturn("newRefreshToken");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        // Act
        AuthResponse result = authService.refreshToken("refreshTokenValue", response);

        // Assert
        verify(redisService).getCacheRefreshToken("refreshTokenValue");
        verify(refreshTokenRepository, never()).findByTokenAndIsRevoked(anyString());
        verify(jwtUtils).generateToken(anyMap(), eq(user));
        verify(jwtUtils).generateRefreshToken(eq(user));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(redisService).deleteCacheToken("refreshTokenValue");
        verify(response).addCookie(argThat(cookie ->
                cookie.getName().equals("refreshToken") &&
                        cookie.getValue().equals("newRefreshToken")
        ));
        assertTrue(refreshToken.isRevoked(), "Refresh token should be revoked");
        assertEquals("newAccessToken", result.getAccessToken());
        assertEquals("newRefreshToken", result.getRefreshToken());
    }

    @Test
    void refreshToken_Success_FromDatabase() {
        // Arrange
        when(redisService.getCacheRefreshToken("refreshTokenValue")).thenReturn(null);
        when(refreshTokenRepository.findByTokenAndIsRevoked("refreshTokenValue")).thenReturn(refreshToken);
        when(jwtUtils.generateToken(anyMap(), eq(user))).thenReturn("newAccessToken");
        when(jwtUtils.generateRefreshToken(eq(user))).thenReturn("newRefreshToken");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        // Act
        AuthResponse result = authService.refreshToken("refreshTokenValue", response);

        // Assert
        verify(redisService).getCacheRefreshToken("refreshTokenValue");
        verify(refreshTokenRepository).findByTokenAndIsRevoked("refreshTokenValue");
        verify(jwtUtils).generateToken(anyMap(), eq(user));
        verify(jwtUtils).generateRefreshToken(eq(user));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(redisService).deleteCacheToken("refreshTokenValue");
        verify(response).addCookie(argThat(cookie ->
                cookie.getName().equals("refreshToken") &&
                        cookie.getValue().equals("newRefreshToken")
        ));
        assertTrue(refreshToken.isRevoked(), "Refresh token should be revoked");
        assertEquals("newAccessToken", result.getAccessToken());
        assertEquals("newRefreshToken", result.getRefreshToken());
    }

    @Test
    void refreshToken_NotFound_ThrowsRefreshTokenNotFoundException() {
        // Arrange
        when(redisService.getCacheRefreshToken("refreshTokenValue")).thenReturn(null);
        when(refreshTokenRepository.findByTokenAndIsRevoked("refreshTokenValue")).thenReturn(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.refreshToken("refreshTokenValue", response));
        assertEquals(ResponseCode.REFRESH_TOKEN_NOT_FOUND, exception.getResponseCode(), "Should throw REFRESH_TOKEN_NOT_FOUND");
        verify(redisService).getCacheRefreshToken("refreshTokenValue");
        verify(refreshTokenRepository).findByTokenAndIsRevoked("refreshTokenValue");
        verify(jwtUtils, never()).generateToken(any(), any());
        verify(jwtUtils, never()).generateRefreshToken(any());
        verify(refreshTokenRepository, never()).save(any());
        verify(redisService, never()).deleteCacheToken(anyString());
        verify(response, never()).addCookie(any());
    }

    @Test
    void refreshToken_NullUser_ThrowsInternalServerError() {
        // Arrange
        RefreshToken invalidToken = RefreshToken.builder()
                .id("1")
                .token("refreshTokenValue")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .isRevoked(false)
                .user(null) // User is null
                .build();
        when(redisService.getCacheRefreshToken("refreshTokenValue")).thenReturn(invalidToken);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.refreshToken("refreshTokenValue", response));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode(), "Should throw INTERNAL_SERVER_ERROR");
        verify(redisService).getCacheRefreshToken("refreshTokenValue");
        verify(refreshTokenRepository, never()).findByTokenAndIsRevoked(anyString());
        verify(jwtUtils, never()).generateToken(any(), any());
        verify(jwtUtils, never()).generateRefreshToken(any());
        verify(refreshTokenRepository, never()).save(any());
        verify(redisService, never()).deleteCacheToken(anyString());
        verify(response, never()).addCookie(any());
    }

    @Test
    void refreshToken_NullUserInfo_ThrowsInternalServerError() {
        // Arrange
        User userWithoutInfo = new User();
        userWithoutInfo.setId(UUID.randomUUID().toString());
        userWithoutInfo.setUserInfo(null); // UserInfo is null
        userWithoutInfo.setRoles(new HashSet<>()); // Khởi tạo roles
        userWithoutInfo.setRefreshTokens(new ArrayList<>()); // Khởi tạo refreshTokens
        userWithoutInfo.setReservations(new ArrayList<>()); // Khởi tạo reservations
        RefreshToken invalidToken = RefreshToken.builder()
                .id("1")
                .token("refreshTokenValue")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .isRevoked(false)
                .user(userWithoutInfo)
                .build();
        when(redisService.getCacheRefreshToken("refreshTokenValue")).thenReturn(invalidToken);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.refreshToken("refreshTokenValue", response));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode(), "Should throw INTERNAL_SERVER_ERROR");
        verify(redisService).getCacheRefreshToken("refreshTokenValue");
        verify(refreshTokenRepository, never()).findByTokenAndIsRevoked(anyString());
        verify(jwtUtils, never()).generateToken(any(), any());
        verify(jwtUtils, never()).generateRefreshToken(any());
        verify(refreshTokenRepository, never()).save(any());
        verify(redisService, never()).deleteCacheToken(anyString());
        verify(response, never()).addCookie(any());
    }

    @Test
    void refreshToken_UnexpectedException_ThrowsInternalServerError() {
        // Arrange
        when(redisService.getCacheRefreshToken("refreshTokenValue")).thenThrow(new RuntimeException("Unexpected error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.refreshToken("refreshTokenValue", response));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode(), "Should throw INTERNAL_SERVER_ERROR");
        verify(redisService).getCacheRefreshToken("refreshTokenValue");
        verify(refreshTokenRepository, never()).findByTokenAndIsRevoked(anyString());
        verify(jwtUtils, never()).generateToken(any(), any());
        verify(jwtUtils, never()).generateRefreshToken(any());
        verify(refreshTokenRepository, never()).save(any());
        verify(redisService, never()).deleteCacheToken(anyString());
        verify(response, never()).addCookie(any());
    }
}
