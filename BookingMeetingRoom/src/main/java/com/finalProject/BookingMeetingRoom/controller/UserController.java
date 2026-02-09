package com.finalProject.BookingMeetingRoom.controller;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.ChangePasswordRequest;
import com.finalProject.BookingMeetingRoom.model.request.ForgotPasswordRequest;
import com.finalProject.BookingMeetingRoom.model.request.ForgotPasswordVerifyRequest;
import com.finalProject.BookingMeetingRoom.model.request.RegistrationRequest;
import com.finalProject.BookingMeetingRoom.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.ok(Response.ofSucceeded(userService.register(request)));
    }

    @PreAuthorize("hasAnyAuthority(@authorityConstant.MAKE)")
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        userService.changePassword(request, authentication);
        return ResponseEntity.ok(Response.ofSucceeded("Password changed successfully"));
    }

//    @PreAuthorize("hasAnyAuthority(@authorityConstant.MAKE)")
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

//    @GetMapping
//    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
//    public ResponseEntity<?> getAllUser() {
//        return ResponseEntity.ok(userService.getAllUser());
//    }

}
