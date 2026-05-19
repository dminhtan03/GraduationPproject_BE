package com.finalProject.BookingMeetingRoom.controller.sprint;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.SprintRequest;
import com.finalProject.BookingMeetingRoom.service.SprintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;

    @PostMapping
    public ResponseEntity<?> createSprint(@RequestBody SprintRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ofSucceeded(sprintService.createSprint(request, auth)));
    }

    @GetMapping
    public ResponseEntity<?> listSprints(Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(sprintService.listSprints(auth)));
    }

    @GetMapping("/{sprintId}")
    public ResponseEntity<?> getSprint(@PathVariable String sprintId, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(sprintService.getSprint(sprintId, auth)));
    }

    @PutMapping("/{sprintId}")
    public ResponseEntity<?> updateSprint(@PathVariable String sprintId,
                                           @RequestBody SprintRequest request, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(sprintService.updateSprint(sprintId, request, auth)));
    }

    @PostMapping("/{sprintId}/end")
    public ResponseEntity<?> endSprint(@PathVariable String sprintId,
                                        @RequestBody Map<String, String> body, Authentication auth) {
        String targetSprintId = body.get("targetSprintId");
        return ResponseEntity.ok(Response.ofSucceeded(sprintService.endSprint(sprintId, targetSprintId, auth)));
    }

    @DeleteMapping("/{sprintId}")
    public ResponseEntity<?> deleteSprint(@PathVariable String sprintId, Authentication auth) {
        sprintService.deleteSprint(sprintId, auth);
        return ResponseEntity.ok(Response.ofSucceeded("Sprint deleted"));
    }
}
