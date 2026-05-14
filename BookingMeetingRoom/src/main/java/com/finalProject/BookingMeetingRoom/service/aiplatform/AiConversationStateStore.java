package com.finalProject.BookingMeetingRoom.service.aiplatform;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiConversationStateStore {

    private final ConcurrentHashMap<String, ConversationState> store = new ConcurrentHashMap<>();

    public ConversationState get(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return store.get(sessionId);
    }

    public void put(ConversationState state) {
        if (state == null || state.getSessionId() == null) {
            return;
        }
        store.put(state.getSessionId(), state);
    }

    public void clear(String sessionId) {
        if (sessionId == null) {
            return;
        }
        store.remove(sessionId);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationState {
        private String sessionId;
        private String pendingAction;
        private Map<String, Object> payload;
        private List<String> missingFields;
        private Instant updatedAt = Instant.now();
    }
}
