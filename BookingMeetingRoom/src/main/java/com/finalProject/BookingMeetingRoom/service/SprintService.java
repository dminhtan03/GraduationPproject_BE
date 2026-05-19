package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.request.SprintRequest;
import com.finalProject.BookingMeetingRoom.model.response.SprintResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface SprintService {
    SprintResponse createSprint(SprintRequest request, Authentication auth);
    List<SprintResponse> listSprints(Authentication auth);
    SprintResponse getSprint(String sprintId, Authentication auth);
    SprintResponse updateSprint(String sprintId, SprintRequest request, Authentication auth);
    SprintResponse endSprint(String sprintId, String targetSprintId, Authentication auth);
    void deleteSprint(String sprintId, Authentication auth);
}
