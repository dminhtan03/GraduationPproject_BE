package com.finalProject.BookingMeetingRoom.controller.task;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.AssignTaskRequest;
import com.finalProject.BookingMeetingRoom.model.request.ReviewTaskRequest;
import com.finalProject.BookingMeetingRoom.model.request.SubmitTaskRequest;
import com.finalProject.BookingMeetingRoom.model.request.TaskRequest;
import com.finalProject.BookingMeetingRoom.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> createTask(@RequestBody TaskRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ofSucceeded(taskService.createTask(request, auth)));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<?> getTask(@PathVariable String taskId, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(taskService.getTask(taskId, auth)));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<?> updateTaskPut(@PathVariable String taskId,
                                            @RequestBody TaskRequest request, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(taskService.updateTask(taskId, request, auth)));
    }

    @PatchMapping("/{taskId}")
    public ResponseEntity<?> updateTaskPatch(@PathVariable String taskId,
                                              @RequestBody TaskRequest request, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(taskService.updateTask(taskId, request, auth)));
    }

    @PatchMapping("/{taskId}/cancel")
    public ResponseEntity<?> cancelTask(@PathVariable String taskId, Authentication auth) {
        taskService.cancelTask(taskId, auth);
        return ResponseEntity.ok(Response.ofSucceeded("Task cancelled"));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> deleteTask(@PathVariable String taskId, Authentication auth) {
        taskService.deleteTask(taskId, auth);
        return ResponseEntity.ok(Response.ofSucceeded("Task deleted"));
    }

    // ── Listing ───────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<?> listTasks(
            @RequestParam(value = "task_type", required = false) String taskType,
            @RequestParam(required = false) String type,
            Authentication auth) {
        String resolved = taskType != null ? taskType : type;
        return ResponseEntity.ok(Response.ofSucceeded(taskService.listMyTasks(resolved, auth)));
    }

    @GetMapping("/assigned-to-me")
    public ResponseEntity<?> listAssignedToMe(Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(taskService.listAssignedToMe(auth)));
    }

    @GetMapping("/pending-assignments")
    public ResponseEntity<?> listPendingAssignments(Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(taskService.listPendingAssignments(auth)));
    }

    @GetMapping("/pending-approvals")
    public ResponseEntity<?> listPendingApprovals(Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(taskService.listPendingApprovals(auth)));
    }

    @GetMapping("/summary/today")
    public ResponseEntity<?> getTodaySummary(Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(taskService.getTodaySummary(auth)));
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<?> changeStatus(@PathVariable String taskId,
                                           @RequestBody Map<String, String> body, Authentication auth) {
        String status = body.get("status");
        return ResponseEntity.ok(Response.ofSucceeded(taskService.changeStatus(taskId, status, auth)));
    }

    @PostMapping("/{taskId}/submit")
    public ResponseEntity<?> submitForReview(@PathVariable String taskId,
                                              @RequestBody SubmitTaskRequest request, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(taskService.submitForReview(taskId, request, auth)));
    }

    @PostMapping("/{taskId}/review")
    public ResponseEntity<?> reviewTask(@PathVariable String taskId,
                                         @RequestBody ReviewTaskRequest request, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(taskService.reviewTask(taskId, request, auth)));
    }

    // ── Assignment ────────────────────────────────────────────────────────────

    @PostMapping("/{taskId}/assign")
    public ResponseEntity<?> assignTask(@PathVariable String taskId,
                                         @RequestBody AssignTaskRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ofSucceeded(taskService.assignTask(taskId, request, auth)));
    }

    // Keep /assignments for ai-platform compatibility
    @PostMapping("/{taskId}/assignments")
    public ResponseEntity<?> assignTaskLegacy(@PathVariable String taskId,
                                               @RequestBody AssignTaskRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ofSucceeded(taskService.assignTask(taskId, request, auth)));
    }

    @PostMapping("/{taskId}/assignments/{assignmentId}/respond")
    public ResponseEntity<?> respondToAssignment(@PathVariable String taskId,
                                                   @PathVariable String assignmentId,
                                                   @RequestBody Map<String, String> body, Authentication auth) {
        String response = body.get("response");
        return ResponseEntity.ok(Response.ofSucceeded(
                taskService.respondToAssignment(taskId, assignmentId, response, auth)));
    }

    @PostMapping("/{taskId}/assignments/{assignmentId}/approve")
    public ResponseEntity<?> approveAssignment(@PathVariable String taskId,
                                                @PathVariable String assignmentId, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(taskService.approveAssignment(taskId, assignmentId, auth)));
    }

    // ── Reviewer ─────────────────────────────────────────────────────────────

    @PostMapping("/{taskId}/reviewer")
    public ResponseEntity<?> inviteReviewer(@PathVariable String taskId,
                                             @RequestBody Map<String, String> body, Authentication auth) {
        String reviewerUserId = body.getOrDefault("reviewer_user_id", body.get("reviewerUserId"));
        return ResponseEntity.ok(Response.ofSucceeded(taskService.inviteReviewer(taskId, reviewerUserId, auth)));
    }

    @PostMapping("/{taskId}/reviewer/respond")
    public ResponseEntity<?> respondReviewerInvite(@PathVariable String taskId,
                                                    @RequestBody Map<String, String> body, Authentication auth) {
        String response = body.get("response");
        return ResponseEntity.ok(Response.ofSucceeded(taskService.respondReviewerInvite(taskId, response, auth)));
    }

    // ── Supporter ─────────────────────────────────────────────────────────────

    @PostMapping("/{taskId}/supporters")
    public ResponseEntity<?> addSupporter(@PathVariable String taskId,
                                           @RequestBody Map<String, String> body, Authentication auth) {
        String userId = body.getOrDefault("user_id", body.getOrDefault("userId", body.get("supporter_user_id")));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ofSucceeded(taskService.addSupporter(taskId, userId, auth)));
    }

    @PostMapping("/{taskId}/supporters/{supporterId}/respond")
    public ResponseEntity<?> respondSupporterInvite(@PathVariable String taskId,
                                                     @PathVariable String supporterId,
                                                     @RequestBody Map<String, String> body, Authentication auth) {
        String response = body.get("response");
        return ResponseEntity.ok(Response.ofSucceeded(
                taskService.respondSupporterInvite(taskId, supporterId, response, auth)));
    }
}
