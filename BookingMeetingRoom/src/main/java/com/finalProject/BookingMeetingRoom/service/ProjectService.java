package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.request.ProjectRequest;
import com.finalProject.BookingMeetingRoom.model.response.ProjectResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ProjectService {
    ProjectResponse createProject(ProjectRequest request, Authentication auth);
    List<ProjectResponse> listMyProjects(Authentication auth);
    ProjectResponse getProject(String projectId, Authentication auth);
    ProjectResponse updateProject(String projectId, ProjectRequest request, Authentication auth);
    void deleteProject(String projectId, Authentication auth);
    ProjectResponse addMember(String projectId, String userId, Authentication auth);
    void removeMember(String projectId, String userId, Authentication auth);
    int repairProjectTasks(String projectId, Authentication auth);
}
