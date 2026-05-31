package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    @Query("SELECT DISTINCT p FROM Project p LEFT JOIN ProjectMember m ON m.project = p " +
           "WHERE p.createdBy.id = :userId OR m.user.id = :userId " +
           "ORDER BY p.createdAt DESC")
    List<Project> findVisibleProjects(@Param("userId") String userId);
}
