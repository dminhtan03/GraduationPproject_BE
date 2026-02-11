package com.finalProject.BookingMeetingRoom.controller.auth;

import com.finalProject.BookingMeetingRoom.common.payload.Response;

import com.finalProject.BookingMeetingRoom.model.request.GoogleLoginRequest;
import com.finalProject.BookingMeetingRoom.model.request.LoginRequest;
import com.finalProject.BookingMeetingRoom.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/doLogin")
    public ResponseEntity<?> doLogin(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        return ResponseEntity.ok(Response.ofSucceeded(authService.doLogin(request, response)));
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletResponse response
    ) {
        return ResponseEntity.ok(Response.ofSucceeded(authService.loginWithGoogle(request.getIdToken(), response)));
    }

    @PostMapping("/doLogout")
    public ResponseEntity<?> doLogout(
            HttpServletRequest request) {
        authService.doLogout(request);
        return ResponseEntity.ok(Response.ofSucceeded("Logout successfully"));
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<?> doRefreshToken(
            @RequestParam(value = "refreshToken") String refreshToken,
            HttpServletResponse response) {
        return ResponseEntity.ok(Response.ofSucceeded(authService.refreshToken(refreshToken, response)));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(HttpServletRequest request) {
        return ResponseEntity.ok(Response.ofSucceeded(authService.getProfile(request)));
    }
}
