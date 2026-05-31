package com.finalProject.BookingMeetingRoom.model.request;

import lombok.Data;

@Data
public class SprintRequest {
    private String name;
    private String startDate; // LocalDate in format "YYYY-MM-DD"
    private String endDate;   // LocalDate in format "YYYY-MM-DD"
    private String status;    // PLANNED | ACTIVE | COMPLETED
    private String projectId;
}
