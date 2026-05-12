package com.finalProject.BookingMeetingRoom.service.chatbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiChatbotLlmServiceTest {

    @Test
    void parse_shouldReturnEmpty_whenLlmDisabled() {
        OpenAiChatbotLlmService service = new OpenAiChatbotLlmService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "llmEnabled", false);

        assertTrue(service.parse("hello", List.of()).isEmpty());
    }

    @Test
    void parse_shouldReturnEmpty_whenApiKeyBlank() {
        OpenAiChatbotLlmService service = new OpenAiChatbotLlmService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "llmEnabled", true);
        ReflectionTestUtils.setField(service, "apiKey", "   ");

        assertTrue(service.parse("hello", List.of()).isEmpty());
    }
}
