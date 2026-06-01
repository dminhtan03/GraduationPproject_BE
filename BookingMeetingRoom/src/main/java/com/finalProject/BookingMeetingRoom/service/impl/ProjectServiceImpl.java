package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.ProjectMemberRole;
import com.finalProject.BookingMeetingRoom.common.enums.ProjectStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Project;
import com.finalProject.BookingMeetingRoom.model.entity.ProjectMember;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.request.ProjectRequest;
import com.finalProject.BookingMeetingRoom.model.response.ProjectResponse;
import com.finalProject.BookingMeetingRoom.repository.ProjectMemberRepository;
import com.finalProject.BookingMeetingRoom.repository.ProjectRepository;
import com.finalProject.BookingMeetingRoom.repository.SprintRepository;
import com.finalProject.BookingMeetingRoom.repository.TaskRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;

    private User resolveUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
    }

    private String fullName(User u) {
        if (u == null) return null;
        if (u.getUserInfo() == null) return u.getUsername();
        return ((u.getUserInfo().getFirstName() != null ? u.getUserInfo().getFirstName() : "") +
                " " + (u.getUserInfo().getLastName() != null ? u.getUserInfo().getLastName() : "")).trim();
    }

    private ProjectResponse toResponse(Project p) {
        List<ProjectMember> members = memberRepository.findByProject_IdOrderByJoinedAtAsc(p.getId());
        List<ProjectResponse.MemberInfo> memberInfos = members.stream()
                .map(m -> ProjectResponse.MemberInfo.builder()
                        .userId(m.getUser().getId())
                        .userName(fullName(m.getUser()))
                        .userEmail(m.getUser().getUserInfo().getEmail())
                        .role(m.getRole().name())
                        .build())
                .collect(Collectors.toList());

        return ProjectResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .goal(p.getGoal())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .status(p.getStatus().name())
                .createdById(p.getCreatedBy().getId())
                .createdByName(fullName(p.getCreatedBy()))
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .members(memberInfos)
                .build();
    }

    @Override
    @Transactional
    public ProjectResponse createProject(ProjectRequest request, Authentication auth) {
        User creator = resolveUser(auth);

        Project p = new Project();
        p.setName(request.getName());
        p.setDescription(request.getDescription());
        p.setGoal(request.getGoal());
        p.setCreatedBy(creator);
        if (request.getStartDate() != null && !request.getStartDate().isBlank())
            p.setStartDate(LocalDate.parse(request.getStartDate()));
        if (request.getEndDate() != null && !request.getEndDate().isBlank())
            p.setEndDate(LocalDate.parse(request.getEndDate()));
        projectRepository.save(p);

        // Add creator as OWNER
        ProjectMember ownerMember = new ProjectMember();
        ownerMember.setProject(p);
        ownerMember.setUser(creator);
        ownerMember.setRole(ProjectMemberRole.OWNER);
        memberRepository.save(ownerMember);

        // Add additional members
        if (request.getMemberIds() != null) {
            for (String memberId : request.getMemberIds()) {
                if (memberId.equals(creator.getId())) continue;
                userRepository.findById(memberId).ifPresent(user -> {
                    ProjectMember m = new ProjectMember();
                    m.setProject(p);
                    m.setUser(user);
                    m.setRole(ProjectMemberRole.MEMBER);
                    memberRepository.save(m);
                });
            }
        }

        return toResponse(p);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> listMyProjects(Authentication auth) {
        User user = resolveUser(auth);
        return projectRepository.findVisibleProjects(user.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse getProject(String projectId, Authentication auth) {
        resolveUser(auth);
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Project not found"));
        return toResponse(p);
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(String projectId, ProjectRequest request, Authentication auth) {
        resolveUser(auth);
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Project not found"));
        if (request.getName() != null) p.setName(request.getName());
        if (request.getDescription() != null) p.setDescription(request.getDescription());
        if (request.getGoal() != null) p.setGoal(request.getGoal());
        if (request.getStartDate() != null && !request.getStartDate().isBlank())
            p.setStartDate(LocalDate.parse(request.getStartDate()));
        if (request.getEndDate() != null && !request.getEndDate().isBlank())
            p.setEndDate(LocalDate.parse(request.getEndDate()));
        if (request.getStatus() != null) {
            try { p.setStatus(ProjectStatus.valueOf(request.getStatus().toUpperCase())); }
            catch (IllegalArgumentException ignored) {}
        }
        projectRepository.save(p);
        return toResponse(p);
    }

    @Override
    @Transactional
    public void deleteProject(String projectId, Authentication auth) {
        User caller = resolveUser(auth);
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Project not found"));
        if (!p.getCreatedBy().getId().equals(caller.getId()))
            throw new CustomException(ResponseCode.TASK_FORBIDDEN);
        // Detach sprints from project (don't delete sprints, just unlink)
        sprintRepository.findByProject_IdOrderByCreatedAtDesc(projectId).forEach(s -> {
            s.setProject(null);
            sprintRepository.save(s);
        });
        memberRepository.deleteByProjectId(projectId);
        projectRepository.delete(p);
    }

    @Override
    @Transactional
    public ProjectResponse addMember(String projectId, String userId, Authentication auth) {
        resolveUser(auth);
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Project not found"));
        if (memberRepository.findByProject_IdAndUser_Id(projectId, userId).isPresent())
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "User is already a member");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
        ProjectMember m = new ProjectMember();
        m.setProject(p);
        m.setUser(user);
        m.setRole(ProjectMemberRole.MEMBER);
        memberRepository.save(m);
        return toResponse(p);
    }

    @Override
    @Transactional
    public void removeMember(String projectId, String userId, Authentication auth) {
        resolveUser(auth);
        ProjectMember m = memberRepository.findByProject_IdAndUser_Id(projectId, userId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Member not found"));
        if (m.getRole() == ProjectMemberRole.OWNER)
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Cannot remove project owner");
        memberRepository.delete(m);
    }

    @Override
    @Transactional
    public int repairProjectTasks(String projectId, Authentication auth) {
        resolveUser(auth);
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Project not found"));
        int count = 0;
        // Fix tasks in this project's sprints that have wrong or null PROJECT_ID
        for (com.finalProject.BookingMeetingRoom.model.entity.Sprint sprint :
                sprintRepository.findByProject_IdOrderByCreatedAtDesc(projectId)) {
            for (com.finalProject.BookingMeetingRoom.model.entity.Task task :
                    taskRepository.findBySprint_IdOrderByCreatedAtDesc(sprint.getId())) {
                if (task.getProject() == null || !task.getProject().getId().equals(projectId)) {
                    task.setProject(p);
                    taskRepository.save(task);
                    count++;
                }
            }
        }
        return count;
    }
}
