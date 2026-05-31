package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, String> {

    List<ProjectMember> findByProject_IdOrderByJoinedAtAsc(String projectId);

    Optional<ProjectMember> findByProject_IdAndUser_Id(String projectId, String userId);

    @Modifying
    @Query("DELETE FROM ProjectMember m WHERE m.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") String projectId);
}
