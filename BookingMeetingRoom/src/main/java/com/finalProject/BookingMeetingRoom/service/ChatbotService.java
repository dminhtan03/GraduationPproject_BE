package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.request.ChatbotMessageRequest;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotMessageResponse;
import org.springframework.security.core.Authentication;

public interface ChatbotService {

    ChatbotMessageResponse handleMessage(ChatbotMessageRequest request, Authentication authentication);
}