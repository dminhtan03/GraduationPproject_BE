package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.common.enums.ReviewStatus;
import com.finalProject.BookingMeetingRoom.model.entity.TaskAssignmentDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskAssignmentDraftRepository extends JpaRepository<TaskAssignmentDraft, String> {
    List<TaskAssignmentDraft> findByMeeting_IdOrderByCreatedAtAsc(String meetingId);
    List<TaskAssignmentDraft> findByMeeting_IdAndReviewStatus(String meetingId, ReviewStatus status);
}
