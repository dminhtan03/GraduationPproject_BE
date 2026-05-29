package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.*;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.*;
import com.finalProject.BookingMeetingRoom.model.request.*;
import com.finalProject.BookingMeetingRoom.model.response.*;
import com.finalProject.BookingMeetingRoom.repository.*;
import com.finalProject.BookingMeetingRoom.model.request.NotificationRequest;
import com.finalProject.BookingMeetingRoom.service.NotificationService;
import com.finalProject.BookingMeetingRoom.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository assignmentRepository;
    private final TaskSupporterRepository supporterRepository;
    private final TaskStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final SprintRepository sprintRepository;
    private final TaskCommentRepository commentRepository;
    private final NotificationService notificationService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void notify(String userId, String title, String content) {
        notifyInternal(userId, title, content, false);
    }

    private void notifyWithEmail(String userId, String title, String content) {
        notifyInternal(userId, title, content, true);
    }

    private void notifyInternal(String userId, String title, String content, boolean sendEmail) {
        if (userId == null || userId.isBlank()) return;
        try {
            NotificationRequest req = new NotificationRequest();
            req.setUserId(userId);
            req.setTitle(title);
            req.setContent(content);
            req.setCreatedAt(LocalDateTime.now());
            req.sendEmail = sendEmail;
            notificationService.sendNotification(List.of(req));
        } catch (Exception e) {
            log.warn("Failed to send task notification to {}: {}", userId, e.getMessage());
        }
    }

    private User resolveUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
    }

    private Task findTask(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(ResponseCode.TASK_NOT_FOUND));
    }

    private String fullName(User u) {
        if (u == null) return null;
        if (u.getUserInfo() == null) return u.getUsername();
        return ((u.getUserInfo().getFirstName() != null ? u.getUserInfo().getFirstName() : "") +
                " " + (u.getUserInfo().getLastName() != null ? u.getUserInfo().getLastName() : "")).trim();
    }

    private void saveHistory(Task task, String fromStatus, String toStatus, User changedBy) {
        TaskStatusHistory h = new TaskStatusHistory();
        h.setTask(task);
        h.setFromStatus(fromStatus);
        h.setToStatus(toStatus);
        h.setChangedBy(changedBy);
        historyRepository.save(h);
    }

    private LocalDateTime parseDueAt(String dueAtStr) {
        if (dueAtStr == null || dueAtStr.isBlank()) return null;
        try {
            String normalized = dueAtStr.endsWith("Z") ? dueAtStr.replace("Z", "") : dueAtStr;
            return LocalDateTime.parse(normalized.length() == 10
                    ? normalized + "T00:00:00" : normalized.substring(0, 19));
        } catch (Exception ex) {
            log.warn("Could not parse dueAt: {}", dueAtStr);
            return null;
        }
    }

    public TaskResponse toResponse(Task task) {
        return toResponse(task, true);
    }

    private TaskResponse toResponse(Task task, boolean includeSubtasks) {
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

        List<TaskResponse> subtaskResponses = new ArrayList<>();
        if (includeSubtasks) {
            List<Task> subtasks = taskRepository.findByParentTask_IdOrderByCreatedAtDesc(task.getId());
            subtaskResponses = subtasks.stream()
                    .map(sub -> toResponse(sub, false))
                    .collect(Collectors.toList());
        }

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
                .subtasks(subtaskResponses)
                .build();
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public TaskResponse createTask(TaskRequest request, Authentication auth) {
        User creator = resolveUser(auth);
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setGoal(request.getGoal());
        task.setExpectedResult(request.getExpectedResult());
        task.setAssignmentBrief(request.getAssignmentBrief() != null ? request.getAssignmentBrief() : request.getBrief());
        task.setAssignmentHow(request.getAssignmentHow() != null ? request.getAssignmentHow() : request.getHow());
        task.setCreatedBy(creator);
        task.setAssignedBy(creator);

        String priorityStr = request.getPriority() != null ? request.getPriority().toUpperCase() : "MEDIUM";
        try { task.setPriority(TaskPriority.valueOf(priorityStr)); }
        catch (IllegalArgumentException e) { task.setPriority(TaskPriority.MEDIUM); }

        task.setStatus(TaskStatus.TODO);
        task.setDueAt(parseDueAt(request.getDue_at() != null ? request.getDue_at() : request.getDueAt()));

        if (request.getMeetingId() != null)
            meetingRepository.findById(request.getMeetingId()).ifPresent(task::setMeeting);

        // Optional reviewer
        if (request.getReviewerUserId() != null) {
            userRepository.findById(request.getReviewerUserId()).ifPresent(reviewer -> {
                task.setReviewer(reviewer);
                task.setReviewerStatus(ReviewerStatus.PENDING);
            });
        }

        if (request.getSprintId() != null && !request.getSprintId().isBlank()) {
            sprintRepository.findById(request.getSprintId()).ifPresent(task::setSprint);
        }
        if (request.getParentTaskId() != null && !request.getParentTaskId().isBlank()) {
            taskRepository.findById(request.getParentTaskId()).ifPresent(task::setParentTask);
        }

        taskRepository.save(task);
        saveHistory(task, null, TaskStatus.TODO.name(), creator);

        // Optional: create assignment in same call
        if (request.getAssigneeId() != null) {
            userRepository.findById(request.getAssigneeId()).ifPresent(assignee -> {
                TaskAssignment a = new TaskAssignment();
                a.setTask(task);
                a.setAssignee(assignee);
                a.setAssigner(creator);
                a.setBrief(task.getAssignmentBrief());
                a.setHow(task.getAssignmentHow());
                assignmentRepository.save(a);
            });
        }

        return toResponse(task);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getTask(String taskId, Authentication auth) {
        return toResponse(findTask(taskId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> listMyTasks(String taskType, String search, String status, Authentication auth) {
        User user = resolveUser(auth);

        TaskStatus taskStatus = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try { taskStatus = TaskStatus.valueOf(status.toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }
        String searchTerm = (search != null && !search.isBlank()) ? search : null;

        List<Task> tasks;
        if ("personal".equalsIgnoreCase(taskType)) {
            tasks = taskRepository.findByCreatedBy_IdAndMeetingIsNullOrderByCreatedAtDesc(user.getId());
        } else if ("backlog".equalsIgnoreCase(taskType)) {
            tasks = taskRepository.findVisibleBacklogTasks(user.getId());
        } else {
            tasks = taskRepository.findVisibleTasksWithFilter(user.getId(), searchTerm, taskStatus);
            return tasks.stream().map(this::toResponse).collect(Collectors.toList());
        }

        // Apply search + status filter for personal/backlog (in-memory, lists are small)
        if (searchTerm != null) {
            final String lower = searchTerm.toLowerCase();
            tasks = tasks.stream().filter(t -> t.getTitle() != null && t.getTitle().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }
        if (taskStatus != null) {
            final TaskStatus fs = taskStatus;
            tasks = tasks.stream().filter(t -> t.getStatus() == fs).collect(Collectors.toList());
        }
        return tasks.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> listAssignedToMe(Authentication auth) {
        User user = resolveUser(auth);
        return taskRepository.findAssignedToUser(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> listPendingAssignments(Authentication auth) {
        User user = resolveUser(auth);
        // Tasks assigned to me that are still PENDING
        List<TaskAssignment> pending = assignmentRepository.findByAssignee_IdAndStatus(user.getId(), AssignmentStatus.PENDING);
        return pending.stream().map(a -> toResponse(a.getTask())).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> listPendingApprovals(Authentication auth) {
        User user = resolveUser(auth);
        // Assignments I created that need my approval
        List<TaskAssignment> pending = assignmentRepository.findByAssigner_IdAndApprovalStatus(user.getId(), ApprovalStatus.PENDING);
        return pending.stream().map(a -> toResponse(a.getTask())).collect(Collectors.toList());
    }

    @Override
    public TodaySummaryResponse getTodaySummary(Authentication auth) {
        User user = resolveUser(auth);
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        List<Task> todayTasks = taskRepository.findTodayTasks(user.getId(), startOfDay, endOfDay,
                List.of(TaskStatus.CANCELLED, TaskStatus.DONE));
        List<TaskResponse> responses = todayTasks.stream().map(this::toResponse).collect(Collectors.toList());
        long todo = taskRepository.countByCreatedBy_IdAndStatus(user.getId(), TaskStatus.TODO);
        long doing = taskRepository.countByCreatedBy_IdAndStatus(user.getId(), TaskStatus.DOING);
        long waiting = taskRepository.countByCreatedBy_IdAndStatus(user.getId(), TaskStatus.WAITING_REVIEW);
        long done = taskRepository.countByCreatedBy_IdAndStatus(user.getId(), TaskStatus.DONE);
        long overdue = taskRepository.findByCreatedBy_Id(user.getId()).stream()
                .filter(t -> t.getDueAt() != null && t.getDueAt().isBefore(LocalDateTime.now())
                        && !List.of(TaskStatus.DONE, TaskStatus.CANCELLED).contains(t.getStatus()))
                .count();
        return TodaySummaryResponse.builder()
                .totalTasks((int)(todo + doing + waiting))
                .todoCount((int) todo).doingCount((int) doing)
                .waitingReviewCount((int) waiting).doneCount((int) done)
                .overdueCount((int) overdue).todayTasks(responses).build();
    }

    // ── Update / Status ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public TaskResponse updateTask(String taskId, TaskRequest request, Authentication auth) {
        User user = resolveUser(auth);
        Task task = findTask(taskId);
        String old = task.getStatus().name();
        if (request.getTitle() != null) task.setTitle(request.getTitle());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getGoal() != null) task.setGoal(request.getGoal());
        if (request.getExpectedResult() != null) task.setExpectedResult(request.getExpectedResult());
        if (request.getAssignmentBrief() != null) task.setAssignmentBrief(request.getAssignmentBrief());
        if (request.getAssignmentHow() != null) task.setAssignmentHow(request.getAssignmentHow());
        if (request.getPriority() != null) {
            try { task.setPriority(TaskPriority.valueOf(request.getPriority().toUpperCase())); }
            catch (IllegalArgumentException ignored) {}
        }
        if (request.getStatus() != null) {
            try {
                TaskStatus ns = TaskStatus.valueOf(request.getStatus().toUpperCase());
                if (!task.getStatus().equals(ns)) {
                    task.setStatus(ns);
                    saveHistory(task, old, ns.name(), user);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        String dueStr = request.getDue_at() != null ? request.getDue_at() : request.getDueAt();
        if (dueStr != null) task.setDueAt(parseDueAt(dueStr));

        if (request.getSprintId() != null) {
            if (request.getSprintId().isBlank() || "backlog".equalsIgnoreCase(request.getSprintId())) {
                task.setSprint(null);
            } else {
                sprintRepository.findById(request.getSprintId()).ifPresent(task::setSprint);
            }
        }
        if (request.getParentTaskId() != null) {
            if (request.getParentTaskId().isBlank()) {
                task.setParentTask(null);
            } else {
                taskRepository.findById(request.getParentTaskId()).ifPresent(task::setParentTask);
            }
        }

        taskRepository.save(task);
        return toResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse changeStatus(String taskId, String newStatusStr, Authentication auth) {
        User user = resolveUser(auth);
        Task task = findTask(taskId);
        TaskStatus newStatus;
        try { newStatus = TaskStatus.valueOf(newStatusStr.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new CustomException(ResponseCode.VALIDATION_FAILED, "Invalid status: " + newStatusStr); }
        String old = task.getStatus().name();
        task.setStatus(newStatus);
        taskRepository.save(task);
        saveHistory(task, old, newStatus.name(), user);

        // Notify creator + assignees about status change (with email)
        String statusMsg = "Nhiệm vụ \"" + task.getTitle() + "\" đã chuyển trạng thái từ "
                + old.replace("_", " ") + " → " + newStatus.name().replace("_", " ");
        if (!task.getCreatedBy().getId().equals(user.getId())) {
            notifyWithEmail(task.getCreatedBy().getId(), "Cập nhật trạng thái nhiệm vụ", statusMsg);
        }
        assignmentRepository.findByTask_Id(task.getId()).forEach(a -> {
            if (a.getAssignee() != null && !a.getAssignee().getId().equals(user.getId())) {
                notifyWithEmail(a.getAssignee().getId(), "Cập nhật trạng thái nhiệm vụ", statusMsg);
            }
        });

        return toResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse submitForReview(String taskId, SubmitTaskRequest request, Authentication auth) {
        User user = resolveUser(auth);
        Task task = findTask(taskId);
        if (task.getStatus() != TaskStatus.DOING && task.getStatus() != TaskStatus.REWORK) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Task must be DOING or REWORK to submit for review");
        }
        String old = task.getStatus().name();
        task.setStatus(TaskStatus.WAITING_REVIEW);
        task.setResultNote(request.getResultNote());
        task.setSubmittedAt(LocalDateTime.now());
        taskRepository.save(task);
        saveHistory(task, old, TaskStatus.WAITING_REVIEW.name(), user);
        // Notify reviewer
        if (task.getReviewer() != null) {
            notify(task.getReviewer().getId(), "Yêu cầu review nhiệm vụ",
                    fullName(user) + " đã nộp kết quả nhiệm vụ \"" + task.getTitle() + "\". Vui lòng kiểm tra.");
        }
        // Notify task creator
        if (!task.getCreatedBy().getId().equals(user.getId())) {
            notify(task.getCreatedBy().getId(), "Nhiệm vụ đã được nộp",
                    fullName(user) + " đã nộp kết quả cho \"" + task.getTitle() + "\".");
        }
        return toResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse reviewTask(String taskId, ReviewTaskRequest request, Authentication auth) {
        User reviewer = resolveUser(auth);
        Task task = findTask(taskId);
        if (task.getStatus() != TaskStatus.WAITING_REVIEW) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Task must be WAITING_REVIEW to review");
        }
        ReviewStatus decision;
        try { decision = ReviewStatus.valueOf(request.getDecision().toUpperCase()); }
        catch (Exception e) { throw new CustomException(ResponseCode.VALIDATION_FAILED, "decision must be APPROVED or REJECTED"); }

        task.setReviewDecision(decision);
        task.setReviewComment(request.getComment());
        task.setReviewedAt(LocalDateTime.now());

        String old = task.getStatus().name();
        if (decision == ReviewStatus.APPROVED) {
            task.setStatus(TaskStatus.DONE);
            saveHistory(task, old, TaskStatus.DONE.name(), reviewer);
        } else {
            task.setStatus(TaskStatus.REWORK);
            saveHistory(task, old, TaskStatus.REWORK.name(), reviewer);
        }
        taskRepository.save(task);
        // Notify assignees about review result
        String reviewMsg = decision == ReviewStatus.APPROVED
                ? "✅ Nhiệm vụ \"" + task.getTitle() + "\" đã được DUYỆT."
                : "🔄 Nhiệm vụ \"" + task.getTitle() + "\" cần CHỈNH SỬA: " + (request.getComment() != null ? request.getComment() : "");
        assignmentRepository.findByTask_Id(task.getId()).forEach(a -> {
            if (a.getAssignee() != null) notify(a.getAssignee().getId(), "Kết quả review nhiệm vụ", reviewMsg);
        });
        // Notify creator if not reviewer
        if (!task.getCreatedBy().getId().equals(reviewer.getId())) {
            notify(task.getCreatedBy().getId(), "Review hoàn thành",
                    fullName(reviewer) + " đã " + (decision == ReviewStatus.APPROVED ? "DUYỆT" : "yêu cầu chỉnh sửa") + " nhiệm vụ \"" + task.getTitle() + "\".");
        }
        return toResponse(task);
    }

    @Override
    @Transactional
    public void cancelTask(String taskId, Authentication auth) {
        User user = resolveUser(auth);
        Task task = findTask(taskId);
        String old = task.getStatus().name();
        task.setStatus(TaskStatus.CANCELLED);
        taskRepository.save(task);
        saveHistory(task, old, TaskStatus.CANCELLED.name(), user);
    }

    @Override
    @Transactional
    public void deleteTask(String taskId, Authentication auth) {
        deleteTaskCascade(taskId);
    }

    private void deleteTaskCascade(String taskId) {
        // Delete subtasks first (recursive)
        taskRepository.findByParentTask_IdOrderByCreatedAtDesc(taskId)
                .forEach(sub -> deleteTaskCascade(sub.getId()));
        // Delete related records
        assignmentRepository.deleteAll(assignmentRepository.findByTask_Id(taskId));
        supporterRepository.deleteAll(supporterRepository.findByTask_Id(taskId));
        historyRepository.deleteAll(historyRepository.findByTask_IdOrderByChangedAtAsc(taskId));
        // Null out parent refs first to avoid self-referencing FK violation, then delete
        commentRepository.clearParentsByTaskId(taskId);
        commentRepository.deleteByTaskId(taskId);
        taskRepository.deleteById(taskId);
    }

    // ── Assignment workflow ───────────────────────────────────────────────────

    @Override
    @Transactional
    public TaskResponse assignTask(String taskId, AssignTaskRequest request, Authentication auth) {
        User assigner = resolveUser(auth);
        Task task = findTask(taskId);
        String assigneeId = request.getAssignee_id() != null ? request.getAssignee_id() : request.getAssigneeId();
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

        String assignerIdReq = request.getAssigner_id() != null ? request.getAssigner_id() : request.getAssignerId();
        User assignerEntity = assignerIdReq != null
                ? userRepository.findById(assignerIdReq).orElse(assigner) : assigner;

        // If assigning as primary, remove existing primary assignments first (reassign)
        if (request.isPrimary()) {
            assignmentRepository.findByTask_Id(taskId).stream()
                    .filter(TaskAssignment::isPrimary)
                    .forEach(assignmentRepository::delete);
            assignmentRepository.flush();
        }

        TaskAssignment a = new TaskAssignment();
        a.setTask(task);
        a.setAssignee(assignee);
        a.setAssigner(assignerEntity);
        a.setBrief(request.getBrief());
        a.setHow(request.getHow());
        a.setPrimary(request.isPrimary());
        a.setApprovalStatus(request.isRequireApproval() ? ApprovalStatus.PENDING : ApprovalStatus.NOT_REQUIRED);
        // Auto-accept: no need for the assignee to manually accept
        a.setStatus(AssignmentStatus.ACCEPTED);
        a.setRespondedAt(LocalDateTime.now());
        assignmentRepository.save(a);

        task.setAssignedBy(assignerEntity);
        taskRepository.save(task);
        // Notify assignee (with email)
        notifyWithEmail(assignee.getId(), "Bạn được giao nhiệm vụ",
                fullName(assignerEntity) + " đã giao nhiệm vụ \"" + task.getTitle() + "\" cho bạn.");
        // Notify task creator if different from assigner (with email)
        if (!task.getCreatedBy().getId().equals(assignerEntity.getId())) {
            notifyWithEmail(task.getCreatedBy().getId(), "Nhiệm vụ đã được giao",
                    fullName(assignerEntity) + " đã giao \"" + task.getTitle() + "\" cho " + fullName(assignee));
        }
        return toResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse respondToAssignment(String taskId, String assignmentId, String response, Authentication auth) {
        User user = resolveUser(auth);
        TaskAssignment a = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Assignment not found"));
        if (!a.getAssignee().getId().equals(user.getId()))
            throw new CustomException(ResponseCode.PERMISSION_DENIED);
        if (a.getStatus() != AssignmentStatus.PENDING)
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Assignment is not pending");
        if ("ACCEPT".equalsIgnoreCase(response)) {
            a.setStatus(AssignmentStatus.ACCEPTED);
        } else {
            a.setStatus(AssignmentStatus.REJECTED);
        }
        a.setRespondedAt(LocalDateTime.now());
        assignmentRepository.save(a);
        return toResponse(a.getTask());
    }

    @Override
    @Transactional
    public TaskResponse approveAssignment(String taskId, String assignmentId, Authentication auth) {
        TaskAssignment a = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Assignment not found"));
        a.setApprovalStatus(ApprovalStatus.APPROVED);
        assignmentRepository.save(a);
        return toResponse(a.getTask());
    }

    // ── Reviewer ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public TaskResponse inviteReviewer(String taskId, String reviewerUserId, Authentication auth) {
        Task task = findTask(taskId);
        User reviewer = userRepository.findById(reviewerUserId)
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
        task.setReviewer(reviewer);
        task.setReviewerStatus(ReviewerStatus.PENDING);
        taskRepository.save(task);
        notify(reviewer.getId(), "Lời mời làm reviewer",
                "Bạn được mời làm reviewer cho nhiệm vụ \"" + task.getTitle() + "\".");
        return toResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse respondReviewerInvite(String taskId, String response, Authentication auth) {
        User user = resolveUser(auth);
        Task task = findTask(taskId);
        if (task.getReviewer() == null || !task.getReviewer().getId().equals(user.getId()))
            throw new CustomException(ResponseCode.PERMISSION_DENIED);
        if ("ACCEPT".equalsIgnoreCase(response)) {
            task.setReviewerStatus(ReviewerStatus.ACCEPTED);
        } else {
            task.setReviewerStatus(ReviewerStatus.REJECTED);
            task.setReviewer(null);
        }
        taskRepository.save(task);
        return toResponse(task);
    }

    // ── Supporter ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public TaskResponse addSupporter(String taskId, String supporterUserId, Authentication auth) {
        User inviter = resolveUser(auth);
        Task task = findTask(taskId);
        User supporter = userRepository.findById(supporterUserId)
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
        // Check if already exists
        if (supporterRepository.findByTask_IdAndUser_Id(taskId, supporterUserId).isPresent())
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "User is already a supporter");
        TaskSupporter ts = new TaskSupporter();
        ts.setTask(task);
        ts.setUser(supporter);
        ts.setInvitedBy(inviter);
        supporterRepository.save(ts);
        return toResponse(task);
    }

    @Override
    @Transactional
    public TaskResponse respondSupporterInvite(String taskId, String supporterId, String response, Authentication auth) {
        User user = resolveUser(auth);
        TaskSupporter ts = supporterRepository.findById(supporterId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Supporter invite not found"));
        if (!ts.getUser().getId().equals(user.getId()))
            throw new CustomException(ResponseCode.PERMISSION_DENIED);
        if ("ACCEPT".equalsIgnoreCase(response)) {
            ts.setStatus(SupporterStatus.ACCEPTED);
        } else {
            ts.setStatus(SupporterStatus.REJECTED);
        }
        ts.setRespondedAt(LocalDateTime.now());
        supporterRepository.save(ts);
        return toResponse(ts.getTask());
    }

    @Override
    @Transactional
    public com.finalProject.BookingMeetingRoom.model.response.CommentResponse addComment(String taskId, String content, String parentId, Authentication auth) {
        User user = resolveUser(auth);
        Task task = findTask(taskId);

        TaskComment comment = new TaskComment();
        comment.setTask(task);
        comment.setAuthor(user);
        comment.setContent(content);

        if (parentId != null && !parentId.isBlank()) {
            commentRepository.findById(parentId).ifPresent(comment::setParent);
        }

        commentRepository.save(comment);
        return mapComment(comment);
    }

    @Override
    public List<com.finalProject.BookingMeetingRoom.model.response.CommentResponse> getComments(String taskId, Authentication auth) {
        resolveUser(auth);
        List<TaskComment> comments = commentRepository.findByTask_IdAndParentIsNullOrderByCreatedAtAsc(taskId);
        return comments.stream().map(this::mapComment).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteComment(String commentId, Authentication auth) {
        User user = resolveUser(auth);
        TaskComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Comment not found"));
        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new CustomException(ResponseCode.PERMISSION_DENIED);
        }
        // First delete children
        List<TaskComment> children = commentRepository.findByParent_IdOrderByCreatedAtAsc(commentId);
        commentRepository.deleteAll(children);
        commentRepository.delete(comment);
    }

    private com.finalProject.BookingMeetingRoom.model.response.CommentResponse mapComment(TaskComment c) {
        return com.finalProject.BookingMeetingRoom.model.response.CommentResponse.builder()
                .id(c.getId())
                .taskId(c.getTask().getId())
                .authorId(c.getAuthor().getId())
                .authorName(fullName(c.getAuthor()))
                .content(c.getContent())
                .parentId(c.getParent() != null ? c.getParent().getId() : null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
