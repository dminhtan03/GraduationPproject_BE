package com.finalProject.BookingMeetingRoom.service.chatbot;

import com.finalProject.BookingMeetingRoom.common.enums.ChatbotIntent;
import com.finalProject.BookingMeetingRoom.common.utils.ChatbotMessageParser;
import com.finalProject.BookingMeetingRoom.service.impl.ChatbotLlmServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatbotLlmServiceImplTest {

    @Test
    void parse_shouldReturnEmpty_whenDisabled() {
        ChatbotLlmServiceImpl service = new ChatbotLlmServiceImpl();
        ReflectionTestUtils.setField(service, "enabled", false);
        ReflectionTestUtils.setField(service, "apiKey", "key");

        Optional<ChatbotMessageParser.ParseResult> result = service.parse("book room", List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void parse_shouldReturnEmpty_whenApiKeyMissing() {
        ChatbotLlmServiceImpl service = new ChatbotLlmServiceImpl();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "apiKey", "");

        Optional<ChatbotMessageParser.ParseResult> result = service.parse("book room", List.of("hello"));

        assertTrue(result.isEmpty());
    }

    @Test
    void parseJsonResponse_shouldReturnParsedFields() throws Exception {
        ChatbotLlmServiceImpl service = new ChatbotLlmServiceImpl();
        Method method = ChatbotLlmServiceImpl.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        String json = "{\"intent\":\"BOOK_ROOM\",\"roomCode\":\"A-101\",\"date\":\"2026-06-05\",\"startTime\":\"10:00\",\"endTime\":\"11:00\",\"minCapacity\":5}";
        @SuppressWarnings("unchecked")
        Optional<ChatbotMessageParser.ParseResult> result = (Optional<ChatbotMessageParser.ParseResult>) method.invoke(service, json);

        assertTrue(result.isPresent());
        assertEquals(ChatbotIntent.BOOK_ROOM, result.get().intent());
        assertEquals("A-101", result.get().roomCode());
        assertEquals(LocalDate.of(2026, 6, 5), result.get().date());
        assertEquals(LocalTime.of(10, 0), result.get().startTime());
        assertEquals(LocalTime.of(11, 0), result.get().endTime());
        assertEquals(5, result.get().minCapacity());
    }

    @Test
    void parseJsonResponse_shouldReturnEmpty_onInvalidJson() throws Exception {
        ChatbotLlmServiceImpl service = new ChatbotLlmServiceImpl();
        Method method = ChatbotLlmServiceImpl.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Optional<ChatbotMessageParser.ParseResult> result = (Optional<ChatbotMessageParser.ParseResult>) method.invoke(service, "no json");

        assertTrue(result.isEmpty());
    }

    @Test
    void jsonString_shouldEscapeQuotesAndNewlines() throws Exception {
        ChatbotLlmServiceImpl service = new ChatbotLlmServiceImpl();
        Method method = ChatbotLlmServiceImpl.class.getDeclaredMethod("jsonString", String.class);
        method.setAccessible(true);

        String escaped = (String) method.invoke(service, "line1\n\"line2\"");

        assertNotNull(escaped);
        assertTrue(escaped.contains("\\n"));
        assertTrue(escaped.contains("\\\""));
        assertTrue(escaped.startsWith("\""));
        assertTrue(escaped.endsWith("\""));
    }
}
