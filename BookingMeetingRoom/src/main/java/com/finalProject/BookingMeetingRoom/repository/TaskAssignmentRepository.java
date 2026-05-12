package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.common.enums.ApprovalStatus;
import com.finalProject.BookingMeetingRoom.common.enums.AssignmentStatus;
import com.finalProject.BookingMeetingRoom.model.entity.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, String> {
    List<TaskAssignment> findByTask_Id(String taskId);
    Optional<TaskAssignment> findByTask_IdAndAssignee_Id(String taskId, String assigneeId);
    List<TaskAssignment> findByAssignee_Id(String assigneeId);
    List<TaskAssignment> findByAssignee_IdAndStatus(String assigneeId, AssignmentStatus status);
    List<TaskAssignment> findByAssigner_IdAndApprovalStatus(String assignerId, ApprovalStatus approvalStatus);
}
