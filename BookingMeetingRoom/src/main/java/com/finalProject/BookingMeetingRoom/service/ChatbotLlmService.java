package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.common.utils.ChatbotMessageParser;

import java.util.List;
import java.util.Optional;

public interface ChatbotLlmService {

    Optional<ChatbotMessageParser.ParseResult> parse(String message, List<String> recentUserMessages);
}
