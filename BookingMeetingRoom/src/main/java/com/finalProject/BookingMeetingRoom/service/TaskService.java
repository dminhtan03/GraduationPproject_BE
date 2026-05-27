package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.request.AssignTaskRequest;
import com.finalProject.BookingMeetingRoom.model.request.ReviewTaskRequest;
import com.finalProject.BookingMeetingRoom.model.request.SubmitTaskRequest;
import com.finalProject.BookingMeetingRoom.model.request.TaskRequest;
import com.finalProject.BookingMeetingRoom.model.response.TaskResponse;
import com.finalProject.BookingMeetingRoom.model.response.TodaySummaryResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface TaskService {
    TaskResponse createTask(TaskRequest request, Authentication auth);
    TaskResponse getTask(String taskId, Authentication auth);
    TaskResponse updateTask(String taskId, TaskRequest request, Authentication auth);
    void cancelTask(String taskId, Authentication auth);
    void deleteTask(String taskId, Authentication auth);
    List<TaskResponse> listMyTasks(String taskType, String search, String status, Authentication auth);
    List<TaskResponse> listAssignedToMe(Authentication auth);
    List<TaskResponse> listPendingAssignments(Authentication auth);
    List<TaskResponse> listPendingApprovals(Authentication auth);
    TodaySummaryResponse getTodaySummary(Authentication auth);
    // Assignment
    TaskResponse assignTask(String taskId, AssignTaskRequest request, Authentication auth);
    TaskResponse respondToAssignment(String taskId, String assignmentId, String response, Authentication auth);
    TaskResponse approveAssignment(String taskId, String assignmentId, Authentication auth);
    // Status
    TaskResponse changeStatus(String taskId, String newStatus, Authentication auth);
    TaskResponse submitForReview(String taskId, SubmitTaskRequest request, Authentication auth);
    TaskResponse reviewTask(String taskId, ReviewTaskRequest request, Authentication auth);
    // Reviewer
    TaskResponse inviteReviewer(String taskId, String reviewerUserId, Authentication auth);
    TaskResponse respondReviewerInvite(String taskId, String response, Authentication auth);
    // Supporter
    TaskResponse addSupporter(String taskId, String supporterUserId, Authentication auth);
    TaskResponse respondSupporterInvite(String taskId, String supporterId, String response, Authentication auth);

    // Comments
    com.finalProject.BookingMeetingRoom.model.response.CommentResponse addComment(String taskId, String content, String parentId, Authentication auth);
    List<com.finalProject.BookingMeetingRoom.model.response.CommentResponse> getComments(String taskId, Authentication auth);
    void deleteComment(String commentId, Authentication auth);
}
