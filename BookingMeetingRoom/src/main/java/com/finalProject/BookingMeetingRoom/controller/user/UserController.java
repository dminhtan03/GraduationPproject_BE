package com.finalProject.BookingMeetingRoom.controller.user;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.request.*;
import com.finalProject.BookingMeetingRoom.model.request.UpdateUserInfoRequest;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.UserService;
// start add multipart import
import org.springframework.web.multipart.MultipartFile;
// end add multipart import
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.ok(Response.ofSucceeded(userService.register(request)));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        userService.changePassword(request, authentication);
        return ResponseEntity.ok(Response.ofSucceeded("Password changed successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> handleForgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        userService.handleForgotPassword(request);
        return ResponseEntity.ok(Response.ofSucceeded("Otp has been sent to your email"));
    }

    @PostMapping("/verify-forgot-password")
    public ResponseEntity<?> verifyForgotPassword(
            @Valid @RequestBody ForgotPasswordVerifyRequest request) {
        userService.verifyForgotPassword(request);
        return ResponseEntity.ok(Response.ofSucceeded("New password has been sent to your email"));
    }

    @GetMapping("/activate-account")
    public ResponseEntity<?> activateAccount(
            @RequestParam(name = "validOtp") String validOtp) {
        userService.activateAccount(validOtp);
        return ResponseEntity.ok(Response.ofSucceeded("Activate account successfully"));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(
            @RequestBody ResendOtpRequest resendOtpRequest) {
        userService.resendOtp(resendOtpRequest);
        return ResponseEntity.ok(Response.ofSucceeded("Resend OTP successfully"));
    }

    // start add adminAddUser and importUsersFromExcel api
    @PostMapping("/admin/add")
    public ResponseEntity<?> adminAddUser(@Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.ok(Response.ofSucceeded(userService.adminAddUser(request)));
    }

    @PostMapping("/admin/import-excel")
    public ResponseEntity<?> importUsersFromExcel(@RequestParam("file") MultipartFile file) {
        userService.importUsersFromExcel(file);
        return ResponseEntity.ok(Response.ofSucceeded("Users imported successfully from excel"));
    }
    // end add adminAddUser and importUsersFromExcel api

    @PutMapping("/update-info")
    public ResponseEntity<?> updateUserInfo(
            @Valid @RequestBody UpdateUserInfoRequest request,
            Authentication authentication) {
        userService.updateUserInfo(request, authentication);
        return ResponseEntity.ok(Response.ofSucceeded("User info updated successfully"));
    }

//    @GetMapping
//    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
//    public ResponseEntity<?> getAllUser() {
//        return ResponseEntity.ok(userService.getAllUser());
//    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
        UserInfo info = user.getUserInfo();
        return ResponseEntity.ok(Response.ofSucceeded(Map.of(
                "id",       user.getId(),
                "email",    info != null && info.getEmail() != null ? info.getEmail() : authentication.getName(),
                "fullName", info != null && info.getFullName() != null ? info.getFullName() : ""
        )));
    }

    // start+ check email exists (dùng khi invite participant trước khi tạo event)
    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmailExists(@RequestParam String email) {
        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND,
                        "No account found with email: " + email.trim()));
        UserInfo info = user.getUserInfo();
        return ResponseEntity.ok(Response.ofSucceeded(Map.of(
                "email",    info != null && info.getEmail() != null ? info.getEmail() : email.trim(),
                "fullName", info != null && info.getFullName() != null ? info.getFullName() : ""
        )));
    }
    // end+ check email exists

}
