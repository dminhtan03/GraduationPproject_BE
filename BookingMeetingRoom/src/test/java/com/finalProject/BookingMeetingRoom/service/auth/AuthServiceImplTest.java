package com.finalProject.BookingMeetingRoom.service.auth;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.common.utils.JwtUtils;
import com.finalProject.BookingMeetingRoom.mapper.UserMapper;
import com.finalProject.BookingMeetingRoom.model.entity.RefreshToken;
import com.finalProject.BookingMeetingRoom.model.entity.Role;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.request.LoginRequest;
import com.finalProject.BookingMeetingRoom.model.response.AuthResponse;
import com.finalProject.BookingMeetingRoom.model.response.UserResponse;
import com.finalProject.BookingMeetingRoom.repository.RefreshTokenRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.RedisService;
import com.finalProject.BookingMeetingRoom.service.impl.AuthServiceImpl;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthenticationManager authManager;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private RedisService redisService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private AuthServiceImpl service;

    private User buildUser(String email) {
        UserInfo info = new UserInfo();
        info.setEmail(email);
        info.setFirstName("A");
        info.setLastName("B");
        info.setPhoneNumber("0123");
        info.setAddress("addr");
        info.setDepartment("IT");
        info.setGender("M");

        Role role = new Role();
        role.setName("USER");

        User user = new User();
        user.setId("u-1");
        user.setEnabled(true);
        user.setLocked(false);
        user.setLoginCount(0);
        user.setPassword("ENC");
        user.setRoles(Set.of(role));
        user.setUserInfo(info);
        user.setReset(false);
        user.setCancellationCount(0);
        return user;
    }

    @Test
    void doLogin_shouldThrowUserNotFound_whenEmailMissing() {
        LoginRequest req = new LoginRequest();
        req.setEmail("x@test.com");
        req.setPassword("123456");

        when(userRepository.findByEmail("x@test.com")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> service.doLogin(req, response));

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void doLogin_shouldThrowUserDisable_whenUserDisabled() {
        LoginRequest req = new LoginRequest();
        req.setEmail("u@test.com");
        req.setPassword("123456");

        User user = buildUser("u@test.com");
        user.setEnabled(false);

        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));

        CustomException ex = assertThrows(CustomException.class, () -> service.doLogin(req, response));

        assertEquals(ResponseCode.USER_DISABLE, ex.getResponseCode());
    }

    @Test
    void doLogin_shouldThrowInvalidPassword_whenPasswordMismatchAndNotLocked() {
        LoginRequest req = new LoginRequest();
        req.setEmail("u@test.com");
        req.setPassword("wrong");

        User user = buildUser("u@test.com");
        user.setLoginCount(0);

        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "ENC")).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class, () -> service.doLogin(req, response));

        assertEquals(ResponseCode.INVALID_PASSWORD, ex.getResponseCode());
        assertEquals(1, user.getLoginCount());
        verify(userRepository).save(user);
    }

    @Test
    void doLogin_shouldThrowAccountLocked_whenLoginCountReachesThree() {
        LoginRequest req = new LoginRequest();
        req.setEmail("u@test.com");
        req.setPassword("wrong");

        User user = buildUser("u@test.com");
        user.setLoginCount(2);

        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "ENC")).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class, () -> service.doLogin(req, response));

        assertEquals(ResponseCode.ACCOUNT_LOCKED, ex.getResponseCode());
        assertTrue(user.isLocked());
    }

    @Test
    void doLogin_shouldReturnTokens_whenValidCredentials() {
        ReflectionTestUtils.setField(service, "refreshTokenExpiration", 60000L);

        LoginRequest req = new LoginRequest();
        req.setEmail("u@test.com");
        req.setPassword("123456");

        User user = buildUser("u@test.com");
        User principal = buildUser("u@test.com");

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("123456", "ENC")).thenReturn(true);
        when(authManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtils.generateToken(any(), eq(principal))).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(user)).thenReturn("refresh-token");

        AuthResponse result = service.doLogin(req, response);

        assertEquals("access-token", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());
        assertEquals(0, user.getLoginCount());
        verify(response).addCookie(any(Cookie.class));
    }

    @Test
    void doLogout_shouldThrowCacheFailed_whenAuthorizationHeaderMissing() {
        when(request.getHeader("Authorization")).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class, () -> service.doLogout(request));

        assertEquals(ResponseCode.CACHE_FAILED, ex.getResponseCode());
    }

    @Test
    void doLogout_shouldBlacklistAndRevokeRefreshToken_whenValidRequest() {
        when(request.getHeader("Authorization")).thenReturn("Bearer access-1");
        when(jwtUtils.extractExpiration("access-1")).thenReturn(new Date(System.currentTimeMillis() + 5000));

        Cookie refresh = new Cookie("refreshToken", "rf-1");
        when(request.getCookies()).thenReturn(new Cookie[]{refresh});

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId("id-1");
        refreshToken.setRevoked(false);
        when(refreshTokenRepository.findByTokenAndIsRevoked("rf-1")).thenReturn(refreshToken);

        service.doLogout(request);

        verify(redisService).setValue(eq("BlackList:access-1"), eq("access-1"), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(redisService).deleteCacheToken("id-1");
        assertTrue(refreshToken.isRevoked());
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    void refreshToken_shouldReturnNewTokens_whenTokenFoundInCache() {
        ReflectionTestUtils.setField(service, "refreshTokenExpiration", 120000L);

        User user = buildUser("u@test.com");
        RefreshToken tokenEntity = new RefreshToken();
        tokenEntity.setId("id-1");
        tokenEntity.setUser(user);
        tokenEntity.setRevoked(false);

        when(redisService.getCacheRefreshToken("rf-1")).thenReturn(tokenEntity);
        when(jwtUtils.generateToken(any(), eq(user))).thenReturn("new-access");
        when(jwtUtils.generateRefreshToken(user)).thenReturn("new-refresh");

        AuthResponse result = service.refreshToken("rf-1", response);

        assertEquals("new-access", result.getAccessToken());
        assertEquals("new-refresh", result.getRefreshToken());
        assertTrue(tokenEntity.isRevoked());
        verify(redisService).deleteCacheToken("rf-1");
        verify(refreshTokenRepository).save(tokenEntity);
        verify(response).addCookie(any(Cookie.class));
    }

    @Test
    void refreshToken_shouldThrowInternalServerError_whenTokenNotFound() {
        when(redisService.getCacheRefreshToken("rf-404")).thenReturn(null);
        when(refreshTokenRepository.findByTokenAndIsRevoked("rf-404")).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class,
                () -> service.refreshToken("rf-404", response));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void getProfile_shouldReturnUserResponse_whenTokenValid() {
        User user = buildUser("u@test.com");
        user.setId("uid-1");
        user.setCancellationCount(3);
        user.setReset(true);
        user.setBookingLockedUntil(LocalDateTime.of(2026, 4, 25, 10, 0));

        when(request.getHeader("Authorization")).thenReturn("Bearer access-1");
        when(jwtUtils.extractUsername("access-1")).thenReturn("u@test.com");
        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));

        UserResponse result = service.getProfile(request);

        assertEquals("uid-1", result.getId());
        assertEquals("u@test.com", result.getEmail());
        assertEquals("A", result.getFirstName());
        assertEquals(3, result.getCancellationCount());
        assertTrue(result.isReset());
    }

    @Test
    void getProfile_shouldThrowInternalServerError_whenUserMissing() {
        when(request.getHeader("Authorization")).thenReturn("Bearer access-1");
        when(jwtUtils.extractUsername("access-1")).thenReturn("notfound@test.com");
        when(userRepository.findByEmail("notfound@test.com")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> service.getProfile(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }
}
