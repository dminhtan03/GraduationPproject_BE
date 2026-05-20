package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, String> {
    List<Sprint> findAllByOrderByCreatedAtDesc();

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s FROM Sprint s " +
           "LEFT JOIN Task t ON t.sprint = s " +
           "LEFT JOIN TaskAssignment a ON a.task = t " +
           "LEFT JOIN TaskSupporter sup ON sup.task = t " +
           "WHERE s.createdBy.id = :userId OR t.createdBy.id = :userId OR t.reviewer.id = :userId OR a.assignee.id = :userId OR sup.user.id = :userId " +
           "ORDER BY s.createdAt DESC")
    List<Sprint> findVisibleSprints(@org.springframework.data.repository.query.Param("userId") String userId);
}
