package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProjectResponse {

    private String id;
    private String name;
    private String description;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MemberInfo> members;

    @Data
    @Builder
    public static class MemberInfo {
        private String userId;
        private String userName;
        private String userEmail;
        private String role;
    }
}
