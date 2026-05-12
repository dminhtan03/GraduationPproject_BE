package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.common.enums.TaskStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    Page<Task> findByCreatedBy_IdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<Task> findByCreatedBy_IdOrderByCreatedAtDesc(String userId);

    List<Task> findByCreatedBy_Id(String userId);

    // Tasks assigned to the user
    @Query("SELECT t FROM Task t JOIN TaskAssignment a ON a.task.id = t.id WHERE a.assignee.id = :userId AND a.status != 'CANCELLED' ORDER BY t.createdAt DESC")
    List<Task> findAssignedToUser(@Param("userId") String userId);

    // Personal tasks created by the user (not from meeting)
    List<Task> findByCreatedBy_IdAndMeetingIsNullOrderByCreatedAtDesc(String userId);

    // Tasks due today for the user
    @Query("SELECT t FROM Task t WHERE t.createdBy.id = :userId AND t.dueAt >= :startOfDay AND t.dueAt < :endOfDay AND t.status NOT IN :excludedStatuses")
    List<Task> findTodayTasks(@Param("userId") String userId,
                               @Param("startOfDay") LocalDateTime startOfDay,
                               @Param("endOfDay") LocalDateTime endOfDay,
                               @Param("excludedStatuses") List<TaskStatus> excludedStatuses);

    long countByCreatedBy_IdAndStatus(String userId, TaskStatus status);

    List<Task> findByMeeting_Id(String meetingId);
}
