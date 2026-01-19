package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.dto.request.LoginRequest;
import com.finalProject.BookingMeetingRoom.model.dto.response.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    AuthResponse doLogin(LoginRequest request, HttpServletResponse response);

    void doLogout(HttpServletRequest request);

    AuthResponse refreshToken(String refreshToken, HttpServletResponse response);

}
