package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.response.RegistrationResponse;
import com.finalProject.BookingMeetingRoom.model.request.*;
import org.springframework.security.core.Authentication;
// start add multipart import
import org.springframework.web.multipart.MultipartFile;
// end add multipart import


public interface UserService {
    RegistrationResponse register(RegistrationRequest request);

    void changePassword(ChangePasswordRequest request, Authentication authentication);

    void handleForgotPassword(ForgotPasswordRequest request);

    void verifyForgotPassword(ForgotPasswordVerifyRequest request);

    void activateAccount(String validOtp);

    void resendOtp(ResendOtpRequest request);

    void updateUserInfo(UpdateUserInfoRequest request, Authentication authentication);

    // start add adminAddUser and importUsersFromExcel methods
    RegistrationResponse adminAddUser(RegistrationRequest request);
    void importUsersFromExcel(MultipartFile file);
    // end add adminAddUser and importUsersFromExcel methods
}
