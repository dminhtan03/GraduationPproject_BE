package com.finalProject.BookingMeetingRoom.service;

import org.springframework.security.core.Authentication;

import com.finalProject.BookingMeetingRoom.model.request.AiChatRequest;
import com.finalProject.BookingMeetingRoom.model.response.AiChatResponse;

public interface AiService {
    AiChatResponse chat(AiChatRequest request, Authentication authentication);
}
