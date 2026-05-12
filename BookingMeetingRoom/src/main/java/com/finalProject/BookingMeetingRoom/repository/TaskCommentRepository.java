package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, String> {
    List<TaskComment> findByTask_IdAndParentIsNullOrderByCreatedAtAsc(String taskId);
    List<TaskComment> findByParent_IdOrderByCreatedAtAsc(String parentId);
}
