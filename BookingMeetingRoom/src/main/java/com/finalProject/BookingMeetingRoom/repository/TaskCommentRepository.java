package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, String> {
    List<TaskComment> findByTask_IdAndParentIsNullOrderByCreatedAtAsc(String taskId);
    List<TaskComment> findByParent_IdOrderByCreatedAtAsc(String parentId);
    List<TaskComment> findByTask_Id(String taskId);

    @Modifying
    @Query("UPDATE TaskComment c SET c.parent = null WHERE c.task.id = :taskId")
    void clearParentsByTaskId(@Param("taskId") String taskId);

    @Modifying
    @Query("DELETE FROM TaskComment c WHERE c.task.id = :taskId")
    void deleteByTaskId(@Param("taskId") String taskId);
}
