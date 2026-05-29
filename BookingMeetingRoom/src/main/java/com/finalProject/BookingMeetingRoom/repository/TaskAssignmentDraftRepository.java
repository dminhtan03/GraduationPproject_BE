package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.common.enums.ReviewStatus;
import com.finalProject.BookingMeetingRoom.model.entity.TaskAssignmentDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskAssignmentDraftRepository extends JpaRepository<TaskAssignmentDraft, String> {
    List<TaskAssignmentDraft> findByMeeting_IdOrderByCreatedAtAsc(String meetingId);
    List<TaskAssignmentDraft> findByMeeting_IdAndReviewStatus(String meetingId, ReviewStatus status);

    @Modifying
    @Query("UPDATE TaskAssignmentDraft d SET d.createdTask = null WHERE d.createdTask.id = :taskId")
    void clearCreatedTaskByTaskId(@Param("taskId") String taskId);
}
