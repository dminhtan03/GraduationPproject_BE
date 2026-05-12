package com.finalProject.BookingMeetingRoom.controller.summary;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/summary/today — ai-platform TaskAPI calls this */
@RestController
@RequestMapping("/api/v1/summary")
@RequiredArgsConstructor
public class SummaryController {

    private final TaskService taskService;

    @GetMapping("/today")
    public ResponseEntity<?> getTodaySummary(Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(taskService.getTodaySummary(auth)));
    }
}
