package com.finalProject.BookingMeetingRoom.controller.project;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.ProjectRequest;
import com.finalProject.BookingMeetingRoom.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody ProjectRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ofSucceeded(projectService.createProject(request, auth)));
    }

    @GetMapping
    public ResponseEntity<?> listProjects(Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(projectService.listMyProjects(auth)));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<?> getProject(@PathVariable String projectId, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(projectService.getProject(projectId, auth)));
    }

    @PutMapping("/{projectId}")
    public ResponseEntity<?> updateProject(@PathVariable String projectId,
                                            @RequestBody ProjectRequest request, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(projectService.updateProject(projectId, request, auth)));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<?> deleteProject(@PathVariable String projectId, Authentication auth) {
        projectService.deleteProject(projectId, auth);
        return ResponseEntity.ok(Response.ofSucceeded("Project deleted"));
    }

    @PostMapping("/{projectId}/members")
    public ResponseEntity<?> addMember(@PathVariable String projectId,
                                        @RequestBody Map<String, String> body, Authentication auth) {
        return ResponseEntity.ok(Response.ofSucceeded(
                projectService.addMember(projectId, body.get("userId"), auth)));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    public ResponseEntity<?> removeMember(@PathVariable String projectId,
                                           @PathVariable String userId, Authentication auth) {
        projectService.removeMember(projectId, userId, auth);
        return ResponseEntity.ok(Response.ofSucceeded("Member removed"));
    }

    /** POST /api/v1/projects/{projectId}/repair-tasks
     *  Fixes existing tasks that belong to this project's sprints but have
     *  PROJECT_ID missing or pointing to a different project. */
    @PostMapping("/{projectId}/repair-tasks")
    public ResponseEntity<?> repairTasks(@PathVariable String projectId, Authentication auth) {
        int fixed = projectService.repairProjectTasks(projectId, auth);
        return ResponseEntity.ok(Response.ofSucceeded(
                java.util.Map.of("fixed", fixed, "message", fixed + " tasks updated")));
    }
}
