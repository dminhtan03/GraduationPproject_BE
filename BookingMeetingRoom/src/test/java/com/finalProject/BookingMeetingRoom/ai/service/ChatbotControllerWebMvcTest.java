package com.finalProject.BookingMeetingRoom.ai.service;

import com.finalProject.BookingMeetingRoom.controller.ai.ChatbotController;
import com.finalProject.BookingMeetingRoom.model.request.ChatbotMessageRequest;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotMessageResponse;
import com.finalProject.BookingMeetingRoom.service.ChatbotService;
import com.finalProject.BookingMeetingRoom.service.SpeechToTextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatbotController.class)
class ChatbotControllerWebMvcTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    ChatbotService chatbotService;

    @MockBean
    SpeechToTextService speechToTextService;

    @Test
    @WithMockUser(username = "user@example.com")
    void messageEndpointShouldReturn200() throws Exception {
        when(chatbotService.handleMessage(any(), any()))
                .thenReturn(ChatbotMessageResponse.builder().reply("ok").build());

        ChatbotMessageRequest req = ChatbotMessageRequest.builder().message("Today available rooms?").build();

        mockMvc.perform(post("/api/v1/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void voiceEndpointWithTranscriptShouldReturn200() throws Exception {
        when(chatbotService.handleMessage(any(), any()))
                .thenReturn(ChatbotMessageResponse.builder().reply("ok").build());

        mockMvc.perform(multipart("/api/v1/chatbot/voice")
                        .param("transcript", "Book AL-102 at 10AM today"))
                .andExpect(status().isOk());
    }
}
