package com.finalProject.BookingMeetingRoom.model.request;

import lombok.Data;
import java.util.List;

@Data
public class ProjectRequest {
    private String name;
    private String description;
    private String goal;
    private String startDate;   // "YYYY-MM-DD"
    private String endDate;     // "YYYY-MM-DD"
    private String status;
    private List<String> memberIds;
}
