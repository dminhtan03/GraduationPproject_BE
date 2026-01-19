package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.dto.request.ChangePasswordRequest;
import com.finalProject.BookingMeetingRoom.model.dto.request.ForgotPasswordRequest;
import com.finalProject.BookingMeetingRoom.model.dto.request.ForgotPasswordVerifyRequest;
import com.finalProject.BookingMeetingRoom.model.dto.request.RegistrationRequest;
import com.finalProject.BookingMeetingRoom.model.dto.response.RegistrationResponse;
import com.finalProject.BookingMeetingRoom.model.dto.response.UserResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface UserService {
    RegistrationResponse register(RegistrationRequest request);

    void changePassword(ChangePasswordRequest request, Authentication authentication);

    void handleForgotPassword(ForgotPasswordRequest request);

    void verifyForgotPassword(ForgotPasswordVerifyRequest request);

    void activateAccount(String validOtp);

    List<UserResponse> getAllUser();
}
