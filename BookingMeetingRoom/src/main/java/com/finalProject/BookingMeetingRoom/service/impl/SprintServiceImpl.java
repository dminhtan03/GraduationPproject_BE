package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.SprintStatus;
import com.finalProject.BookingMeetingRoom.common.enums.TaskStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.*;
import com.finalProject.BookingMeetingRoom.model.request.SprintRequest;
import com.finalProject.BookingMeetingRoom.model.response.SprintResponse;
import com.finalProject.BookingMeetingRoom.model.response.TaskResponse;
import com.finalProject.BookingMeetingRoom.repository.*;
import com.finalProject.BookingMeetingRoom.service.SprintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SprintServiceImpl implements SprintService {

    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskAssignmentRepository assignmentRepository;
    private final TaskSupporterRepository supporterRepository;
    private final com.finalProject.BookingMeetingRoom.repository.ProjectRepository projectRepository;

    private User resolveUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
    }

    private String fullName(User u) {
        if (u == null) return null;
        if (u.getUserInfo() == null) return u.getUsername();
        return ((u.getUserInfo().getFirstName() != null ? u.getUserInfo().getFirstName() : "") +
                " " + (u.getUserInfo().getLastName() != null ? u.getUserInfo().getLastName() : "")).trim();
    }

    private TaskResponse mapTask(Task task) {
        List<TaskAssignment> assignments = assignmentRepository.findByTask_Id(task.getId());
        List<TaskSupporter> supporters = supporterRepository.findByTask_Id(task.getId());

        List<TaskResponse.AssignmentInfo> aInfos = assignments.stream()
                .map(a -> TaskResponse.AssignmentInfo.builder()
                        .id(a.getId())
                        .assigneeId(a.getAssignee() != null ? a.getAssignee().getId() : null)
                        .assigneeName(fullName(a.getAssignee()))
                        .assignerId(a.getAssigner() != null ? a.getAssigner().getId() : null)
                        .assignerName(fullName(a.getAssigner()))
                        .status(a.getStatus().name())
                        .approvalStatus(a.getApprovalStatus() != null ? a.getApprovalStatus().name() : null)
                        .primary(a.isPrimary())
                        .brief(a.getBrief())
                        .how(a.getHow())
                        .rejectionReason(a.getRejectionReason())
                        .build())
                .collect(Collectors.toList());

        List<TaskResponse.SupporterInfo> sInfos = supporters.stream()
                .map(s -> TaskResponse.SupporterInfo.builder()
                        .id(s.getId())
                        .userId(s.getUser().getId())
                        .userName(fullName(s.getUser()))
                        .status(s.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .goal(task.getGoal())
                .expectedResult(task.getExpectedResult())
                .assignmentBrief(task.getAssignmentBrief())
                .assignmentHow(task.getAssignmentHow())
                .priority(task.getPriority().name())
                .status(task.getStatus().name())
                .dueAt(task.getDueAt())
                .createdById(task.getCreatedBy().getId())
                .createdByName(fullName(task.getCreatedBy()))
                .assignedById(task.getAssignedBy() != null ? task.getAssignedBy().getId() : null)
                .assignedByName(fullName(task.getAssignedBy()))
                .reviewerUserId(task.getReviewer() != null ? task.getReviewer().getId() : null)
                .reviewerName(fullName(task.getReviewer()))
                .reviewerStatus(task.getReviewerStatus() != null ? task.getReviewerStatus().name() : null)
                .reviewDecision(task.getReviewDecision() != null ? task.getReviewDecision().name() : null)
                .reviewComment(task.getReviewComment())
                .reviewedAt(task.getReviewedAt())
                .resultNote(task.getResultNote())
                .submittedAt(task.getSubmittedAt())
                .meetingId(task.getMeeting() != null ? task.getMeeting().getId() : null)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .assignments(aInfos)
                .supporters(sInfos)
                .sprintId(task.getSprint() != null ? task.getSprint().getId() : null)
                .sprintName(task.getSprint() != null ? task.getSprint().getName() : null)
                .parentTaskId(task.getParentTask() != null ? task.getParentTask().getId() : null)
                .parentTaskTitle(task.getParentTask() != null ? task.getParentTask().getTitle() : null)
                .build();
    }

    private SprintResponse toResponse(Sprint sprint, boolean includeTasks, User currentUser) {
        List<TaskResponse> taskResponses = new ArrayList<>();
        if (includeTasks && currentUser != null) {
            List<Task> tasks = taskRepository.findVisibleTasksBySprint(sprint.getId(), currentUser.getId());
            taskResponses = tasks.stream().map(this::mapTask).collect(Collectors.toList());
        }
        return SprintResponse.builder()
                .id(sprint.getId())
                .name(sprint.getName())
                .startDate(sprint.getStartDate())
                .endDate(sprint.getEndDate())
                .status(sprint.getStatus().name())
                .createdAt(sprint.getCreatedAt())
                .updatedAt(sprint.getUpdatedAt())
                .createdById(sprint.getCreatedBy() != null ? sprint.getCreatedBy().getId() : null)
                .createdByName(sprint.getCreatedBy() != null ? fullName(sprint.getCreatedBy()) : null)
                .projectId(sprint.getProject() != null ? sprint.getProject().getId() : null)
                .projectName(sprint.getProject() != null ? sprint.getProject().getName() : null)
                .tasks(taskResponses)
                .build();
    }

    @Override
    @Transactional
    public SprintResponse createSprint(SprintRequest request, Authentication auth) {
        User currentUser = resolveUser(auth); // Check if authenticated
        Sprint s = new Sprint();
        s.setCreatedBy(currentUser);
        s.setName(request.getName());
        if (request.getStartDate() != null && !request.getStartDate().isBlank()) {
            s.setStartDate(LocalDate.parse(request.getStartDate()));
        }
        if (request.getEndDate() != null && !request.getEndDate().isBlank()) {
            s.setEndDate(LocalDate.parse(request.getEndDate()));
        }
        if (request.getStatus() != null) {
            try {
                s.setStatus(SprintStatus.valueOf(request.getStatus().toUpperCase()));
            } catch (Exception ignored) {}
        }
        if (request.getProjectId() != null && !request.getProjectId().isBlank()) {
            projectRepository.findById(request.getProjectId()).ifPresent(s::setProject);
        }
        sprintRepository.save(s);
        return toResponse(s, false, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintResponse> listSprints(Authentication auth) {
        User currentUser = resolveUser(auth);
        return sprintRepository.findVisibleSprints(currentUser.getId()).stream()
                .map(s -> toResponse(s, true, currentUser))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SprintResponse getSprint(String sprintId, Authentication auth) {
        User currentUser = resolveUser(auth);
        Sprint s = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Sprint not found"));
        return toResponse(s, true, currentUser);
    }

    @Override
    @Transactional
    public SprintResponse updateSprint(String sprintId, SprintRequest request, Authentication auth) {
        User currentUser = resolveUser(auth);
        Sprint s = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Sprint not found"));
        if (request.getName() != null) s.setName(request.getName());
        if (request.getStartDate() != null && !request.getStartDate().isBlank()) {
            s.setStartDate(LocalDate.parse(request.getStartDate()));
        }
        if (request.getEndDate() != null && !request.getEndDate().isBlank()) {
            s.setEndDate(LocalDate.parse(request.getEndDate()));
        }
        if (request.getStatus() != null) {
            try {
                s.setStatus(SprintStatus.valueOf(request.getStatus().toUpperCase()));
            } catch (Exception ignored) {}
        }
        sprintRepository.save(s);
        return toResponse(s, true, currentUser);
    }

    @Override
    @Transactional
    public SprintResponse endSprint(String sprintId, String targetSprintId, Authentication auth) {
        User currentUser = resolveUser(auth);
        Sprint source = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Sprint not found"));
        
        source.setStatus(SprintStatus.COMPLETED);
        sprintRepository.save(source);

        // Fetch target sprint if provided
        Sprint target = null;
        if (targetSprintId != null && !targetSprintId.isBlank() && !"backlog".equalsIgnoreCase(targetSprintId)) {
            target = sprintRepository.findById(targetSprintId).orElse(null);
        }

        // Find all incomplete tasks in source sprint
        List<Task> tasks = taskRepository.findBySprint_IdOrderByCreatedAtDesc(sprintId);
        for (Task t : tasks) {
            if (t.getStatus() != TaskStatus.DONE && t.getStatus() != TaskStatus.CANCELLED) {
                // Move incomplete tasks to target sprint or backlog (null)
                t.setSprint(target);
                taskRepository.save(t);
            }
        }

        return toResponse(source, true, currentUser);
    }

    @Override
    @Transactional
    public void deleteSprint(String sprintId, Authentication auth) {
        resolveUser(auth);
        Sprint s = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Sprint not found"));
        // Remove sprint link from tasks
        List<Task> tasks = taskRepository.findBySprint_IdOrderByCreatedAtDesc(sprintId);
        for (Task t : tasks) {
            t.setSprint(null);
            taskRepository.save(t);
        }
        sprintRepository.delete(s);
    }
}
