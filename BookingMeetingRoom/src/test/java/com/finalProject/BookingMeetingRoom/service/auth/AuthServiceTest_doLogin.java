package com.finalProject.BookingMeetingRoom.service.auth;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.common.utils.JwtUtils;
import com.finalProject.BookingMeetingRoom.model.entity.Permission;
import com.finalProject.BookingMeetingRoom.model.entity.Role;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.request.LoginRequest;
import com.finalProject.BookingMeetingRoom.model.response.AuthResponse;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.impl.AuthServiceImpl;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest_doLogin {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private HttpServletResponse response;

    private User user;
    private LoginRequest loginRequest;

    @BeforeEach
    public void setup() {
        // Khởi tạo UserInfo
        UserInfo userInfo = new UserInfo();
        userInfo.setId(UUID.randomUUID().toString());
        userInfo.setEmail("test@example.com"); // Sử dụng email nhất quán
        userInfo.setFirstName("John");
        userInfo.setLastName("Doe");
        userInfo.setCreatedAt(LocalDateTime.now());

        // Khởi tạo Role và Permission
        Permission permission = new Permission();
        permission.setId(UUID.randomUUID().toString());
        permission.setName("READ");

        Role role = new Role();
        role.setId(UUID.randomUUID().toString());
        role.setName("USER");
        role.setPermissions(new HashSet<>(Collections.singletonList(permission)));

        // Khởi tạo User
        user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setPassword("encodedPassword");
        user.setEnabled(true);
        user.setLocked(false);
        user.setLoginCount(0);
        user.setUserInfo(userInfo);
        user.setRoles(new HashSet<>(Collections.singletonList(role)));
        user.setCreatedAt(LocalDateTime.now());

        // Khởi tạo LoginRequest
        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password");

        // Các mock chung
        lenient().when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        lenient().when(passwordEncoder.matches(anyString(), eq("encodedPassword"))).thenReturn(true);
    }

    @Test
    void doLogin_Success() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(user);
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtUtils.generateToken(anyMap(), eq(user))).thenReturn("accessToken");
        when(jwtUtils.generateRefreshToken(eq(user))).thenReturn("refreshToken");

        // Act
        AuthResponse result = authService.doLogin(loginRequest, response);

        // Assert
        assertNotNull(result);
        assertEquals("accessToken", result.getAccessToken());
        assertEquals("refreshToken", result.getRefreshToken());
        verify(response).addCookie(any(Cookie.class));
        verify(authManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtUtils).generateToken(anyMap(), eq(user));
        verify(jwtUtils).generateRefreshToken(eq(user));
    }

    @Test
    void doLogin_UserNotFound_ThrowsException() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.doLogin(loginRequest, response));
        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    void doLogin_UserDisabled_ThrowsException() {
        // Arrange
        user.setEnabled(false);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.doLogin(loginRequest, response));
        assertEquals(ResponseCode.USER_DISABLE, exception.getResponseCode());
    }

    @Test
    void doLogin_InvalidPassword_ThrowsException() {
        // Arrange
        user.setLoginCount(1); // Đặt loginCount để kiểm tra tăng
        when(passwordEncoder.matches(anyString(), eq("encodedPassword"))).thenReturn(false); // Mật khẩu không hợp lệ
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user); // Mock phương thức save

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.doLogin(loginRequest, response));
        assertEquals(ResponseCode.INVALID_PASSWORD, exception.getResponseCode());
        assertEquals(2, user.getLoginCount(), "loginCount nên được tăng lên 2");
        assertFalse(user.isLocked(), "Tài khoản không nên bị khóa");
        verify(userRepository).save(user); // Xác minh rằng save được gọi
        verify(authManager, never()).authenticate(any()); // Không gọi xác thực
        verify(jwtUtils, never()).generateToken(any(), any()); // Không tạo token
        verify(jwtUtils, never()).generateRefreshToken(any()); // Không tạo refresh token
        verify(response, never()).addCookie(any()); // Không đặt cookie
    }

    @Test
    void doLogin_AccountLockedAfterThreeAttempts_ThrowsException() {
        // Arrange
        user.setLoginCount(2); // Đặt loginCount để lần thất bại tiếp theo là lần thứ ba
        when(passwordEncoder.matches(anyString(), eq("encodedPassword"))).thenReturn(false); // Mật khẩu không hợp lệ
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user); // Mock phương thức save

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.doLogin(loginRequest, response));
        assertEquals(ResponseCode.ACCOUNT_LOCKED, exception.getResponseCode());
        assertTrue(user.isLocked(), "Tài khoản nên bị khóa");
        assertEquals(3, user.getLoginCount(), "loginCount nên được tăng lên 3");
        verify(userRepository).save(user); // Xác minh rằng save được gọi
        verify(authManager, never()).authenticate(any()); // Không gọi xác thực
        verify(jwtUtils, never()).generateToken(any(), any()); // Không tạo token
        verify(jwtUtils, never()).generateRefreshToken(any()); // Không tạo refresh token
        verify(response, never()).addCookie(any()); // Không đặt cookie
    }

    @Test
    void doLogin_AuthenticationFailure_ThrowsException() {
        // Arrange
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), eq("encodedPassword"))).thenReturn(true);
        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> authService.doLogin(loginRequest, response));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        assertEquals(0, user.getLoginCount());

        // Verify
        verify(authManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtUtils, never()).generateToken(any(), any());
        verify(jwtUtils, never()).generateRefreshToken(any());
        verify(response, never()).addCookie(any());

    }


    @Test
    void doLogin_SaveUserFails_ThrowsException() {
        // Arrange
        user.setLoginCount(1);
        when(passwordEncoder.matches(anyString(), eq("encodedPassword"))).thenReturn(false);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenThrow(new DataAccessException("Database error") {});

        // Act & Assert
        assertThrows(CustomException.class, () -> authService.doLogin(loginRequest, response));
        verify(userRepository).save(user);
        verify(authManager, never()).authenticate(any());
        verify(jwtUtils, never()).generateToken(any(), any());
        verify(jwtUtils, never()).generateRefreshToken(any());
        verify(response, never()).addCookie(any());
    }

}
