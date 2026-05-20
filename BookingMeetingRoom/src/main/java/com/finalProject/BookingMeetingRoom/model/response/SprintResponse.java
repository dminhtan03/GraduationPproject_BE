package com.finalProject.BookingMeetingRoom.model.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SprintResponse {
    private String id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdById;
    private String createdByName;
    private List<TaskResponse> tasks;
}
