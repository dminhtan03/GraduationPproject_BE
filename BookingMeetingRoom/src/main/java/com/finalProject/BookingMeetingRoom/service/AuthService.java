package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.request.LoginRequest;
import com.finalProject.BookingMeetingRoom.model.response.AuthResponse;
import com.finalProject.BookingMeetingRoom.model.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    AuthResponse doLogin(LoginRequest request, HttpServletResponse response);

    void doLogout(HttpServletRequest request);

    AuthResponse refreshToken(String refreshToken, HttpServletResponse response);

    AuthResponse loginWithGoogle(String idToken, HttpServletResponse response);

    UserResponse getProfile(HttpServletRequest request);
}
