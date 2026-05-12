package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.common.enums.SupporterStatus;
import com.finalProject.BookingMeetingRoom.model.entity.TaskSupporter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskSupporterRepository extends JpaRepository<TaskSupporter, String> {
    List<TaskSupporter> findByTask_Id(String taskId);
    Optional<TaskSupporter> findByTask_IdAndUser_Id(String taskId, String userId);
    List<TaskSupporter> findByUser_IdAndStatus(String userId, SupporterStatus status);
    List<TaskSupporter> findByTask_IdAndStatus(String taskId, SupporterStatus status);
}
