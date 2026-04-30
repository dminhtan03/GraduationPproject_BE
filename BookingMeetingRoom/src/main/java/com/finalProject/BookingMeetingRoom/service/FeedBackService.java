package com.finalProject.BookingMeetingRoom.service;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

import com.finalProject.BookingMeetingRoom.model.request.FeedbackRequest;
import com.finalProject.BookingMeetingRoom.model.response.FeedbackResponse;
import com.finalProject.BookingMeetingRoom.model.response.AdminFeedbackResponse;

import jakarta.validation.Valid;

public interface FeedBackService {
    
    void createFeedback(@Valid FeedbackRequest request, Authentication connectedUser);

    Page<FeedbackResponse> getFeedbackOfARoom(String roomId, int pageNum, int pageSize);

    Page<AdminFeedbackResponse> getAllFeedbacks(Integer rating, String email, int pageNum, int pageSize);

    AdminFeedbackResponse getFeedbackDetail(String id);
}
