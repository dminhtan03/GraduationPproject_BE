package com.finalProject.BookingMeetingRoom.service.aiplatform;

import com.finalProject.BookingMeetingRoom.model.response.aiplatform.AiPlatformChatResponse;
import com.finalProject.BookingMeetingRoom.model.response.TodaySummaryResponse;
import com.finalProject.BookingMeetingRoom.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiPlatformChatService {

    private final AiIntentService intentService;
    private final AiTaskAgentService taskAgentService;
    private final AiConversationStateStore stateStore;
    private final TaskService taskService;

    private static final String OFF_TOPIC_REPLY =
            "Toi chi ho tro cac van de lien quan den cong viec va to chuc. " +
            "Ban co the hoi ve task, cuoc hop, tien do cong viec.";

    public AiPlatformChatResponse handleMessage(String sessionId, String message, Authentication auth) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
        }

        AiConversationStateStore.ConversationState state = stateStore.get(sessionId);
        if (state != null && state.getMissingFields() != null && !state.getMissingFields().isEmpty()) {
            AiTaskAgentService.AiTaskResult result = taskAgentService.continueFlow(message, sessionId, auth);
            return AiPlatformChatResponse.builder()
                    .reply(result.getMessage())
                    .intent("task")
                    .data(result.getData())
                    .build();
        }

        String intent = intentService.classify(message);
        log.info("AiPlatformChat intent={}", intent);

        switch (intent) {
            case "task" -> {
                AiTaskAgentService.AiTaskResult result = taskAgentService.startFlow(message, sessionId, auth);
                return AiPlatformChatResponse.builder()
                        .reply(result.getMessage())
                        .intent("task")
                        .data(result.getData())
                        .build();
            }
            case "summary" -> {
                return AiPlatformChatResponse.builder()
                        .reply(buildSummary(auth))
                        .intent("summary")
                        .data(null)
                        .build();
            }
            case "meeting" -> {
                return AiPlatformChatResponse.builder()
                        .reply("De xu ly cuoc hop, vui long upload file audio qua trang Meetings.")
                        .intent("meeting")
                        .data(null)
                        .build();
            }
            case "off_topic" -> {
                return AiPlatformChatResponse.builder()
                        .reply(OFF_TOPIC_REPLY)
                        .intent("off_topic")
                        .data(null)
                        .build();
            }
            default -> {
                return AiPlatformChatResponse.builder()
                        .reply(OFF_TOPIC_REPLY)
                        .intent("general")
                        .data(null)
                        .build();
            }
        }
    }

    private String buildSummary(Authentication auth) {
        TodaySummaryResponse summary = taskService.getTodaySummary(auth);
        StringBuilder sb = new StringBuilder();
        sb.append("Tom tat ngay ").append(LocalDate.now()).append("\n");
        sb.append("- Tong task: ").append(summary.getTotalTasks()).append("\n");
        sb.append("- Todo: ").append(summary.getTodoCount()).append("\n");
        sb.append("- Doing: ").append(summary.getDoingCount()).append("\n");
        sb.append("- Waiting review: ").append(summary.getWaitingReviewCount()).append("\n");
        sb.append("- Overdue: ").append(summary.getOverdueCount()).append("\n");
        if (summary.getTodayTasks() != null && !summary.getTodayTasks().isEmpty()) {
            sb.append("Viec hom nay:\n");
            summary.getTodayTasks().stream().limit(8).forEach(t ->
                    sb.append("- ").append(t.getTitle()).append("\n"));
        }
        return sb.toString().trim();
    }
}
