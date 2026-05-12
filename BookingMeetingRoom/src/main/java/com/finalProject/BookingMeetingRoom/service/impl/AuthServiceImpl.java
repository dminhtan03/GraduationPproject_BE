package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.common.utils.JwtUtils;
import com.finalProject.BookingMeetingRoom.mapper.UserMapper;
import com.finalProject.BookingMeetingRoom.model.request.LoginRequest;
import com.finalProject.BookingMeetingRoom.model.response.AuthResponse;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.response.UserResponse;
import com.finalProject.BookingMeetingRoom.repository.RefreshTokenRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.AuthService;
import com.finalProject.BookingMeetingRoom.service.RedisService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

import java.util.HashMap;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final Logger logger = LogManager.getLogger(AuthServiceImpl.class);
    private final UserRepository userRepository;
    private final AuthenticationManager authManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RedisService redisService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    @Value("${application.security.google.client-id}")
    private String googleClientId;

    public AuthResponse doLogin(LoginRequest request, HttpServletResponse response) {
        try {
            var user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

            if (!user.isEnabled()) {
                throw new CustomException(ResponseCode.USER_DISABLE);
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                user.setLoginCount(user.getLoginCount() + 1);
                if (user.getLoginCount() == 3) {
                    user.setLocked(true);
                    userRepository.save(user);
                    throw new CustomException(ResponseCode.ACCOUNT_LOCKED);
                }
                userRepository.save(user);
                throw new CustomException(ResponseCode.INVALID_PASSWORD);
            } else {
                user.setLoginCount(0);
                userRepository.save(user);
            }

            var authentication = authManager.authenticate(new UsernamePasswordAuthenticationToken(
                    request.getEmail(), request.getPassword())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            logger.info("User {} logged in with roles {}", userDetails.getUsername(), userDetails.getAuthorities());

            var claims = new HashMap<String, Object>();
            claims.put("user", user.getUserInfo().getFullName());

            String accessToken = jwtUtils.generateToken(claims, (User) authentication.getPrincipal());
            String refreshToken = jwtUtils.generateRefreshToken(user);

            var refreshTokenCookies = new Cookie("refreshToken", refreshToken);
            refreshTokenCookies.setHttpOnly(true);
            refreshTokenCookies.setSecure(true);
            refreshTokenCookies.setPath("/");
            refreshTokenCookies.setMaxAge((int) (refreshTokenExpiration / 1000));
            response.addCookie(refreshTokenCookies);

            return new AuthResponse(accessToken, refreshToken);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public AuthResponse loginWithGoogle(String idToken, HttpServletResponse response) {
        try {
            var transport = GoogleNetHttpTransport.newTrustedTransport();
            var jsonFactory = JacksonFactory.getDefaultInstance();

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken googleIdToken = verifier.verify(idToken);

            if (googleIdToken == null) {
                throw new CustomException(ResponseCode.VALIDATION_FAILED);
            }

            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            String email = payload.getEmail();

            Optional<User> optionalUser = userRepository.findByEmail(email);
            if (optionalUser.isEmpty()) {
                throw new CustomException(ResponseCode.USER_NOT_FOUND);
            }

            User user = optionalUser.get();

            if (!user.isEnabled()) {
                throw new CustomException(ResponseCode.USER_DISABLE);
            }
            if (user.isLocked()) {
                throw new CustomException(ResponseCode.ACCOUNT_LOCKED);
            }
            user.setLoginCount(0);
            userRepository.save(user);

            var claims = new HashMap<String, Object>();
            claims.put("user", user.getUserInfo().getFullName());

            String accessToken = jwtUtils.generateToken(claims, user);
            String refreshToken = jwtUtils.generateRefreshToken(user);

            Cookie refreshTokenCookies = new Cookie("refreshToken", refreshToken);
            refreshTokenCookies.setHttpOnly(true);
            refreshTokenCookies.setSecure(true);
            refreshTokenCookies.setPath("/");
            refreshTokenCookies.setMaxAge((int) (refreshTokenExpiration / 1000));
            response.addCookie(refreshTokenCookies);

            return new AuthResponse(accessToken, refreshToken);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    public void doLogout(HttpServletRequest request) {
        try {
            final String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new CustomException(ResponseCode.AUTH_HEADER_NOT_FOUND);
            }

            String token = authHeader.substring(7);
            long expirationMillis = jwtUtils.extractExpiration(token).getTime() - System.currentTimeMillis();

            if (expirationMillis > 0) {
                String key = "BlackList:" + token;
                redisService.setValue(key, token, expirationMillis, TimeUnit.MILLISECONDS);
            }

//            Extract refresh token from cookie
            String refreshToken = null;
            Cookie[] cookies = request.getCookies();

            if(request.getCookies() != null) {
                for (Cookie cookie : cookies) {
                    if("refreshToken".equals(cookie.getName())) {
                        refreshToken = cookie.getValue();
                        break;
                    }
                }
            }

            if(refreshToken != null) {

                var rfToken = refreshTokenRepository.findByTokenAndIsRevoked(refreshToken);
                if(rfToken == null) {
                    throw new CustomException(ResponseCode.REFRESH_TOKEN_NOT_FOUND);
                }
                rfToken.setRevoked(true);
                redisService.deleteCacheToken(String.valueOf(rfToken.getId()));
                refreshTokenRepository.save(rfToken);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new CustomException(ResponseCode.CACHE_FAILED);
        }
    }


    public AuthResponse refreshToken(String refreshToken, HttpServletResponse response) {
        try {
            var tokenEntity = Optional.ofNullable(redisService.getCacheRefreshToken(refreshToken))
                    .orElseGet(() -> refreshTokenRepository.findByTokenAndIsRevoked(refreshToken));

            if (tokenEntity == null) {
                throw new CustomException(ResponseCode.REFRESH_TOKEN_NOT_FOUND);
            }

            User user = tokenEntity.getUser();

            var claims = new HashMap<String, Object>();
            claims.put("user", user.getUserInfo().getFullName());
            String newAccessToken = jwtUtils.generateToken(claims, user);

            String newRefreshToken = jwtUtils.generateRefreshToken(user);
            tokenEntity.setRevoked(true);
            refreshTokenRepository.save(tokenEntity);
            redisService.deleteCacheToken(refreshToken);

            Cookie refreshTokenCookies = new Cookie("refreshToken", newRefreshToken);
            refreshTokenCookies.setHttpOnly(true);
            refreshTokenCookies.setSecure(true);
            refreshTokenCookies.setPath("/");
            refreshTokenCookies.setMaxAge((int) (refreshTokenExpiration/1000));
            response.addCookie(refreshTokenCookies);

            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .build();
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Retrieves the profile of the currently authenticated user.
     *
     * @param request the HTTP request containing the access token
     * @return UserResponse containing user profile information
     */
    @Override
    public UserResponse getProfile(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");

            String accessToken = authHeader.substring(7);

            String userEmail = jwtUtils.extractUsername(accessToken);
            var user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
            var userInfo = user.getUserInfo();

            return UserResponse.builder()
                    .id(user.getId())
                    .firstName(userInfo.getFirstName())
                    .lastName(userInfo.getLastName())
                    .email(userInfo.getEmail())
                    .phoneNumber(userInfo.getPhoneNumber())
                    .address(userInfo.getAddress())
                    .department(userInfo.getDepartment())
                    .gender(userInfo.getGender())
                    .bookingLockedUntil(user.getBookingLockedUntil()) //start add lock funtion booking
                    .cancellationCount(user.getCancellationCount())// end add lock funtion booking
                    .isReset(user.isReset())
                    .build();
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }
}
