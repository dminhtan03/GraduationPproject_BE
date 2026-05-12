package com.finalProject.BookingMeetingRoom.controller.notification;


import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.service.NotificationService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/getAll")
    public ResponseEntity<?> getAllNotifications(Authentication authentication ,
                                                 @RequestParam(value = "page", required = false, defaultValue = "0") int page ,
                                                 @RequestParam(value = "size",required = false, defaultValue = "10") int size) {

        return ResponseEntity.ok(Response.ofSucceeded(notificationService.getAllNotifications(authentication, page, size)));
    }

    @PutMapping("/markAsRead/{notificationId}")
    public ResponseEntity<?> markNotificationAsRead(@PathVariable String notificationId) {

        return ResponseEntity.ok(Response.ofSucceeded(notificationService.markNotificationAsRead(notificationId)));
    }

}
