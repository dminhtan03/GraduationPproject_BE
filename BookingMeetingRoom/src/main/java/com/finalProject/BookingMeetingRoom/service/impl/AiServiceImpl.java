package com.finalProject.BookingMeetingRoom.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.finalProject.BookingMeetingRoom.model.response.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finalProject.BookingMeetingRoom.common.config.OpenAiProperties;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.request.AiChatRequest;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomSearchRequest;
import com.finalProject.BookingMeetingRoom.service.AiService;
import com.finalProject.BookingMeetingRoom.service.DashboardService;
import com.finalProject.BookingMeetingRoom.service.ReservationService;
import com.finalProject.BookingMeetingRoom.service.RoomService;
import com.finalProject.BookingMeetingRoom.service.chat.ChatHistoryService;
import com.finalProject.BookingMeetingRoom.service.ai.RagRoomResolver;
import com.finalProject.BookingMeetingRoom.model.dto.RoomLookupItem;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private static final int MAX_TOOL_STEPS = 6;
    private static final int MAX_ERROR_BODY_LENGTH = 800;

            private static final Pattern LOCATION_CODE_PATTERN = Pattern.compile("\\b([A-Z]{1,4}\\d{1,4}-\\d{1,4})\\b", Pattern.CASE_INSENSITIVE);
            private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
                "(\\d{1,2})(?:h|:)(\\d{0,2})?\\s*(den|-|–|to)\\s*(\\d{1,2})(?:h|:)(\\d{0,2})?",
                Pattern.CASE_INSENSITIVE);
            private static final Pattern RESERVATION_ID_PATTERN = Pattern.compile(
                "\\b([a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12})\\b",
                Pattern.CASE_INSENSITIVE);
            private static final Pattern RESERVATION_TOKEN_PATTERN = Pattern.compile("\\b(RES[0-9A-Z_-]{3,})\\b", Pattern.CASE_INSENSITIVE);
            private static final Pattern EXTEND_HOUR_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(gio|h)\\b", Pattern.CASE_INSENSITIVE);
            private static final Pattern CANCEL_REASON_PATTERN = Pattern.compile("(ly do|vi)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
                private static final Pattern DATE_SLASH_PATTERN = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{2,4})\\b");
                private static final Pattern TIME_AMPM_PATTERN = Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b", Pattern.CASE_INSENSITIVE);

    private final Logger logger = LoggerFactory.getLogger(AiServiceImpl.class);

    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;
    private final DashboardService dashboardService;
    private final RoomService roomService;
    private final ReservationService reservationService;
    private final RagRoomResolver ragRoomResolver;
    private final ChatHistoryService chatHistoryService;
    private final UserRepository userRepository;

    @Override
    public AiChatResponse chat(AiChatRequest request, Authentication authentication) {
        String sessionId = StringUtils.hasText(request.getSessionId()) ? request.getSessionId() : UUID.randomUUID().toString();
        User chatUser = resolveChatUser(authentication);
        logChatMessage(chatUser, sessionId, SenderType.USER, request == null ? null : request.getMessage());

        AiChatResponse fastPath = tryFastPathReservationAction(request, authentication, sessionId);
        if (fastPath != null) {
            logChatMessage(chatUser, sessionId, SenderType.BOT, fastPath.getReply());
            return fastPath;
        }

        fastPath = tryFastPathRoomDetail(request, sessionId);
        if (fastPath != null) {
            logChatMessage(chatUser, sessionId, SenderType.BOT, fastPath.getReply());
            return fastPath;
        }

        fastPath = tryFastPathBooking(request, authentication, sessionId);
        if (fastPath != null) {
            logChatMessage(chatUser, sessionId, SenderType.BOT, fastPath.getReply());
            return fastPath;
        }

        fastPath = tryFastPathCurrentAvailability(request, sessionId);
        if (fastPath != null) {
            logChatMessage(chatUser, sessionId, SenderType.BOT, fastPath.getReply());
            return fastPath;
        }

        fastPath = tryFastPathAvailability(request, sessionId);
        if (fastPath != null) {
            logChatMessage(chatUser, sessionId, SenderType.BOT, fastPath.getReply());
            return fastPath;
        }

        if (!StringUtils.hasText(openAiProperties.getApiKey())) {
            AiChatResponse response = AiChatResponse.builder()
                    .sessionId(sessionId)
                    .reply("Chua cau hinh OpenAI API key. Vui long them ai.openai.api-key trong application-local.yml.")
                    .build();
            logChatMessage(chatUser, sessionId, SenderType.BOT, response.getReply());
            return response;
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        String language = detectLanguage(request.getMessage());
        messages.add(systemMessage(language));
        appendRagContext(messages, request.getMessage());
        messages.add(userMessage(request));

        ToolContext context = new ToolContext();
        for (int step = 0; step < MAX_TOOL_STEPS; step++) {
            JsonNode response = callOpenAi(messages, buildTools());
            JsonNode messageNode = response.path("choices").path(0).path("message");
            JsonNode toolCalls = messageNode.path("tool_calls");

            if (toolCalls.isArray() && toolCalls.size() > 0) {
                Map<String, Object> assistantMessage = new LinkedHashMap<>();
                assistantMessage.put("role", "assistant");
                assistantMessage.put("tool_calls", objectMapper.convertValue(toolCalls, new TypeReference<List<Object>>() {}));
                messages.add(assistantMessage);

                for (JsonNode toolCall : toolCalls) {
                    String toolCallId = toolCall.path("id").asText();
                    String name = toolCall.path("function").path("name").asText();
                    String args = toolCall.path("function").path("arguments").asText("{}");

                    String toolResult = handleToolCall(name, args, authentication, context);

                    Map<String, Object> toolMessage = new LinkedHashMap<>();
                    toolMessage.put("role", "tool");
                    toolMessage.put("tool_call_id", toolCallId);
                    toolMessage.put("content", toolResult);
                    messages.add(toolMessage);
                }

                continue;
            }

            String reply = messageNode.path("content").asText("");
            if (context.roomDetail != null) {
                reply = buildRoomDetailReply(request.getMessage(), context.roomDetail.getLocationCode());
            }
                AiChatResponse aiResponse = AiChatResponse.builder()
                    .sessionId(sessionId)
                    .reply(reply)
                    .suggestions(context.suggestions)
                    .reservationCreated(context.reservationCreated)
                    .reservation(context.reservation)
                    .roomDetail(context.roomDetail)
                    .build();
                aiResponse = normalizeAvailabilityResponse(request.getMessage(), aiResponse);
                logChatMessage(chatUser, sessionId, SenderType.BOT, aiResponse.getReply());
                return aiResponse;
        }

        String fallbackReply = (context.suggestions != null && !context.suggestions.isEmpty())
            ? "Da tim duoc phong phu hop theo yeu cau. Ban muon chon phong nao?"
            : "He thong AI da dat gioi han buoc xu ly. Vui long thu lai voi yeu cau ro rang hon.";

        if (context.roomDetail != null) {
            fallbackReply = buildRoomDetailReply(null, context.roomDetail.getLocationCode());
        }

        AiChatResponse response = AiChatResponse.builder()
                .sessionId(sessionId)
                .reply(fallbackReply)
                .suggestions(context.suggestions)
                .reservationCreated(context.reservationCreated)
                .reservation(context.reservation)
                .roomDetail(context.roomDetail)
                .build();
        response = normalizeAvailabilityResponse(request.getMessage(), response);
        logChatMessage(chatUser, sessionId, SenderType.BOT, response.getReply());
        return response;
    }

    private User resolveChatUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !StringUtils.hasText(authentication.getName())) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }

    private void logChatMessage(User user, String sessionId, SenderType sender, String message) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(message)) {
            return;
        }

        try {
            chatHistoryService.log(user, sessionId, sender, message);
        } catch (Exception ex) {
            logger.debug("Failed to log chat history for session {} sender {}: {}", sessionId, sender, ex.getMessage());
        }
    }

    private Map<String, Object> systemMessage(String language) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String content;
        if ("en".equals(language)) {
            content = "You are a meeting-room booking assistant. "
                + "Reply in the user's language (English or Vietnamese with accents). "
                + "Use tools only for actions (search, book, cancel, return, extend). "
                + "For room availability or room-listing questions, use search_random_available_rooms and return at most 20 random valid rooms that match the user's conditions. Do not manually loop through buildings and floors. Keep the reply concise. "
                + "If the user provides a locationCode (e.g., V5-020) call find_room_by_location_code to get roomId. "
                + "If a tool returns ok=false, explain the reason from code/message and suggest the next step. "
                + "If booking overlaps, suggest alternative rooms if possible. "
                + "After you get a list of suggested rooms, respond immediately without more tool calls. "
                + "If RAG_CONTEXT is available, prefer it. "
                + "Ask for missing required info. "
                + "Current time: " + now + ".";
        } else {
            content = "Ban la tro ly AI dat phong hop. "
                + "Tra loi bang dung ngon ngu nguoi dung (neu nguoi dung hoi tieng Viet thi tra loi tieng Viet co dau, neu hoi tieng Anh thi tra loi tieng Anh). "
                + "Chi su dung tool de thuc hien hanh dong (tim phong, dat phong, huy, tra phong, gia han). "
                + "Voi cau hoi ve danh sach phong hoac phong trong, hay dung search_random_available_rooms de lay toi da 20 phong hop le ngau nhien theo dieu kien nguoi dung. Khong tu lap vong qua building va floor. Giữ cau tra loi ngan gon. "
                + "Neu nguoi dung dua locationCode (vi du V5-020) thi goi tool find_room_by_location_code de lay roomId. "
                + "Neu tool tra ve ok=false thi phai noi ro ly do tu code/message va de xuat buoc tiep theo. "
                + "Neu dat phong bi overlap thi de xuat phong khac phu hop neu co the. "
                + "Sau khi da tim duoc danh sach phong goi y thi tra loi ngay, khong goi tool lap lai. "
                + "Neu co RAG_CONTEXT thi uu tien su dung thong tin do. "
                + "Neu truy van chi tiet phong, khong can format markdown; du lieu tra ve trong JSON roomDetail. "
                + "Neu thieu thong tin bat buoc thi hoi lai nguoi dung. "
                + "Thoi gian hien tai: " + now + ".";
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "system");
        message.put("content", content);
        return message;
    }

    private Map<String, Object> userMessage(AiChatRequest request) {
        StringBuilder content = new StringBuilder();
        content.append(request.getMessage() == null ? "" : request.getMessage());

        if (request.getStartTime() != null) {
            content.append("\nstartTime: ").append(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            content.append("\nendTime: ").append(request.getEndTime());
        }
        if (request.getCapacity() != null) {
            content.append("\ncapacity: ").append(request.getCapacity());
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", content.toString());
        return message;
    }

    private AiChatResponse tryFastPathBooking(AiChatRequest request, Authentication authentication, String sessionId) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            return null;
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String message = request.getMessage();
        String locationCode = extractLocationCode(message);
        TimeRange timeRange = extractTimeRange(message);

        if (StringUtils.hasText(locationCode) && timeRange != null) {
            ToolContext context = new ToolContext();
            context.lastLocationCode = locationCode;
            context.lastStartTime = timeRange.start;
            context.lastEndTime = timeRange.end;

            try {
                RoomSearchResponse room = roomService.findRoomByLocationCode(locationCode);
                ReservationRequest reservationRequest = new ReservationRequest();
                reservationRequest.setRoomId(room.getRoomId());
                reservationRequest.setStartTime(timeRange.start);
                reservationRequest.setEndTime(timeRange.end);
                reservationRequest.setPurpose("Dat phong");

                ReservationResponse reservation = reservationService.reserveRoom(reservationRequest, authentication);
                String reply = generateResponseReply(request.getMessage(), "BOOK_SUCCESS", reservation);
                return AiChatResponse.builder()
                        .sessionId(sessionId)
                        .reply(reply)
                        .reservationCreated(true)
                        .reservation(reservation)
                        .build();
            } catch (CustomException ex) {
                if (isOverlap(ex.getResponseCode())) {
                    applyOverlapSuggestions(context);
                }
                String messageReply = ex.getResponseCode() != null ? ex.getResponseCode().getMessage() : "Khong the dat phong.";
                String reply = generateResponseReply(request.getMessage(), "BOOK_FAILED", messageReply);
                return AiChatResponse.builder()
                        .sessionId(sessionId)
                        .reply(reply)
                        .suggestions(context.suggestions)
                        .reservationCreated(false)
                        .build();
            }
        }

        TimeRange fallbackRange = timeRange;
        if (fallbackRange == null) {
            LocalDateTime englishStart = extractEnglishDateTime(message);
            if (englishStart != null) {
                fallbackRange = new TimeRange(englishStart, englishStart.plusHours(1));
            }
        }

        RagRoomResolver.RagResult rag = ragRoomResolver.resolve(message);
        if (fallbackRange == null || rag.capacity() == null || rag.candidates() == null || rag.candidates().isEmpty()) {
            return null;
        }

        List<RoomLookupItem> candidates = new ArrayList<>(rag.candidates());
        Collections.shuffle(candidates);

        CustomException lastError = null;
        for (RoomLookupItem candidate : candidates) {
            try {
                ReservationRequest reservationRequest = new ReservationRequest();
                reservationRequest.setRoomId(candidate.getRoomId());
                reservationRequest.setStartTime(fallbackRange.start);
                reservationRequest.setEndTime(fallbackRange.end);
                reservationRequest.setPurpose("Dat phong");

                ReservationResponse reservation = reservationService.reserveRoom(reservationRequest, authentication);
                String reply = generateResponseReply(request.getMessage(), "BOOK_SUCCESS", reservation);
                return AiChatResponse.builder()
                        .sessionId(sessionId)
                        .reply(reply)
                        .reservationCreated(true)
                        .reservation(reservation)
                        .build();
            } catch (CustomException ex) {
                lastError = ex;
                if (!isOverlap(ex.getResponseCode())) {
                    continue;
                }
            }
        }

        String messageReply = lastError != null && lastError.getResponseCode() != null
                ? lastError.getResponseCode().getMessage()
                : "Khong tim thay phong phu hop.";
        String reply = generateResponseReply(request.getMessage(), "BOOK_FAILED", messageReply);
        return AiChatResponse.builder()
                .sessionId(sessionId)
                .reply(reply)
                .reservationCreated(false)
                .build();
    }

    private AiChatResponse tryFastPathRoomDetail(AiChatRequest request, String sessionId) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            return null;
        }

        String normalized = normalizeMessage(request.getMessage());
        boolean asksDetail = normalized.contains("chi tiet")
                || normalized.contains("thong tin chi tiet")
                || normalized.contains("thong tin phong")
                || normalized.contains("room detail")
                || normalized.contains("details");
        if (!asksDetail) {
            return null;
        }

        String locationCode = extractLocationCode(request.getMessage());
        if (!StringUtils.hasText(locationCode)) {
            return null;
        }

        try {
            RoomSearchResponse room = roomService.findRoomByLocationCode(locationCode);
            if (room == null || !StringUtils.hasText(room.getRoomId())) {
                return AiChatResponse.builder()
                        .sessionId(sessionId)
                        .reply("Khong tim thay phong theo ma " + locationCode + ".")
                        .build();
            }

            RoomDetailResponse detail = roomService.getRoomDetail(room.getRoomId());
            if (detail == null) {
                return AiChatResponse.builder()
                        .sessionId(sessionId)
                        .reply("Khong lay duoc chi tiet phong " + locationCode + ".")
                        .build();
            }

                    String reply = buildRoomDetailReply(request.getMessage(), locationCode);
            return AiChatResponse.builder()
                    .sessionId(sessionId)
                    .reply(reply)
                    .roomDetail(detail)
                    .build();
        } catch (CustomException ex) {
            String message = ex.getResponseCode() != null ? ex.getResponseCode().getMessage() : "Khong tim thay phong.";
            return AiChatResponse.builder()
                    .sessionId(sessionId)
                    .reply(message)
                    .build();
        } catch (Exception ex) {
            logger.warn("Fast room detail lookup failed for {}: {}", locationCode, ex.getMessage());
            return null;
        }
    }

    private AiChatResponse tryFastPathReservationAction(AiChatRequest request, Authentication authentication, String sessionId) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            return null;
        }

        String raw = request.getMessage();
        String normalized = normalizeMessage(raw);

        ActionIntent intent = detectActionIntent(normalized);
        if (intent == ActionIntent.NONE) {
            return null;
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            return AiChatResponse.builder()
                    .sessionId(sessionId)
                    .reply("Vui long dang nhap de thuc hien yeu cau nay.")
                    .reservationCreated(false)
                    .build();
        }

        String reservationId = extractReservationId(raw);
        String locationCode = extractLocationCode(raw);
        if (!StringUtils.hasText(reservationId) && !StringUtils.hasText(locationCode)) {
            return AiChatResponse.builder()
                .sessionId(sessionId)
                .reply("Vui long cung cap reservationId hoac ma phong (locationCode).")
                .reservationCreated(false)
                .build();
        }

        try {
            switch (intent) {
                case CANCEL -> {
                    String reason = extractCancelReason(raw);
                    if (!StringUtils.hasText(reason)) {
                        reason = "Khong the tham du";
                    }
                    if (StringUtils.hasText(reservationId)) {
                        reservationService.cancelReservation(reservationId, reason, authentication);
                    } else {
                        reservationService.cancelReservationByLocationCode(locationCode, reason, authentication);
                    }
                    String reply = generateResponseReply(raw, "CANCEL_SUCCESS", null);
                    return AiChatResponse.builder()
                            .sessionId(sessionId)
                            .reply(reply)
                            .reservationCreated(false)
                            .build();
                }
                case RETURN -> {
                    if (StringUtils.hasText(reservationId)) {
                        reservationService.returnRoom(reservationId, authentication);
                    } else {
                        reservationService.returnRoomByLocationCode(locationCode, authentication);
                    }
                    String reply = generateResponseReply(raw, "RETURN_SUCCESS", null);
                    return AiChatResponse.builder()
                            .sessionId(sessionId)
                            .reply(reply)
                            .reservationCreated(false)
                            .build();
                }
                case EXTEND -> {
                    Double hour = extractExtendHour(raw);
                    if (hour == null) {
                        return AiChatResponse.builder()
                                .sessionId(sessionId)
                                .reply("Vui long cho biet so gio muon gia han.")
                                .reservationCreated(false)
                                .build();
                    }
                    if (StringUtils.hasText(reservationId)) {
                        reservationService.extendReservation(reservationId, hour, authentication);
                    } else {
                        reservationService.extendReservationByLocationCode(locationCode, hour, authentication);
                    }
                    String reply = generateResponseReply(raw, "EXTEND_SUCCESS", null);
                    return AiChatResponse.builder()
                            .sessionId(sessionId)
                            .reply(reply)
                            .reservationCreated(false)
                            .build();
                }
                default -> {
                    return null;
                }
            }
        } catch (CustomException ex) {
            String messageReply = ex.getResponseCode() != null ? ex.getResponseCode().getMessage() : "Khong the thuc hien yeu cau.";
            String reply = generateResponseReply(raw, "ACTION_FAILED", messageReply);
            return AiChatResponse.builder()
                    .sessionId(sessionId)
                    .reply(reply)
                    .reservationCreated(false)
                    .build();
        }
    }

    private AiChatResponse tryFastPathCurrentAvailability(AiChatRequest request, String sessionId) {
        if (request == null || !StringUtils.hasText(request.getMessage())) return null;

        String normalized = normalizeMessage(request.getMessage());
        
        // detect current/instant availability question (Vietnamese & English)
        boolean asksAvailability = (normalized.contains("co phong")
                || normalized.contains("phong trong")
                || normalized.contains("phong nao")
                || normalized.contains("available") || normalized.contains("empty") || normalized.contains("free"))
                && (normalized.contains("hien tai")
                    || normalized.contains("bay gio")
                    || normalized.contains("dang")
                    || normalized.contains("now") || normalized.contains("currently") || normalized.contains("right now"));
        
        if (!asksAvailability) return null;

        // ensure NO explicit time range like "10h" - delegate to tryFastPathAvailability
        if (extractTimeRange(request.getMessage()) != null || 
            parseSingleHourRange(request.getMessage()) != null) {
            return null;
        }

        List<String> buildingIds = resolveBuildingIds(normalized);
        LocalDateTime now = LocalDateTime.now();
        List<RoomSearchResponse> suggestions = roomService.searchRandomAvailableRooms(buildingIds, now, now.plusHours(1), 20);

        if (suggestions.isEmpty()) return null;

        String language = detectLanguage(request.getMessage());
        String reply = "vi".equals(language)
                ? "Mình tìm được " + suggestions.size() + " phòng trống hiện tại phù hợp."
                : "I found " + suggestions.size() + " rooms available now.";

        return AiChatResponse.builder()
                .sessionId(sessionId)
                .reply(reply)
                .suggestions(suggestions)
                .reservationCreated(false)
                .build();
    }

    private AiChatResponse tryFastPathAvailability(AiChatRequest request, String sessionId) {
        if (request == null || !StringUtils.hasText(request.getMessage())) return null;

        String normalized = normalizeMessage(request.getMessage());
        // detect availability question in Vietnamese/English
        boolean asksAvailability = normalized.contains("co phong")
                || normalized.contains("phong trong") || normalized.contains("trong")
                || normalized.contains("available") || normalized.contains("free rooms") || normalized.contains("any rooms");
        if (!asksAvailability) return null;

        TimeRange range = extractTimeRange(request.getMessage());
        if (range == null) {
            // try single hour like "10h" or "10:00"
            range = parseSingleHourRange(request.getMessage());
        }
        if (range == null) {
            // try English time format like "11PM today" or "at 10am"
            range = parseEnglishTimeWithToday(request.getMessage());
        }
        if (range == null) return null;

        List<String> buildingIds = resolveBuildingIds(normalized);
        List<RoomSearchResponse> suggestions = roomService.searchRandomAvailableRooms(buildingIds, range.start, range.end, 20);

        if (suggestions.isEmpty()) return null;

        String language = detectLanguage(request.getMessage());
        String reply = "vi".equals(language)
                ? "Mình tìm được " + suggestions.size() + " phòng trống phù hợp."
                : "I found " + suggestions.size() + " available rooms.";

        return AiChatResponse.builder()
                .sessionId(sessionId)
                .reply(reply)
                .suggestions(suggestions)
                .reservationCreated(false)
                .build();
    }

    private TimeRange parseEnglishTimeWithToday(String message) {
        if (!StringUtils.hasText(message)) return null;
        
        // Pattern: "11PM", "10am", "11:30pm", "at 10am", "at 11PM today", etc.
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(message);
        
        if (!matcher.find()) {
            return null;
        }
        
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        String ampm = matcher.group(3).toLowerCase(Locale.ROOT);
        
        // Convert to 24h format
        if ("pm".equals(ampm) && hour != 12) {
            hour += 12;
        } else if ("am".equals(ampm) && hour == 12) {
            hour = 0;
        }
        
        // Use today's date or tomorrow if specified
        LocalDate date = LocalDate.now();
        String normalized = normalizeMessage(message);
        if (normalized.contains("ngay mai") || normalized.contains("tomorrow")) {
            date = date.plusDays(1);
        }
        
        LocalDateTime start = date.atTime(hour, minute);
        LocalDateTime end = start.plusHours(1);
        
        return new TimeRange(start, end);
    }

    private List<String> resolveBuildingIds(String normalizedMessage) {
        List<com.finalProject.BookingMeetingRoom.model.response.AmbiguousBuildingResponse> buildings = dashboardService.getAllBuildings();
        List<String> buildingIds = new ArrayList<>();

        for (com.finalProject.BookingMeetingRoom.model.response.AmbiguousBuildingResponse building : buildings) {
            if (buildingMatchesMessage(normalizedMessage, building.getName())) {
                buildingIds.add(building.getId());
            }
        }

        return buildingIds;
    }

    private boolean buildingMatchesMessage(String normalizedMessage, String buildingName) {
        if (!StringUtils.hasText(normalizedMessage) || !StringUtils.hasText(buildingName)) {
            return false;
        }

        String normalizedBuildingName = normalizeMessage(buildingName);
        String[] tokens = normalizedBuildingName.split("\\s+");
        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            if (token.matches("^(toa|nha|building|tac|nhan|day|khu|house|block|tower|complex|center|centre)$")) {
                continue;
            }
            if (normalizedMessage.contains(token)) {
                return true;
            }
        }

        return false;
    }

    private TimeRange parseSingleHourRange(String message) {
        if (!StringUtils.hasText(message)) return null;
        String normalized = normalizeMessage(message);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\b(\\d{1,2})h\\b");
        java.util.regex.Matcher m = p.matcher(normalized);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            LocalDate date = LocalDate.now();
            if (normalized.contains("ngay mai") || normalized.contains("tomorrow")) {
                date = date.plusDays(1);
            }
            LocalDateTime start = date.atTime(hour, 0);
            LocalDateTime end = start.plusHours(1);
            return new TimeRange(start, end);
        }
        return null;
    }

    private String generateResponseReply(String userMessage, String outcome, Object data) {
        if (!StringUtils.hasText(openAiProperties.getApiKey())) {
            return data == null ? "" : String.valueOf(data);
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> system = new LinkedHashMap<>();
        String language = detectLanguage(userMessage);
        String systemPrompt = "Ban la tro ly AI. Tra loi ngan gon, dung ngon ngu nguoi dung (tieng Viet co dau hoac tieng Anh). "
            + "Chi tra loi 1-2 cau, khong nhac den he thong. Neu can, de xuat buoc tiep theo ngan gon.";
        if ("en".equals(language)) {
            systemPrompt = "You are an AI assistant. Reply briefly in the user's language (English or Vietnamese with accents). "
                + "Use 1-2 sentences, do not mention the system. If needed, suggest the next step briefly.";
        }
        if ("ROOM_DETAIL".equals(outcome)) {
            if ("en".equals(language)) {
                systemPrompt = "You are an AI assistant that replies with a simple room intro in English. "
                    + "Return exactly one short sentence only. Start with 'Here is the room information for ...'. "
                    + "Do not use bullet points, markdown, lists, or extra details. Do not invent information.";
            } else {
                systemPrompt = "Ban la tro ly AI tra loi gioi thieu phong bang tieng Viet co dau. "
                    + "Tra loi dung 1 cau ngan, bat dau bang 'Day la thong tin cua phong ...'. "
                    + "Khong dung bullet points, markdown, danh sach, hay thong tin bo sung. Khong tu tao them du lieu.";
            }
        }
        system.put("role", "system");
        system.put("content", systemPrompt);
        messages.add(system);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        user.put("content", "User message: " + (userMessage == null ? "" : userMessage)
                + "\nOutcome: " + outcome
                + "\nData: " + (data == null ? "" : data));
        messages.add(user);

        JsonNode response = callOpenAiForResponseOnly(messages);
        return response.path("choices").path(0).path("message").path("content").asText("");
    }

    private String buildRoomDetailReply(String userMessage, String locationCode) {
        String code = StringUtils.hasText(locationCode) ? locationCode.trim() : "phong nay";
        String language = detectLanguage(userMessage);
        if ("en".equals(language)) {
            return "Here is the room information for " + code + ".";
        }
        return "Đây là thông tin của phòng " + code + ".";
    }

    private AiChatResponse normalizeAvailabilityResponse(String userMessage, AiChatResponse response) {
        if (response == null || response.getSuggestions() == null || response.getSuggestions().isEmpty()) {
            return response;
        }

        if (!isAvailabilityListQuery(userMessage)) {
            return response;
        }

        List<RoomSearchResponse> filteredSuggestions = response.getSuggestions().stream()
                .filter(item -> item != null && item.getStatus() == com.finalProject.BookingMeetingRoom.common.enums.RoomStatus.AVAILABLE)
                .limit(20)
                .toList();

        if (filteredSuggestions.isEmpty()) {
            return response;
        }

        String language = detectLanguage(userMessage);
        String reply = "vi".equals(language)
                ? "Mình tìm được " + filteredSuggestions.size() + " phòng phù hợp."
                : "I found " + filteredSuggestions.size() + " matching rooms.";

        return AiChatResponse.builder()
                .sessionId(response.getSessionId())
                .reply(reply)
                .suggestions(filteredSuggestions)
                .reservationCreated(response.isReservationCreated())
                .reservation(response.getReservation())
                .roomDetail(response.getRoomDetail())
                .build();
    }

    private boolean isAvailabilityListQuery(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }

        String normalized = normalizeMessage(message);
        boolean asksAvailability = normalized.contains("phòng") || normalized.contains("phong")
                || normalized.contains("available") || normalized.contains("free") || normalized.contains("empty");
        boolean asksList = normalized.contains("những phòng") || normalized.contains("phong nao")
                || normalized.contains("phong nào") || normalized.contains("rooms")
                || normalized.contains("which rooms") || normalized.contains("what rooms");
        boolean asksTime = normalized.contains("ngay mai") || normalized.contains("tomorrow")
                || normalized.contains("hien tai") || normalized.contains("now") || normalized.contains("currently")
                || normalized.matches(".*\\b\\d{1,2}h\\b.*") || normalized.matches(".*\\b\\d{1,2}(?::\\d{2})?\\s*(am|pm)\\b.*");

        return asksAvailability && (asksList || asksTime);
    }

    private String detectLanguage(String message) {
        if (!StringUtils.hasText(message)) {
            return "vi";
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.matches(".*[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ].*")) {
            return "vi";
        }
        if (normalized.contains("the") || normalized.contains("please") || normalized.contains("book")
                || normalized.contains("cancel") || normalized.contains("return") || normalized.contains("extend")) {
            return "en";
        }
        return "en";
    }

    private JsonNode callOpenAiForResponseOnly(List<Map<String, Object>> messages) {
        String url = openAiProperties.getBaseUrl();
        if (!StringUtils.hasText(url)) {
            url = "https://api.openai.com/v1";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", openAiProperties.getModel());
        payload.put("messages", messages);
        payload.put("temperature", 0.2);
        int maxTokens = openAiProperties.getResponseMaxTokens();
        if (maxTokens > 0) {
            payload.put("max_tokens", maxTokens);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiProperties.getApiKey().trim());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate().postForEntity(url + "/chat/completions", entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception ex) {
            logger.warn("OpenAI response-only request failed", ex);
            return objectMapper.createObjectNode();
        }
    }

    private String extractLocationCode(String message) {
        Matcher matcher = LOCATION_CODE_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    private String extractReservationId(String message) {
        Matcher matcher = RESERVATION_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        matcher = RESERVATION_TOKEN_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    private String extractCancelReason(String message) {
        Matcher matcher = CANCEL_REASON_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(2).trim();
        }
        return null;
    }

    private Double extractExtendHour(String message) {
        String normalized = normalizeMessage(message);
        Matcher matcher = EXTEND_HOUR_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1).replace(',', '.'));
        }
        return null;
    }

    private TimeRange extractTimeRange(String message) {
        String normalized = normalizeMessage(message);
        LocalDate date = LocalDate.now();
        if (normalized.contains("ngay mai") || normalized.contains("sang mai")
                || normalized.contains("tomorrow") || normalized.contains(" mai ")) {
            date = date.plusDays(1);
        }

        Matcher matcher = TIME_RANGE_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }

        int startHour = Integer.parseInt(matcher.group(1));
        int startMinute = parseMinute(matcher.group(2));
        int endHour = Integer.parseInt(matcher.group(4));
        int endMinute = parseMinute(matcher.group(5));

        LocalDateTime start = date.atTime(startHour, startMinute);
        LocalDateTime end = date.atTime(endHour, endMinute);
        if (end.isBefore(start)) {
            end = start.plusHours(1);
        }

        return new TimeRange(start, end);
    }

    private LocalDateTime extractEnglishDateTime(String message) {
        Matcher dateMatcher = DATE_SLASH_PATTERN.matcher(message);
        if (!dateMatcher.find()) {
            return null;
        }
        int month = Integer.parseInt(dateMatcher.group(1));
        int day = Integer.parseInt(dateMatcher.group(2));
        int year = Integer.parseInt(dateMatcher.group(3));
        if (year < 100) {
            year = 2000 + year;
        }

        Matcher timeMatcher = TIME_AMPM_PATTERN.matcher(message);
        if (!timeMatcher.find()) {
            return null;
        }
        int hour = Integer.parseInt(timeMatcher.group(1));
        int minute = timeMatcher.group(2) != null ? Integer.parseInt(timeMatcher.group(2)) : 0;
        String ampm = timeMatcher.group(3).toLowerCase(Locale.ROOT);
        if ("pm".equals(ampm) && hour < 12) {
            hour += 12;
        } else if ("am".equals(ampm) && hour == 12) {
            hour = 0;
        }

        return LocalDateTime.of(year, month, day, hour, minute);
    }

    private int parseMinute(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private String normalizeMessage(String message) {
        String normalized = Normalizer.normalize(message, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String stripMarkdown(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String value = text;
        value = value.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
        value = value.replaceAll("\\*([^*]+)\\*", "$1");
        value = value.replaceAll("`([^`]+)`", "$1");
        value = value.replaceAll("^\\s*[-*+]\\s+", "");
        value = value.replaceAll("^\\s*\\d+\\.\\s+", "");
        value = value.replaceAll("!\\[([^\\]]*)\\]\\([^\\)]+\\)", "$1");
        value = value.replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1");
        return value;
    }

    private String toSingleLine(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private ActionIntent detectActionIntent(String normalized) {
        if (normalized.contains("huy") || normalized.contains("cancel")) {
            return ActionIntent.CANCEL;
        }
        if (normalized.contains("tra phong") || normalized.contains("return")) {
            return ActionIntent.RETURN;
        }
        if (normalized.contains("gia han") || normalized.contains("them gio") || normalized.contains("extend")) {
            return ActionIntent.EXTEND;
        }
        return ActionIntent.NONE;
    }

    private enum ActionIntent {
        CANCEL,
        RETURN,
        EXTEND,
        NONE
    }

    private static class TimeRange {
        private final LocalDateTime start;
        private final LocalDateTime end;

        private TimeRange(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }

    private void appendRagContext(List<Map<String, Object>> messages, String message) {
        RagRoomResolver.RagResult result = ragRoomResolver.resolve(message);
        if (!result.hasContext()) {
            return;
        }

        StringBuilder content = new StringBuilder();
        content.append("RAG_CONTEXT:\n");

        if (result.exactRoom() != null) {
            RoomLookupItem room = result.exactRoom();
            content.append("exactRoom={roomId=").append(room.getRoomId())
                    .append(", locationCode=").append(room.getLocationCode())
                    .append(", buildingId=").append(room.getBuildingId())
                    .append(", floorId=").append(room.getFloorId())
                    .append(", capacity=").append(room.getCapacity())
                    .append("}\n");
        }

        if (result.candidates() != null && !result.candidates().isEmpty()) {
            content.append("candidates=[");
            for (int i = 0; i < result.candidates().size(); i++) {
                RoomLookupItem room = result.candidates().get(i);
                content.append("{roomId=").append(room.getRoomId())
                        .append(", locationCode=").append(room.getLocationCode())
                        .append(", buildingId=").append(room.getBuildingId())
                        .append(", floorId=").append(room.getFloorId())
                        .append(", capacity=").append(room.getCapacity())
                        .append("}");
                if (i < result.candidates().size() - 1) {
                    content.append(", ");
                }
            }
            content.append("]\n");
        }

        Map<String, Object> ragMessage = new LinkedHashMap<>();
        ragMessage.put("role", "system");
        ragMessage.put("content", content.toString());
        messages.add(ragMessage);
    }

    private List<Map<String, Object>> buildTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("list_buildings", "Lay danh sach toa nha", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)));
        tools.add(tool("list_floors", "Lay danh sach tang theo buildingId", Map.of(
                "type", "object",
                "properties", Map.of("buildingId", Map.of("type", "string")),
                "required", List.of("buildingId"),
                "additionalProperties", false)));
        tools.add(tool("search_rooms", "Tim phong theo buildingId, floorId va khoang thoi gian", Map.of(
                "type", "object",
                "properties", Map.of(
                        "buildingId", Map.of("type", "string"),
                        "floorId", Map.of("type", "string"),
                        "startTime", Map.of("type", "string", "description", "ISO 8601, vi du 2026-05-04T09:00"),
                        "endTime", Map.of("type", "string", "description", "ISO 8601, vi du 2026-05-04T10:00")
                ),
                "required", List.of("buildingId", "floorId", "startTime", "endTime"),
                "additionalProperties", false)));
        tools.add(tool("get_room_detail", "Lay chi tiet phong theo roomId", Map.of(
                "type", "object",
                "properties", Map.of("roomId", Map.of("type", "string")),
                "required", List.of("roomId"),
                "additionalProperties", false)));
        tools.add(tool("find_room_by_location_code", "Tim phong theo locationCode (duy nhat)", Map.of(
            "type", "object",
            "properties", Map.of("locationCode", Map.of("type", "string")),
            "required", List.of("locationCode"),
            "additionalProperties", false)));
        tools.add(tool("create_reservation", "Dat phong theo roomId va khoang thoi gian", Map.of(
                "type", "object",
                "properties", Map.of(
                        "roomId", Map.of("type", "string"),
                "locationCode", Map.of("type", "string"),
                        "startTime", Map.of("type", "string"),
                        "endTime", Map.of("type", "string"),
                        "purpose", Map.of("type", "string"),
                        "note", Map.of("type", "string")
                ),
            "required", List.of("startTime", "endTime", "purpose"),
                "additionalProperties", false)));
        tools.add(tool("cancel_reservation", "Huy dat phong theo reservationId", Map.of(
                "type", "object",
                "properties", Map.of(
                        "reservationId", Map.of("type", "string"),
                "locationCode", Map.of("type", "string"),
                        "reason", Map.of("type", "string")
                ),
                "required", List.of("reservationId", "reason"),
                "additionalProperties", false)));
        tools.add(tool("extend_reservation", "Gia han dat phong theo reservationId hoac locationCode", Map.of(
                "type", "object",
                "properties", Map.of(
                        "reservationId", Map.of("type", "string"),
                "locationCode", Map.of("type", "string"),
                        "hour", Map.of("type", "number")
                ),
            "required", List.of("hour"),
                "additionalProperties", false)));
        tools.add(tool("return_room", "Tra phong theo reservationId hoac locationCode", Map.of(
                "type", "object",
            "properties", Map.of(
                "reservationId", Map.of("type", "string"),
                "locationCode", Map.of("type", "string")
            ),
            "required", List.of(),
                "additionalProperties", false)));
        return tools;
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private JsonNode callOpenAi(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        String url = openAiProperties.getBaseUrl();
        if (!StringUtils.hasText(url)) {
            url = "https://api.openai.com/v1";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", openAiProperties.getModel());
        payload.put("messages", messages);
        payload.put("tools", tools);
        payload.put("tool_choice", "auto");
        payload.put("temperature", 0.2);
        if (openAiProperties.getMaxTokens() > 0) {
            payload.put("max_tokens", openAiProperties.getMaxTokens());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiProperties.getApiKey().trim());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate().postForEntity(url + "/chat/completions", entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (HttpStatusCodeException ex) {
            String body = ex.getResponseBodyAsString();
            logger.warn("OpenAI request failed: status={}, body={}", ex.getStatusCode(), truncate(body));
            throw new IllegalStateException("OpenAI request failed: " + ex.getStatusCode(), ex);
        } catch (RestClientException | JsonProcessingException ex) {
            logger.warn("OpenAI request failed", ex);
            throw new IllegalStateException("OpenAI request failed", ex);
        }
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = Math.max(openAiProperties.getTimeoutMs(), 1000);
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private String truncate(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        if (body.length() <= MAX_ERROR_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }

    private String handleToolCall(String name, String rawArgs, Authentication authentication, ToolContext context) {
        JsonNode args = parseArgs(rawArgs);

        try {
            return switch (name) {
                case "list_buildings" -> okJson(dashboardService.getAllBuildings());
                case "list_floors" -> {
                    String buildingId = args.path("buildingId").asText(null);
                    if (!StringUtils.hasText(buildingId)) {
                        yield errorJson("missing_fields", "buildingId");
                    }
                    yield okJson(dashboardService.getAllFloorsByBuildingId(buildingId));
                }
                case "search_rooms" -> {
                    String buildingId = args.path("buildingId").asText(null);
                    String floorId = args.path("floorId").asText(null);
                    LocalDateTime startTime = parseDateTime(args.path("startTime").asText(null));
                    LocalDateTime endTime = parseDateTime(args.path("endTime").asText(null));

                    LocalDateTime[] normalized = normalizeTimeRange(startTime, endTime);
                    startTime = normalized[0];
                    endTime = normalized[1];

                    if (!StringUtils.hasText(buildingId) || !StringUtils.hasText(floorId)) {
                        yield errorJson("missing_fields", "buildingId, floorId");
                    }

                    RoomSearchRequest request = new RoomSearchRequest();
                    request.setBuildingId(buildingId);
                    request.setFloorId(floorId);
                    request.setStartTime(startTime);
                    request.setEndTime(endTime);

                    List<RoomSearchResponse> rooms = roomService.searchRooms(request);
                    context.suggestions = rooms;
                    yield okJson(rooms);
                }
                case "search_random_available_rooms" -> {
                    LocalDateTime startTime = parseDateTime(args.path("startTime").asText(null));
                    LocalDateTime endTime = parseDateTime(args.path("endTime").asText(null));
                    int limit = args.path("limit").asInt(20);

                    LocalDateTime[] normalized = normalizeTimeRange(startTime, endTime);
                    startTime = normalized[0];
                    endTime = normalized[1];

                    List<String> buildingIds = new ArrayList<>();
                    JsonNode buildingIdsNode = args.path("buildingIds");
                    if (buildingIdsNode.isArray()) {
                        for (JsonNode buildingIdNode : buildingIdsNode) {
                            if (StringUtils.hasText(buildingIdNode.asText())) {
                                buildingIds.add(buildingIdNode.asText());
                            }
                        }
                    }

                    if (limit <= 0) {
                        limit = 20;
                    }
                    limit = Math.min(limit, 20);

                    List<RoomSearchResponse> rooms = roomService.searchRandomAvailableRooms(buildingIds, startTime, endTime, limit);
                    context.suggestions = rooms;
                    yield okJson(rooms);
                }
                case "get_room_detail" -> {
                    RoomDetailResponse detail = roomService.getRoomDetail(args.path("roomId").asText());
                    context.roomDetail = detail;
                    yield okJson(detail);
                }
                case "find_room_by_location_code" -> {
                    String locationCode = args.path("locationCode").asText(null);
                    if (!StringUtils.hasText(locationCode)) {
                        yield errorJson("missing_fields", "locationCode");
                    }
                    yield okJson(roomService.findRoomByLocationCode(locationCode));
                }
                case "create_reservation" -> {
                    ensureAuth(authentication);
                    String roomId = args.path("roomId").asText(null);
                    String locationCode = args.path("locationCode").asText(null);
                    LocalDateTime startTime = parseDateTime(args.path("startTime").asText(null));
                    LocalDateTime endTime = parseDateTime(args.path("endTime").asText(null));
                    String purpose = args.path("purpose").asText(null);
                    String note = args.path("note").asText(null);

                    LocalDateTime[] normalized = normalizeTimeRange(startTime, endTime);
                    startTime = normalized[0];
                    endTime = normalized[1];

                    if (!StringUtils.hasText(roomId) && StringUtils.hasText(locationCode)) {
                        RoomSearchResponse room = roomService.findRoomByLocationCode(locationCode);
                        roomId = room.getRoomId();
                    }

                    context.lastRoomId = roomId;
                    context.lastLocationCode = locationCode;
                    context.lastStartTime = startTime;
                    context.lastEndTime = endTime;

                    if (!StringUtils.hasText(roomId) || !StringUtils.hasText(purpose)) {
                        yield errorJson("missing_fields", "roomId or locationCode, purpose");
                    }

                    ReservationRequest reservationRequest = new ReservationRequest();
                    reservationRequest.setRoomId(roomId);
                    reservationRequest.setStartTime(startTime);
                    reservationRequest.setEndTime(endTime);
                    reservationRequest.setPurpose(purpose);
                    reservationRequest.setNote(note);

                    ReservationResponse reservation = reservationService.reserveRoom(reservationRequest, authentication);
                    context.reservation = reservation;
                    context.reservationCreated = true;
                    yield okJson(reservation);
                }
                case "cancel_reservation" -> {
                    ensureAuth(authentication);
                    String reservationId = args.path("reservationId").asText(null);
                    String locationCode = args.path("locationCode").asText(null);
                    String reason = args.path("reason").asText(null);
                    if (!StringUtils.hasText(reservationId) && !StringUtils.hasText(locationCode)) {
                        yield errorJson("missing_fields", "reservationId or locationCode");
                    }
                    if (!StringUtils.hasText(reason)) {
                        reason = "Khong the tham du";
                    }
                    if (StringUtils.hasText(reservationId)) {
                        reservationService.cancelReservation(reservationId, reason, authentication);
                    } else {
                        reservationService.cancelReservationByLocationCode(locationCode, reason, authentication);
                    }
                    yield okJson(Map.of("message", "cancelled"));
                }
                case "extend_reservation" -> {
                    ensureAuth(authentication);
                    String reservationId = args.path("reservationId").asText(null);
                    String locationCode = args.path("locationCode").asText(null);
                    if (!StringUtils.hasText(reservationId)) {
                        if (!StringUtils.hasText(locationCode)) {
                            yield errorJson("missing_fields", "reservationId or locationCode");
                        }
                    }
                    double hour = args.path("hour").asDouble(1);
                    if (StringUtils.hasText(reservationId)) {
                        reservationService.extendReservation(reservationId, hour, authentication);
                    } else {
                        reservationService.extendReservationByLocationCode(locationCode, hour, authentication);
                    }
                    yield okJson(Map.of("message", "extended", "hour", hour));
                }
                case "return_room" -> {
                    ensureAuth(authentication);
                    String reservationId = args.path("reservationId").asText(null);
                    String locationCode = args.path("locationCode").asText(null);
                    if (!StringUtils.hasText(reservationId) && !StringUtils.hasText(locationCode)) {
                        yield errorJson("missing_fields", "reservationId or locationCode");
                    }
                    if (StringUtils.hasText(reservationId)) {
                        reservationService.returnRoom(reservationId, authentication);
                    } else {
                        reservationService.returnRoomByLocationCode(locationCode, authentication);
                    }
                    yield okJson(Map.of("message", "returned"));
                }
                default -> errorJson("unknown_tool", "Tool khong ho tro: " + name);
            };
        } catch (CustomException ex) {
            if (isOverlap(ex.getResponseCode())) {
                applyOverlapSuggestions(context);
            }
            return errorJsonWithCode(ex.getResponseCode());
        } catch (Exception ex) {
            return errorJson("tool_error", ex.getMessage());
        }
    }

    private JsonNode parseArgs(String rawArgs) {
        if (!StringUtils.hasText(rawArgs)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(rawArgs);
        } catch (JsonProcessingException ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String okJson(Object data) {
        try {
            return objectMapper.writeValueAsString(Map.of("ok", true, "data", data));
        } catch (JsonProcessingException ex) {
            return "{\"ok\":true}";
        }
    }

    private String errorJson(String code, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("ok", false, "code", code, "message", message));
        } catch (JsonProcessingException ex) {
            return "{\"ok\":false,\"code\":\"" + code + "\"}";
        }
    }

    private String errorJsonWithCode(ResponseCode responseCode) {
        if (responseCode == null) {
            return errorJson("unknown", "Unknown error");
        }
        boolean overlap = isOverlap(responseCode);

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", false);
            payload.put("code", responseCode.getCode());
            payload.put("message", responseCode.getMessage());
            payload.put("overlap", overlap);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return errorJson(responseCode.getCode(), responseCode.getMessage());
        }
    }

    private boolean isOverlap(ResponseCode responseCode) {
        return responseCode == ResponseCode.CANNOT_RESERVE_ROOM
                || responseCode == ResponseCode.USER_TIME_OVERLAP
                || responseCode == ResponseCode.RESERVATION_TIME_OVERLAP
                || responseCode == ResponseCode.ROOM_IN_ACADEMIC_SCHEDULE;
    }

    private void applyOverlapSuggestions(ToolContext context) {
        try {
            RoomContextResponse roomContext = null;
            if (StringUtils.hasText(context.lastLocationCode)) {
                roomContext = roomService.getRoomContextByLocationCode(context.lastLocationCode);
            } else if (StringUtils.hasText(context.lastRoomId)) {
                roomContext = roomService.getRoomContextByRoomId(context.lastRoomId);
            }

            if (roomContext == null
                    || !StringUtils.hasText(roomContext.getFloorId())
                    || !StringUtils.hasText(roomContext.getBuildingId())) {
                return;
            }

            LocalDateTime startTime = context.lastStartTime;
            LocalDateTime endTime = context.lastEndTime;
            LocalDateTime[] normalized = normalizeTimeRange(startTime, endTime);

            RoomSearchRequest request = new RoomSearchRequest();
            request.setBuildingId(roomContext.getBuildingId());
            request.setFloorId(roomContext.getFloorId());
            request.setStartTime(normalized[0]);
            request.setEndTime(normalized[1]);

            List<RoomSearchResponse> rooms = roomService.searchRooms(request);
            if (rooms == null || rooms.isEmpty()) {
                return;
            }

            int limit = Math.min(20, rooms.size());
            context.suggestions = rooms.subList(0, limit);
        } catch (Exception ex) {
            logger.warn("Failed to build overlap suggestions", ex);
        }
    }

    private void ensureAuth(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("auth_required");
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (Exception ex) {
                // try next format
            }
        }

        return LocalDateTime.parse(value);
    }

    private LocalDateTime[] normalizeTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();

        if (startTime == null && endTime == null) {
            startTime = now;
            endTime = now.plusHours(1);
        } else if (startTime != null && endTime == null) {
            endTime = startTime.plusHours(1);
        } else if (startTime == null) {
            startTime = endTime.minusHours(1);
        }

        if (endTime.isBefore(startTime)) {
            LocalDateTime tmp = startTime;
            startTime = endTime;
            endTime = tmp.plusHours(1);
        }

        return new LocalDateTime[] { startTime, endTime };
    }

    private static class ToolContext {
        private List<RoomSearchResponse> suggestions;
        private ReservationResponse reservation;
        private boolean reservationCreated;
        private String lastRoomId;
        private String lastLocationCode;
        private LocalDateTime lastStartTime;
        private LocalDateTime lastEndTime;
        private RoomDetailResponse roomDetail;
    }
}
