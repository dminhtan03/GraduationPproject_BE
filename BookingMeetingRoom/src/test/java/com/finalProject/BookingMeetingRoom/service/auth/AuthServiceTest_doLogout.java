package com.finalProject.BookingMeetingRoom.service.auth;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.common.utils.JwtUtils;
import com.finalProject.BookingMeetingRoom.model.entity.RefreshToken;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.repository.RefreshTokenRepository;
import com.finalProject.BookingMeetingRoom.service.RedisService;
import com.finalProject.BookingMeetingRoom.service.impl.AuthServiceImpl;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest_doLogout {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RedisService redisService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private HttpServletRequest request;

    private User user;
    private UserInfo userInfo;
    private RefreshToken refreshToken;

    @BeforeEach
    public void setup() {
        // Initialize UserInfo
        userInfo = new UserInfo();
        userInfo.setId(UUID.randomUUID().toString());
        userInfo.setEmail("test@example.com");
        userInfo.setFirstName("John");
        userInfo.setLastName("Doe");
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
        user.setRoles(new HashSet<>()); // Khởi tạo roles để tránh NullPointerException

        // Initialize RefreshToken
        refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID().toString())
                .token("refreshTokenValue")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .isRevoked(false)
                .user(user)
                .build();
    }


    @Test
    void doLogout_Success_WithAccessTokenAndRefreshToken() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer accessToken");
        when(jwtUtils.extractExpiration("accessToken")).thenReturn(new Date(System.currentTimeMillis() + 10000)); // Token còn hiệu lực
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "refreshTokenValue")});
        when(refreshTokenRepository.findByTokenAndIsRevoked("refreshTokenValue")).thenReturn(refreshToken);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        // Act
        authService.doLogout(request);

        // Assert
        ArgumentCaptor<Long> expirationCaptor = ArgumentCaptor.forClass(Long.class);

        verify(redisService).setValue(
                eq("BlackList:accessToken"),
                eq("accessToken"),
                expirationCaptor.capture(),
                eq(TimeUnit.MILLISECONDS)
        );

        long captured = expirationCaptor.getValue();
        assertTrue(captured >= 9900L && captured <= 10000L, "Expiration time should be close to 10000ms");
        verify(refreshTokenRepository).findByTokenAndIsRevoked("refreshTokenValue");
        verify(refreshTokenRepository).save(refreshToken);
        verify(redisService).deleteCacheToken(refreshToken.getId());
        assertTrue(refreshToken.isRevoked(), "Refresh token should be revoked");
    }

    @Test
    void doLogout_NoAuthorizationHeader_ThrowsTokenInvalidException() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.doLogout(request));
        assertEquals(ResponseCode.TOKEN_INVALID, exception.getResponseCode());
        verify(redisService, never()).setValue(anyString(), anyString(), anyLong(), any());
        verify(refreshTokenRepository, never()).findByTokenAndIsRevoked(anyString());
        verify(refreshTokenRepository, never()).save(any());
        verify(redisService, never()).deleteCacheToken(anyString());
    }

    @Test
    void doLogout_InvalidAuthorizationHeader_ThrowsTokenInvalidException() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("InvalidToken");

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.doLogout(request));
        assertEquals(ResponseCode.TOKEN_INVALID, exception.getResponseCode());
        verify(redisService, never()).setValue(anyString(), anyString(), anyLong(), any());
        verify(refreshTokenRepository, never()).findByTokenAndIsRevoked(anyString());
        verify(refreshTokenRepository, never()).save(any());
        verify(redisService, never()).deleteCacheToken(anyString());
    }

    @Test
    void doLogout_AccessTokenExpired_DoesNotBlacklist() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer accessToken");
        when(jwtUtils.extractExpiration("accessToken")).thenReturn(new Date(System.currentTimeMillis() - 1000));
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "refreshTokenValue")});
        when(refreshTokenRepository.findByTokenAndIsRevoked("refreshTokenValue")).thenReturn(refreshToken);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        // Act
        authService.doLogout(request);

        // Assert
        verify(redisService, never()).setValue(anyString(), anyString(), anyLong(), any());
        verify(refreshTokenRepository).findByTokenAndIsRevoked("refreshTokenValue");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        redisService.deleteCacheToken(refreshToken.getId());
        assertTrue(refreshToken.isRevoked(), "Refresh token should be revoked");
    }


    @Test
    void doLogout_NoCookies_DoesNotProcessRefreshToken() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer accessToken");
        when(jwtUtils.extractExpiration("accessToken")).thenReturn(new Date(System.currentTimeMillis() + 10000));
        when(request.getCookies()).thenReturn(null);

        // Act
        authService.doLogout(request);

        // Assert
        ArgumentCaptor<Long> expirationCaptor = ArgumentCaptor.forClass(Long.class);

        verify(redisService).setValue(
                eq("BlackList:accessToken"),
                eq("accessToken"),
                expirationCaptor.capture(),
                eq(TimeUnit.MILLISECONDS)
        );

        long captured = expirationCaptor.getValue();
        assertTrue(captured >= 9900L && captured <= 10000L, "Expiration time should be close to 10000ms");
        verify(refreshTokenRepository, never()).findByTokenAndIsRevoked(anyString());
        verify(refreshTokenRepository, never()).save(any());
        verify(redisService, never()).deleteCacheToken(anyString());
    }

    @Test
    void doLogout_NoRefreshTokenCookie_DoesNotProcessRefreshToken() {
        // Arrange
        long expirationTime = 10000L;
        when(request.getHeader("Authorization")).thenReturn("Bearer accessToken");
        when(jwtUtils.extractExpiration("accessToken"))
                .thenReturn(new Date(System.currentTimeMillis() + expirationTime));
        when(request.getCookies())
                .thenReturn(new Cookie[]{new Cookie("otherCookie", "value")});

        // Act
        authService.doLogout(request);

        // Assert
        ArgumentCaptor<Long> expirationCaptor = ArgumentCaptor.forClass(Long.class);

        verify(redisService).setValue(
                eq("BlackList:accessToken"),
                eq("accessToken"),
                expirationCaptor.capture(),
                eq(TimeUnit.MILLISECONDS)
        );

        long captured = expirationCaptor.getValue();
        assertTrue(captured >= 9900L && captured <= 10000L, "Expiration time should be close to 10000ms");

        verify(refreshTokenRepository, never()).findByTokenAndIsRevoked(anyString());
        verify(refreshTokenRepository, never()).save(any());
        verify(redisService, never()).deleteCacheToken(anyString());
    }


    @Test
    void doLogout_RefreshTokenNotFound_ThrowsRefreshTokenNotFoundException() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer accessToken");
        when(jwtUtils.extractExpiration("accessToken")).thenReturn(new Date(System.currentTimeMillis() + 10000));
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", "refreshTokenValue")});
        when(refreshTokenRepository.findByTokenAndIsRevoked("refreshTokenValue")).thenReturn(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.doLogout(request));
        assertEquals(ResponseCode.REFRESH_TOKEN_NOT_FOUND, exception.getResponseCode());
        // Assert
        ArgumentCaptor<Long> expirationCaptor = ArgumentCaptor.forClass(Long.class);

        verify(redisService).setValue(
                eq("BlackList:accessToken"),
                eq("accessToken"),
                expirationCaptor.capture(),
                eq(TimeUnit.MILLISECONDS)
        );

        long captured = expirationCaptor.getValue();
        assertTrue(captured >= 9900L && captured <= 10000L, "Expiration time should be close to 10000ms");

        verify(refreshTokenRepository).findByTokenAndIsRevoked("refreshTokenValue");
        verify(refreshTokenRepository, never()).save(any());
        verify(redisService, never()).deleteCacheToken(anyString());
    }

    @Test
    void doLogout_UnexpectedException_ThrowsInternalServerError() {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer accessToken");
        when(jwtUtils.extractExpiration("accessToken")).thenThrow(new RuntimeException("Unexpected error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.doLogout(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        verify(redisService, never()).setValue(anyString(), anyString(), anyLong(), any());
        verify(refreshTokenRepository, never()).findByTokenAndIsRevoked(anyString());
        verify(refreshTokenRepository, never()).save(any());
        verify(redisService, never()).deleteCacheToken(anyString());
    }
}
