package com.finalProject.BookingMeetingRoom.service.chat;

import com.finalProject.BookingMeetingRoom.service.impl.ChatHistoryServiceImpl;
import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Chat_history;
import com.finalProject.BookingMeetingRoom.model.entity.Chatbot;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotDetailResponse;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotHistoryResponse;
import com.finalProject.BookingMeetingRoom.repository.AiRepository;
import com.finalProject.BookingMeetingRoom.repository.ChatHistoryRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceImplTest {

    @Mock
    private ChatHistoryRepository chatHistoryRepository;

    @Mock
    private AiRepository aiRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ChatHistoryServiceImpl chatHistoryService;

    @Test
    void createSession_shouldReturnUuid() {
        String sessionId = chatHistoryService.createSession();
        assertNotNull(sessionId);
        assertFalse(sessionId.isBlank());
    }

    @Test
    void ensureSessionId_shouldReturnExisting_whenValid() {
        String existing = "session-1";
        assertEquals(existing, chatHistoryService.ensureSessionId(existing));
    }

    @Test
    void ensureSessionId_shouldGenerate_whenBlank() {
        String generated = chatHistoryService.ensureSessionId(" ");
        assertNotNull(generated);
        assertFalse(generated.isBlank());
    }

    @Test
    void log_shouldSkip_whenSessionMissing() {
        chatHistoryService.log(new User(), " ", SenderType.USER, "hello");
        verify(chatHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any(Chat_history.class));
    }

    @Test
    void log_shouldSkip_whenMessageMissing() {
        chatHistoryService.log(new User(), "s1", SenderType.USER, " ");
        verify(chatHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any(Chat_history.class));
    }

    @Test
    void log_shouldSave_whenDataValidAndActiveChatbotExists() {
        User user = new User();
        user.setId("u1");

        Chatbot bot = new Chatbot();
        bot.setId("bot-1");
        when(aiRepository.findFirstByActiveTrueOrderByCreatedAtAsc()).thenReturn(Optional.of(bot));

        chatHistoryService.log(user, "s1", SenderType.USER, "hello");

        ArgumentCaptor<Chat_history> captor = ArgumentCaptor.forClass(Chat_history.class);
        verify(chatHistoryRepository).save(captor.capture());

        Chat_history saved = captor.getValue();
        assertEquals("s1", saved.getSessionId());
        assertEquals(SenderType.USER, saved.getSender());
        assertEquals("hello", saved.getMessage());
        assertEquals(user, saved.getUser());
        assertEquals(bot, saved.getChatbot());
    }

    @Test
    void log_shouldSkip_whenCreateDefaultChatbotFails() {
        when(aiRepository.findFirstByActiveTrueOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(aiRepository.findById("DEFAULT_CHATBOT")).thenReturn(Optional.empty());
        when(aiRepository.save(org.mockito.ArgumentMatchers.any(Chatbot.class)))
                .thenThrow(new RuntimeException("cannot save"));

        chatHistoryService.log(new User(), "s1", SenderType.USER, "hello");

        verify(chatHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any(Chat_history.class));
    }

    @Test
    void getRecentMessages_shouldReturnEmpty_whenInputInvalid() {
        assertTrue(chatHistoryService.getRecentMessages("", SenderType.USER, 5).isEmpty());
        assertTrue(chatHistoryService.getRecentMessages("s1", null, 5).isEmpty());
        assertTrue(chatHistoryService.getRecentMessages("s1", SenderType.USER, 0).isEmpty());
    }

    @Test
    void getRecentMessages_shouldFilterTrimAndLimit() {
        Chat_history a = new Chat_history();
        a.setMessage("  hi  ");

        Chat_history b = new Chat_history();
        b.setMessage("   ");

        Chat_history c = new Chat_history();
        c.setMessage("bot");

        when(chatHistoryRepository.findTop10BySessionIdAndSenderOrderByCreatedAtDesc("s1", SenderType.BOT))
                .thenReturn(List.of(a, b, c));

        List<String> result = chatHistoryService.getRecentMessages("s1", SenderType.BOT, 1);

        assertEquals(1, result.size());
        assertEquals("hi", result.get(0));
    }

    @Test
    void deleteSession_shouldThrowValidationFailed_whenSessionBlank() {
        CustomException ex = assertThrows(CustomException.class, () -> chatHistoryService.deleteSession(" "));
        assertEquals(ResponseCode.VALIDATION_FAILED, ex.getResponseCode());
    }

    @Test
    void deleteSession_shouldTrimAndDelete() {
        when(chatHistoryRepository.deleteBySessionId("s1")).thenReturn(3L);
        long deleted = chatHistoryService.deleteSession(" s1 ");
        assertEquals(3L, deleted);
    }

    @Test
    void getAllSessionsOfCurrentUser_shouldThrowAccessDenied_whenAuthInvalid() {
        CustomException ex = assertThrows(CustomException.class,
                () -> chatHistoryService.getAllSessionsOfCurrentUser(null));
        assertEquals(ResponseCode.ACCESS_DENIED, ex.getResponseCode());
    }

    @Test
    void getAllSessionsOfCurrentUser_shouldThrowUserNotFound_whenMissingUser() {
        when(authentication.getName()).thenReturn("u@mail.com");
        when(userRepository.findByEmail("u@mail.com")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> chatHistoryService.getAllSessionsOfCurrentUser(authentication));

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void getAllSessionsOfCurrentUser_shouldGroupBySessionAndCount() {
        User user = new User();
        user.setId("u1");

        Chat_history row1 = new Chat_history();
        row1.setSessionId("s1");
        row1.setSender(SenderType.USER);
        row1.setMessage("m1");
        row1.setCreatedAt(LocalDateTime.of(2026, 4, 20, 10, 5));

        Chat_history row2 = new Chat_history();
        row2.setSessionId("s1");
        row2.setSender(SenderType.BOT);
        row2.setMessage("m2");
        row2.setCreatedAt(LocalDateTime.of(2026, 4, 20, 10, 0));

        Chat_history row3 = new Chat_history();
        row3.setSessionId("s2");
        row3.setSender(SenderType.USER);
        row3.setMessage("x1");
        row3.setCreatedAt(LocalDateTime.of(2026, 4, 20, 11, 0));

        when(authentication.getName()).thenReturn("u@mail.com");
        when(userRepository.findByEmail("u@mail.com")).thenReturn(Optional.of(user));
        when(chatHistoryRepository.findByUser_IdOrderByCreatedAtDesc("u1")).thenReturn(List.of(row1, row2, row3));

        List<ChatbotHistoryResponse> result = chatHistoryService.getAllSessionsOfCurrentUser(authentication);

        assertEquals(2, result.size());
        ChatbotHistoryResponse s1 = result.stream().filter(r -> "s1".equals(r.getSessionId())).findFirst().orElseThrow();
        assertEquals(2L, s1.getMessageCount());
        assertEquals(LocalDateTime.of(2026, 4, 20, 10, 0), s1.getStartedAt());
    }

    @Test
    void getSessionDetailOfCurrentUser_shouldThrowValidation_whenSessionBlank() {
        CustomException ex = assertThrows(CustomException.class,
                () -> chatHistoryService.getSessionDetailOfCurrentUser("", authentication));
        assertEquals(ResponseCode.VALIDATION_FAILED, ex.getResponseCode());
    }

    @Test
    void getSessionDetailOfCurrentUser_shouldThrowValidation_whenSessionNotFound() {
        User user = new User();
        user.setId("u1");

        when(authentication.getName()).thenReturn("u@mail.com");
        when(userRepository.findByEmail("u@mail.com")).thenReturn(Optional.of(user));
        when(chatHistoryRepository.findBySessionIdAndUser_IdOrderByCreatedAtAsc("s1", "u1")).thenReturn(List.of());

        CustomException ex = assertThrows(CustomException.class,
                () -> chatHistoryService.getSessionDetailOfCurrentUser("s1", authentication));

        assertEquals(ResponseCode.VALIDATION_FAILED, ex.getResponseCode());
    }

    @Test
    void getSessionDetailOfCurrentUser_shouldReturnConversationDetails() {
        User user = new User();
        user.setId("u1");

        Chat_history row1 = new Chat_history();
        row1.setId("m1");
        row1.setSender(SenderType.USER);
        row1.setMessage("hello");
        row1.setCreatedAt(LocalDateTime.of(2026, 4, 20, 10, 0));

        Chat_history row2 = new Chat_history();
        row2.setId("m2");
        row2.setSender(SenderType.BOT);
        row2.setMessage("hi");
        row2.setCreatedAt(LocalDateTime.of(2026, 4, 20, 10, 1));

        when(authentication.getName()).thenReturn("u@mail.com");
        when(userRepository.findByEmail("u@mail.com")).thenReturn(Optional.of(user));
        when(chatHistoryRepository.findBySessionIdAndUser_IdOrderByCreatedAtAsc("s1", "u1"))
                .thenReturn(List.of(row1, row2));

        ChatbotDetailResponse result = chatHistoryService.getSessionDetailOfCurrentUser(" s1 ", authentication);

        assertEquals("s1", result.getSessionId());
        assertEquals(2L, result.getMessageCount());
        assertEquals(SenderType.BOT, result.getLastSender());
        assertEquals("hi", result.getLastMessage());
        assertEquals(2, result.getMessages().size());
    }
}


