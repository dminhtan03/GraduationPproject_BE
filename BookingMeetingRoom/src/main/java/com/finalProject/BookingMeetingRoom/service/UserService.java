package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.response.RegistrationResponse;
import com.finalProject.BookingMeetingRoom.model.request.*;
import org.springframework.security.core.Authentication;


public interface UserService {
    RegistrationResponse register(RegistrationRequest request);

    void changePassword(ChangePasswordRequest request, Authentication authentication);

    void handleForgotPassword(ForgotPasswordRequest request);

    void verifyForgotPassword(ForgotPasswordVerifyRequest request);

    void activateAccount(String validOtp);

    void resendOtp(ResendOtpRequest request);

    void updateUserInfo(UpdateUserInfoRequest request, Authentication authentication);
}
