package com.finalProject.BookingMeetingRoom.service.aiplatform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finalProject.BookingMeetingRoom.model.request.TaskRequest;
import com.finalProject.BookingMeetingRoom.model.request.AssignTaskRequest;
import com.finalProject.BookingMeetingRoom.model.response.TaskResponse;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.TaskService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskAgentService {

    private final AiLlmService aiLlmService;
    private final AiConversationStateStore stateStore;
    private final TaskService taskService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final List<String> CREATE_KEYWORDS = List.of(
            "tao task", "tao viec", "tao nhiem vu", "them task", "create task", "them viec"
    );
    private static final List<String> LIST_MY_KEYWORDS = List.of(
            "task ca nhan", "viec ca nhan", "task cua toi", "viec cua toi", "xem task", "xem viec"
    );
    private static final List<String> LIST_ASSIGNED_KEYWORDS = List.of(
            "task duoc giao", "viec duoc giao", "assigned to me", "giao cho toi"
    );
    private static final List<String> ASSIGN_KEYWORDS = List.of(
            "giao task", "giao viec", "giao nhiem vu", "assign", "phan cong"
    );

    public AiTaskResult startFlow(String text, String sessionId, Authentication auth) {
        ParsedAction parsed = parseAction(text);
        String action = fallbackAction(text, parsed.action);

        if ("list_my_tasks".equals(action)) {
            return listMyTasks(auth);
        }
        if ("list_assigned_tasks".equals(action)) {
            return listAssignedTasks(auth);
        }
        if ("create_task".equals(action)) {
            return handleCreateTask(parsed.payload, text, sessionId, auth);
        }
        if ("assign_task".equals(action)) {
            return handleAssignTask(parsed.payload, text, sessionId, auth);
        }

        return new AiTaskResult(false, "Toi chua hieu yeu cau. Ban muon tao task, xem task, hay giao task?", null);
    }

    public AiTaskResult continueFlow(String text, String sessionId, Authentication auth) {
        AiConversationStateStore.ConversationState state = stateStore.get(sessionId);
        if (state == null) {
            return new AiTaskResult(false, "Khong co trang thai pending.", null);
        }

        if ("create_task".equals(state.getPendingAction()) && state.getMissingFields().contains("title")) {
            state.getPayload().put("title", text.trim());
            state.getMissingFields().remove("title");
            stateStore.clear(sessionId);
            return executeCreate(state.getPayload(), auth);
        }

        if ("assign_get_title".equals(state.getPendingAction()) && state.getMissingFields().contains("title")) {
            state.getPayload().put("task_title", text.trim());
            state.getMissingFields().remove("title");
            stateStore.clear(sessionId);
            return finalizeAssign(state.getPayload(), sessionId, auth);
        }

        if ("select_assignee".equals(state.getPendingAction()) && state.getMissingFields().contains("choice")) {
            int idx;
            try {
                idx = Integer.parseInt(text.trim()) - 1;
            } catch (NumberFormatException ex) {
                return new AiTaskResult(false, "Vui long nhap so thu tu hop le.", null);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) state.getPayload().get("candidates");
            if (candidates == null || idx < 0 || idx >= candidates.size()) {
                return new AiTaskResult(false, "So khong hop le.", null);
            }
            Map<String, Object> selected = candidates.get(idx);
            state.getPayload().put("assignee", selected);
            state.getMissingFields().remove("choice");
            stateStore.clear(sessionId);
            return finalizeAssign(state.getPayload(), sessionId, auth);
        }

        stateStore.clear(sessionId);
        return new AiTaskResult(false, "Flow da het han. Vui long thu lai.", null);
    }

    private AiTaskResult handleCreateTask(Map<String, Object> payload, String text, String sessionId, Authentication auth) {
        String title = payload != null ? asString(payload.get("title")) : null;
        if (title == null || title.isBlank()) {
            AiConversationStateStore.ConversationState state = new AiConversationStateStore.ConversationState(
                    sessionId,
                    "create_task",
                    new LinkedHashMap<>(payload != null ? payload : Map.of()),
                    new ArrayList<>(List.of("title")),
                    java.time.Instant.now()
            );
            stateStore.put(state);
            return new AiTaskResult(false, "Ban muon dat tieu de cho task nay la gi?", null);
        }
        return executeCreate(payload, auth);
    }

    private AiTaskResult handleAssignTask(Map<String, Object> payload, String text, String sessionId, Authentication auth) {
        String assigneeName = payload != null ? asString(payload.get("assignee_name")) : null;
        String taskTitle = payload != null ? asString(payload.get("task_title")) : null;

        if (assigneeName == null || assigneeName.isBlank()) {
            return new AiTaskResult(false, "Ban muon giao task cho ai? Vui long nhap ten day du.", null);
        }
        if (taskTitle == null || taskTitle.isBlank()) {
            AiConversationStateStore.ConversationState state = new AiConversationStateStore.ConversationState(
                    sessionId,
                    "assign_get_title",
                    new LinkedHashMap<>(Map.of("assignee_name", assigneeName)),
                    new ArrayList<>(List.of("title")),
                    java.time.Instant.now()
            );
            stateStore.put(state);
            return new AiTaskResult(false, "Ban muon giao task gi?", null);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assignee_name", assigneeName);
        data.put("task_title", taskTitle);
        return finalizeAssign(data, sessionId, auth);
    }

    private AiTaskResult finalizeAssign(Map<String, Object> data, String sessionId, Authentication auth) {
        String taskTitle = asString(data.get("task_title"));
        String assigneeName = asString(data.get("assignee_name"));

        List<Map<String, Object>> matches = findUsersByName(assigneeName);
        if (matches.isEmpty()) {
            return new AiTaskResult(false, "Khong tim thay nguoi dung ten " + assigneeName + ".", null);
        }
        if (matches.size() > 1) {
            List<String> lines = new ArrayList<>();
            lines.add("Tim thay " + matches.size() + " nguoi. Ban muon giao cho ai?");
            for (int i = 0; i < matches.size(); i++) {
                Map<String, Object> u = matches.get(i);
                lines.add((i + 1) + ". " + u.get("fullName") + " - " + u.getOrDefault("email", ""));
            }
            AiConversationStateStore.ConversationState state = new AiConversationStateStore.ConversationState(
                    sessionId,
                    "select_assignee",
                    new LinkedHashMap<>(Map.of("candidates", matches, "task_title", taskTitle, "assignee_name", assigneeName)),
                    new ArrayList<>(List.of("choice")),
                    java.time.Instant.now()
            );
            stateStore.put(state);
            return new AiTaskResult(false, String.join("\n", lines), null);
        }

        Map<String, Object> assignee = matches.get(0);
        return createAndAssign(taskTitle, assignee, auth);
    }

    private AiTaskResult executeCreate(Map<String, Object> payload, Authentication auth) {
        TaskRequest req = new TaskRequest();
        req.setTitle(asString(payload.get("title")));
        req.setDescription(asString(payload.get("description")));
        req.setGoal(asString(payload.get("goal")));
        req.setExpectedResult(asString(payload.get("expected_result")));
        req.setPriority(safePriority(asString(payload.get("priority"))));
        req.setDue_at(asString(payload.get("due_at")));
        TaskResponse created = taskService.createTask(req, auth);
        String msg = "Da tao task: " + created.getTitle();
        return new AiTaskResult(true, msg, Map.of("task", created));
    }

    private AiTaskResult createAndAssign(String taskTitle, Map<String, Object> assignee, Authentication auth) {
        TaskRequest createReq = new TaskRequest();
        createReq.setTitle(taskTitle);
        createReq.setPriority("LOW");
        TaskResponse created = taskService.createTask(createReq, auth);

        AssignTaskRequest assignReq = new AssignTaskRequest();
        assignReq.setAssigneeId(asString(assignee.get("id")));
        TaskResponse assigned = taskService.assignTask(created.getId(), assignReq, auth);

        String msg = "Da tao va giao task " + taskTitle + " cho " + assignee.get("fullName");
        return new AiTaskResult(true, msg, Map.of("task", assigned));
    }

    private AiTaskResult listMyTasks(Authentication auth) {
        List<TaskResponse> tasks = taskService.listMyTasks("personal", null, null, auth);
        if (tasks.isEmpty()) {
            return new AiTaskResult(true, "Ban khong co task ca nhan nao.", null);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Ban co ").append(tasks.size()).append(" task ca nhan:\n");
        for (int i = 0; i < Math.min(8, tasks.size()); i++) {
            TaskResponse t = tasks.get(i);
            sb.append(" ").append(i + 1).append(". ").append(t.getTitle());
            if (t.getDueAt() != null) {
                sb.append(" - Han: ").append(t.getDueAt().toLocalDate());
            }
            sb.append("\n");
        }
        return new AiTaskResult(true, sb.toString().trim(), null);
    }

    private AiTaskResult listAssignedTasks(Authentication auth) {
        List<TaskResponse> tasks = taskService.listAssignedToMe(auth);
        if (tasks.isEmpty()) {
            return new AiTaskResult(true, "Ban khong co task nao duoc giao.", null);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Ban co ").append(tasks.size()).append(" task duoc giao:\n");
        for (int i = 0; i < Math.min(8, tasks.size()); i++) {
            TaskResponse t = tasks.get(i);
            sb.append(" ").append(i + 1).append(". ").append(t.getTitle());
            if (t.getDueAt() != null) {
                sb.append(" - Han: ").append(t.getDueAt().toLocalDate());
            }
            sb.append("\n");
        }
        return new AiTaskResult(true, sb.toString().trim(), null);
    }

    private ParsedAction parseAction(String text) {
        String systemPrompt = "Ban la he thong phan tich yeu cau quan ly cong viec. Tra JSON duy nhat.";
        String userPrompt = buildTaskPrompt(text);
        JsonNode node = aiLlmService.runJson(systemPrompt, userPrompt, 0.2);
        if (node == null || !node.isObject()) {
            return new ParsedAction("unknown", Map.of());
        }
        String action = node.path("action").asText("unknown");
        Map<String, Object> payload = objectMapper.convertValue(
                node.path("payload"), new TypeReference<Map<String, Object>>() {}
        );
        return new ParsedAction(action, payload != null ? payload : Map.of());
    }

    private String buildTaskPrompt(String text) {
        String currentDate = LocalDate.now().toString();
        return "Yeu cau: " + text + "\nNgay hien tai: " + currentDate + "\n" +
                "Tra ve JSON: {\"action\": \"create_task|list_my_tasks|list_assigned_tasks|assign_task|unknown\", \"payload\": {..}}";
    }

    private String fallbackAction(String text, String action) {
        if (action != null && !"unknown".equals(action)) {
            return action;
        }
        String lower = fold(text);
        if (containsAny(lower, CREATE_KEYWORDS)) {
            return "create_task";
        }
        if (containsAny(lower, LIST_ASSIGNED_KEYWORDS)) {
            return "list_assigned_tasks";
        }
        if (containsAny(lower, LIST_MY_KEYWORDS)) {
            return "list_my_tasks";
        }
        if (containsAny(lower, ASSIGN_KEYWORDS)) {
            return "assign_task";
        }
        return "unknown";
    }

    private List<Map<String, Object>> findUsersByName(String name) {
        String query = fold(name);
        List<Map<String, Object>> matches = new ArrayList<>();
        userRepository.findAll().forEach(u -> {
            String fullName = (u.getUserInfo() != null)
                    ? ((u.getUserInfo().getFirstName() != null ? u.getUserInfo().getFirstName() : "") +
                    " " + (u.getUserInfo().getLastName() != null ? u.getUserInfo().getLastName() : "")).trim()
                    : "";
            String folded = fold(fullName);
            if (!fullName.isBlank() && (folded.contains(query) || query.contains(folded))) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", u.getId());
                m.put("fullName", fullName);
                m.put("email", u.getUserInfo() != null ? u.getUserInfo().getEmail() : null);
                matches.add(m);
            }
        });
        return matches;
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private String fold(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safePriority(String priority) {
        if (priority == null) {
            return "LOW";
        }
        String p = priority.trim().toUpperCase(Locale.ROOT);
        return switch (p) {
            case "HIGH", "URGENT", "LOW" -> p;
            default -> "LOW";
        };
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    @Data
    @AllArgsConstructor
    public static class AiTaskResult {
        private boolean success;
        private String message;
        private Map<String, Object> data;
    }

    private static class ParsedAction {
        private final String action;
        private final Map<String, Object> payload;

        private ParsedAction(String action, Map<String, Object> payload) {
            this.action = action;
            this.payload = payload;
        }
    }
}
