package com.finalProject.BookingMeetingRoom.controller.feedback;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.FeedbackRequest;
import com.finalProject.BookingMeetingRoom.service.FeedBackService;

import jakarta.validation.Valid;

@RestController
@RequestMapping(value = "/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedBackService feedbackService;

    @PostMapping("/add")
    public ResponseEntity<?> createFeedback(@Valid @RequestBody FeedbackRequest request, Authentication connectedUser) {
        feedbackService.createFeedback(request, connectedUser);
        return ResponseEntity.ok(Response.ofSucceeded("Feedback created successfully"));
    }

    @GetMapping(value = "")
    public ResponseEntity<?> getFeedbackOfARoom(
            @RequestParam(name = "roomId", required = false) String roomId,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "10") int size

    ) {
        return ResponseEntity.ok(Response.ofSucceeded(feedbackService.getFeedbackOfARoom(roomId, page, size)));
    }
    @GetMapping(value = "/admin")
    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    public ResponseEntity<?> getAllFeedbacks(
            @RequestParam(name = "rating", required = false) Integer rating,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(Response.ofSucceeded(feedbackService.getAllFeedbacks(rating, email, page, size)));
    }

    @GetMapping(value = "/admin/{id}")
    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    public ResponseEntity<?> getFeedbackDetail(@PathVariable String id) {
        return ResponseEntity.ok(Response.ofSucceeded(feedbackService.getFeedbackDetail(id)));
    }
    
}
