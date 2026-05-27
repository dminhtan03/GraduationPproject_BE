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

    List<Task> findBySprint_IdOrderByCreatedAtDesc(String sprintId);

    List<Task> findByParentTask_IdOrderByCreatedAtDesc(String parentTaskId);

    @Query("SELECT DISTINCT t FROM Task t LEFT JOIN TaskAssignment a ON a.task = t LEFT JOIN TaskSupporter s ON s.task = t " +
           "WHERE t.sprint IS NULL AND t.parentTask IS NULL " +
           "AND (t.createdBy.id = :userId OR t.reviewer.id = :userId OR a.assignee.id = :userId OR s.user.id = :userId) " +
           "ORDER BY t.createdAt DESC")
    List<Task> findVisibleBacklogTasks(@Param("userId") String userId);

    @Query("SELECT DISTINCT t FROM Task t LEFT JOIN TaskAssignment a ON a.task = t LEFT JOIN TaskSupporter s ON s.task = t " +
           "WHERE t.sprint.id = :sprintId " +
           "AND (t.createdBy.id = :userId OR t.reviewer.id = :userId OR a.assignee.id = :userId OR s.user.id = :userId) " +
           "ORDER BY t.createdAt DESC")
    List<Task> findVisibleTasksBySprint(@Param("sprintId") String sprintId, @Param("userId") String userId);

    @Query("SELECT DISTINCT t FROM Task t LEFT JOIN TaskAssignment a ON a.task = t LEFT JOIN TaskSupporter s ON s.task = t " +
           "WHERE (t.createdBy.id = :userId OR t.reviewer.id = :userId OR a.assignee.id = :userId OR s.user.id = :userId) " +
           "ORDER BY t.createdAt DESC")
    List<Task> findVisibleTasks(@Param("userId") String userId);

    List<Task> findBySprintIsNullAndParentTaskIsNullOrderByCreatedAtDesc();

    List<Task> findBySprint_IdAndParentTaskIsNullOrderByCreatedAtDesc(String sprintId);

    @Query("SELECT DISTINCT t FROM Task t LEFT JOIN TaskAssignment a ON a.task = t LEFT JOIN TaskSupporter s ON s.task = t " +
           "WHERE (t.createdBy.id = :userId OR t.reviewer.id = :userId OR a.assignee.id = :userId OR s.user.id = :userId) " +
           "AND (:search IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "ORDER BY t.createdAt DESC")
    List<Task> findVisibleTasksWithFilter(@Param("userId") String userId,
                                          @Param("search") String search,
                                          @Param("status") TaskStatus status);
}
