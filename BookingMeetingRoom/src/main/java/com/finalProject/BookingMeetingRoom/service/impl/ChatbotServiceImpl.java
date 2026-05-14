package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.ChatbotIntent;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.common.utils.ChatbotMessageParser;
import com.finalProject.BookingMeetingRoom.model.entity.Amenity;
import com.finalProject.BookingMeetingRoom.model.entity.Building;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.RoomImage;
import com.finalProject.BookingMeetingRoom.model.request.ChatbotMessageRequest;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotMessageResponse;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotMenuOptionResponse;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotRoomItemResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomDetailResponse;
import com.finalProject.BookingMeetingRoom.repository.BuildingRepository;
import com.finalProject.BookingMeetingRoom.repository.FloorRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomImageRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.ChatHistoryService;
import com.finalProject.BookingMeetingRoom.service.ChatbotLlmService;
import com.finalProject.BookingMeetingRoom.service.ChatbotService;
import com.finalProject.BookingMeetingRoom.service.ReservationService;
import com.finalProject.BookingMeetingRoom.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(ReservationStatus.RESERVED, ReservationStatus.IN_USE);

    private final RoomRepository roomRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final RoomImageRepository roomImageRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final ChatbotRoomSuggestionEngine suggestionEngine;
    private final UserRepository userRepository;
    private final ChatHistoryService chatHistoryService;
    private final ChatbotLlmService chatbotLlmService;
    private final RoomService roomService;

    private final ChatbotMessageParser parser = new ChatbotMessageParser();

    @Override
    public ChatbotMessageResponse handleMessage(ChatbotMessageRequest request, Authentication authentication) {
        try {
            String message = request != null ? request.getMessage() : null;
            String sessionId = chatHistoryService.ensureSessionId(request != null ? request.getSessionId() : null);

            // History is disabled: do not load or store prior messages for context.
            List<String> recentUserMessages = List.of();

            var user = (authentication != null)
                    ? userRepository.findByEmail(authentication.getName()).orElse(null)
                    : null;

            if (message == null || message.isBlank()) {
                ChatbotMessageResponse res = buildMenuResponse(message);
                res.setSessionId(sessionId);
                return res;
            }

            ChatbotMessageParser.ParseResult parsed = parser.parse(message);
            ChatbotIntent menuIntent = detectMenuIntent(message);
            if (menuIntent == null && parsed.intent() == ChatbotIntent.FALLBACK) {
                menuIntent = detectMenuIntentFromHistory(recentUserMessages);
            }

            ChatbotMessageParser.ParseResult mergedCurrent = menuIntent != null
                    ? overrideIntent(parsed, menuIntent)
                    : parsed;

            ChatbotMessageParser.ParseResult effectiveParsed = mergeWithContext(mergedCurrent, recentUserMessages);

            ChatbotMessageResponse response;
            try {
                response = switch (effectiveParsed.intent()) {
                    case CHECK_AVAILABLE_ROOMS_TODAY -> handleAvailableRoomsToday(message, effectiveParsed);
                    case SUGGEST_ROOMS_BY_CAPACITY -> handleSuggestRoomsByCapacity(message, effectiveParsed);
                    case BOOK_ROOM -> handleBookRoom(message, effectiveParsed, authentication);
                    case CANCEL_RESERVATION -> handleCancelReservation(message, effectiveParsed, recentUserMessages, authentication);
                    case EXTEND_RESERVATION -> handleExtendReservation(message, effectiveParsed, recentUserMessages, authentication);
                    case RETURN_ROOM -> handleReturnRoom(message, effectiveParsed, recentUserMessages, authentication);
                    case VIEW_FACILITY_DETAILS -> handleFacilityDetails(message, effectiveParsed);
                    case LOOKUP -> handleLookup(message, effectiveParsed);
                    default -> handleFallback(message);
                };
            } catch (CustomException e) {
                if (e.getResponseCode() == ResponseCode.BOOKING_FUNCTION_LOCKED) {
                    response = toBookingLockedResponse(message, effectiveParsed.intent(), e.getData());
                } else {
                    response = toBusinessErrorResponse(message, effectiveParsed.intent(), e);
                }
            }

            if (response != null) {
                response.setSessionId(sessionId);
            }
            return response;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected chatbot error", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    private ChatbotMessageParser.ParseResult mergeRuleWithLlm(
            ChatbotMessageParser.ParseResult ruleParsed,
            ChatbotMessageParser.ParseResult llmParsed
    ) {
        if (ruleParsed == null) return llmParsed;
        if (llmParsed == null) return ruleParsed;

        ChatbotIntent intent = chooseBalancedIntent(ruleParsed, llmParsed);

        String roomCode = ruleParsed.roomCode() != null ? ruleParsed.roomCode() : llmParsed.roomCode();
        LocalDate date = ruleParsed.date() != null ? ruleParsed.date() : llmParsed.date();
        LocalTime startTime = ruleParsed.startTime() != null ? ruleParsed.startTime() : llmParsed.startTime();
        LocalTime endTime = ruleParsed.endTime() != null ? ruleParsed.endTime() : llmParsed.endTime();
        Integer minCapacity = ruleParsed.minCapacity() != null ? ruleParsed.minCapacity() : llmParsed.minCapacity();

        return new ChatbotMessageParser.ParseResult(
                intent,
                ruleParsed.normalizedMessage(),
                roomCode,
                date,
                startTime,
                endTime,
                ruleParsed.endTimeDefaulted(),
                minCapacity
        );
    }

    private ChatbotIntent chooseBalancedIntent(
            ChatbotMessageParser.ParseResult ruleParsed,
            ChatbotMessageParser.ParseResult llmParsed
    ) {
        ChatbotIntent ruleIntent = ruleParsed.intent();
        ChatbotIntent llmIntent = llmParsed.intent();

        if (llmIntent == null || llmIntent == ChatbotIntent.FALLBACK) {
            return ruleIntent;
        }

        if (ruleIntent == null || ruleIntent == ChatbotIntent.FALLBACK) {
            return llmIntent;
        }

        if (ruleIntent == llmIntent) {
            return ruleIntent;
        }

        int ruleSignals = countSignals(ruleParsed);
        int llmSignals = countSignals(llmParsed);
        boolean hasBookingHints = hasBookingHints(ruleParsed.normalizedMessage());

        // Balanced policy:
        // - keep rule when it has enough structure
        // - allow LLM override when rule is weak and LLM has richer extracted slots
        if (ruleSignals <= 1 && llmSignals >= 2) {
            return llmIntent;
        }

        // Allow safer upgrade to booking when user wording suggests booking but rule picked non-booking intent.
        if (llmIntent == ChatbotIntent.BOOK_ROOM
                && hasBookingHints
                && llmSignals >= Math.max(2, ruleSignals)) {
            return llmIntent;
        }

        // If LLM confidently detects facility details and the user message contains facility cues,
        // prefer LLM for abstract queries where rule intent may drift.
        if (llmIntent == ChatbotIntent.VIEW_FACILITY_DETAILS
                && hasFacilityHints(ruleParsed.normalizedMessage())
                && ruleSignals == 0) {
            return llmIntent;
        }

        return ruleIntent;
    }

    private int countSignals(ChatbotMessageParser.ParseResult parsed) {
        int count = 0;
        if (parsed.roomCode() != null && !parsed.roomCode().isBlank()) count++;
        if (parsed.date() != null) count++;
        if (parsed.startTime() != null) count++;
        if (parsed.endTime() != null) count++;
        if (parsed.minCapacity() != null) count++;
        return count;
    }

    private boolean hasBookingHints(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) return false;
        return normalizedMessage.contains("book")
                || normalizedMessage.contains("reserve")
                || normalizedMessage.contains("đặt")
                || normalizedMessage.contains("dat")
                || normalizedMessage.contains("mượn")
                || normalizedMessage.contains("muon")
                || normalizedMessage.contains("giữ")
                || normalizedMessage.contains("giu");
    }

    private boolean hasFacilityHints(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isBlank()) return false;
        String folded = foldText(normalizedMessage);
        return containsAnyEither(normalizedMessage, folded,
                "chi tiết", "chi tiet", "thông tin", "thong tin", "detail", "details", "info",
                "tòa", "toà", "toa", "building", "tầng", "tang", "floor", "phòng", "phong", "room",
                "status", "trạng thái", "trang thai", "capacity", "sức chứa", "suc chua");
    }

    private ChatbotMessageParser.ParseResult mergeWithContext(ChatbotMessageParser.ParseResult current, List<String> recentUserMessages) {
        if (current == null) return null;
        if (recentUserMessages == null || recentUserMessages.isEmpty()) return current;

        ChatbotIntent resolvedIntent = current.intent();
        String roomCode = current.roomCode();
        LocalDate date = current.date();
        LocalTime startTime = current.startTime();
        LocalTime endTime = current.endTime();
        boolean endTimeDefaulted = current.endTimeDefaulted();
        Integer minCapacity = current.minCapacity();

        // Parse context messages from newest to oldest.
        for (String ctx : recentUserMessages) {
            if (ctx == null || ctx.isBlank()) continue;
            var parsed = parser.parse(ctx);

            if (resolvedIntent == ChatbotIntent.FALLBACK && parsed.intent() != ChatbotIntent.FALLBACK) {
                resolvedIntent = parsed.intent();
            }

            if (roomCode == null && parsed.roomCode() != null) roomCode = parsed.roomCode();
            if (date == null && parsed.date() != null) date = parsed.date();
            if (startTime == null && parsed.startTime() != null) startTime = parsed.startTime();
            if (endTime == null && parsed.endTime() != null) {
                endTime = parsed.endTime();
                endTimeDefaulted = parsed.endTimeDefaulted();
            }
            if (minCapacity == null && parsed.minCapacity() != null) minCapacity = parsed.minCapacity();
        }

        return new ChatbotMessageParser.ParseResult(
                resolvedIntent,
                current.normalizedMessage(),
                roomCode,
                date,
                startTime,
                endTime,
                endTimeDefaulted,
                minCapacity
        );
    }

    private ChatbotMessageResponse handleFallback(String message) {
        return buildMenuResponse(message);
    }

    private ChatbotMessageResponse buildMenuResponse(String message) {
        boolean vi = isVietnameseMessage(message);
        String reply = vi
                ? "Vui lòng chọn chức năng: (1) Đặt phòng, (2) Hủy phòng, (3) Thêm giờ, (4) Tra cứu."
                : "Please choose a function: (1) Book room, (2) Cancel room, (3) Extend time, (4) Lookup.";

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.FALLBACK)
                .reply(reply)
                .menuOptions(buildMenuOptions(vi))
                .build();
    }

    private List<ChatbotMenuOptionResponse> buildMenuOptions(boolean vi) {
        return List.of(
                ChatbotMenuOptionResponse.builder()
                        .code("1")
                        .label(vi ? "Đặt phòng" : "Book room")
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .build(),
                ChatbotMenuOptionResponse.builder()
                        .code("2")
                        .label(vi ? "Hủy phòng" : "Cancel room")
                        .intent(ChatbotIntent.CANCEL_RESERVATION)
                        .build(),
                ChatbotMenuOptionResponse.builder()
                        .code("3")
                        .label(vi ? "Thêm giờ" : "Extend time")
                        .intent(ChatbotIntent.EXTEND_RESERVATION)
                        .build(),
                ChatbotMenuOptionResponse.builder()
                        .code("4")
                        .label(vi ? "Tra cứu" : "Lookup")
                        .intent(ChatbotIntent.LOOKUP)
                        .build()
        );
    }

    private ChatbotIntent detectMenuIntentFromHistory(List<String> recentUserMessages) {
        if (recentUserMessages == null || recentUserMessages.isEmpty()) return null;
        for (String msg : recentUserMessages) {
            ChatbotIntent intent = detectMenuIntent(msg);
            if (intent != null) return intent;
        }
        return null;
    }

    private ChatbotIntent detectMenuIntent(String message) {
        if (message == null || message.isBlank()) return null;
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        String folded = foldText(normalized);

        String compact = folded.replaceAll("\\s+", " ").trim();
        if (compact.matches("^[1-4][).]?$")) {
            return mapMenuNumber(compact.substring(0, 1));
        }

        if (compact.matches("^(chon|choose|option|menu|lua chon)\\s*[1-4]$")
                || compact.matches("^[1-4]\\s*(chon|choose|option|menu|lua chon)$")) {
            String digit = compact.replaceAll("[^1-4]", "");
            return mapMenuNumber(digit);
        }

        if (containsAnyEither(normalized, folded,
                "đặt phòng", "dat phong", "book room", "booking", "reserve room")) {
            return ChatbotIntent.BOOK_ROOM;
        }

        if (containsAnyEither(normalized, folded,
                "hủy phòng", "huy phong", "cancel", "abort", "huỷ phòng")) {
            return ChatbotIntent.CANCEL_RESERVATION;
        }

        if (containsAnyEither(normalized, folded,
                "thêm giờ", "them gio", "gia hạn", "gia han", "extend", "add hour", "extra hour")) {
            return ChatbotIntent.EXTEND_RESERVATION;
        }

        if (containsAnyEither(normalized, folded,
                "tra cứu", "tra cuu", "tra cứu", "lookup", "search", "find", "kiem tra", "check")) {
            return ChatbotIntent.LOOKUP;
        }

        return null;
    }

    private ChatbotIntent mapMenuNumber(String digit) {
        if (digit == null || digit.isBlank()) return null;
        return switch (digit) {
            case "1" -> ChatbotIntent.BOOK_ROOM;
            case "2" -> ChatbotIntent.CANCEL_RESERVATION;
            case "3" -> ChatbotIntent.EXTEND_RESERVATION;
            case "4" -> ChatbotIntent.LOOKUP;
            default -> null;
        };
    }

    private ChatbotMessageParser.ParseResult overrideIntent(ChatbotMessageParser.ParseResult parsed, ChatbotIntent intent) {
        if (parsed == null) {
            return new ChatbotMessageParser.ParseResult(intent, "", null, null, null, null, false, null);
        }

        return new ChatbotMessageParser.ParseResult(
                intent,
                parsed.normalizedMessage(),
                parsed.roomCode(),
                parsed.date(),
                parsed.startTime(),
                parsed.endTime(),
                parsed.endTimeDefaulted(),
                parsed.minCapacity()
        );
    }

    private ChatbotMessageResponse toBookingLockedResponse(String message, ChatbotIntent intent, Object data) {
        boolean vi = isVietnameseMessage(message);
        String unlockAt = "";
        if (data instanceof LocalDateTime dt) {
            unlockAt = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }

        String reply;
        if (vi) {
            reply = unlockAt.isBlank()
                    ? "Chức năng đặt phòng hiện đang bị khóa do vượt quá số lần hủy cho phép. Vui lòng thử lại sau."
                    : "Chức năng đặt phòng hiện đang bị khóa do vượt quá số lần hủy cho phép. Thời gian mở khóa dự kiến: " + unlockAt + ".";
        } else {
            reply = unlockAt.isBlank()
                    ? "Booking function is locked due to too many cancellation attempts. Please try again later."
                    : "Booking function is locked due to too many cancellation attempts. Expected unlock time: " + unlockAt + ".";
        }

        return ChatbotMessageResponse.builder()
                .intent(intent != null ? intent : ChatbotIntent.FALLBACK)
                .reply(reply)
                .build();
    }

    private ChatbotMessageResponse toBusinessErrorResponse(String message, ChatbotIntent intent, CustomException e) {
        String businessMessage = (e.getData() instanceof String s && !s.isBlank())
                ? s
                : (e.getResponseCode() != null ? e.getResponseCode().getMessage() : e.getMessage());

        if (businessMessage == null || businessMessage.isBlank()) {
            businessMessage = isVietnameseMessage(message)
                    ? "Đã xảy ra lỗi khi xử lý yêu cầu. Vui lòng thử lại."
                    : "An error occurred while processing your request. Please try again.";
        }

        return ChatbotMessageResponse.builder()
                .intent(intent != null ? intent : ChatbotIntent.FALLBACK)
                .reply(businessMessage)
                .build();
    }

    private ChatbotMessageResponse handleLookup(String message, ChatbotMessageParser.ParseResult parsed) {
        ChatbotIntent lookupIntent = resolveLookupIntent(message, parsed);

        return switch (lookupIntent) {
            case CHECK_AVAILABLE_ROOMS_TODAY -> handleAvailableRoomsToday(message, parsed);
            case SUGGEST_ROOMS_BY_CAPACITY -> handleSuggestRoomsByCapacity(message, parsed);
            case VIEW_FACILITY_DETAILS -> handleFacilityDetails(message, parsed);
            default -> buildLookupPrompt(message);
        };
    }

    private ChatbotMessageResponse buildLookupPrompt(String message) {
        boolean vi = isVietnameseMessage(message);
        String reply = vi
                ? "Bạn muốn tra cứu phòng trống, gợi ý theo sức chứa, hay xem chi tiết tòa/tầng/phòng?"
                : "What would you like to look up: available rooms, capacity suggestions, or facility details?";

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.LOOKUP)
                .reply(reply)
                .menuOptions(buildMenuOptions(vi))
                .build();
    }

    private ChatbotIntent resolveLookupIntent(String message, ChatbotMessageParser.ParseResult parsed) {
        if (parsed != null) {
            ChatbotIntent current = parsed.intent();
            if (current == ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY
                    || current == ChatbotIntent.SUGGEST_ROOMS_BY_CAPACITY
                    || current == ChatbotIntent.VIEW_FACILITY_DETAILS) {
                return current;
            }

            if (parsed.minCapacity() != null && parsed.minCapacity() > 0) {
                return ChatbotIntent.SUGGEST_ROOMS_BY_CAPACITY;
            }

            if (parsed.roomCode() != null && !parsed.roomCode().isBlank()
                    && parsed.startTime() == null && parsed.endTime() == null) {
                return ChatbotIntent.VIEW_FACILITY_DETAILS;
            }

            if (parsed.startTime() != null || parsed.endTime() != null) {
                return ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY;
            }
        }

        String normalized = parsed != null && parsed.normalizedMessage() != null
                ? parsed.normalizedMessage()
                : Objects.toString(message, "").trim().toLowerCase(Locale.ROOT);
        String folded = foldText(normalized);

        if (containsAnyEither(normalized, folded,
                "phòng trống", "phong trong", "available", "free", "còn phòng", "con phong", "trống", "trong")) {
            return ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY;
        }

        if (containsAnyEither(normalized, folded,
                "chi tiết", "chi tiet", "thông tin", "thong tin", "detail", "details", "info")) {
            return ChatbotIntent.VIEW_FACILITY_DETAILS;
        }

        return ChatbotIntent.FALLBACK;
    }

    private ChatbotMessageResponse handleCancelReservation(
            String message,
            ChatbotMessageParser.ParseResult parsed,
            List<String> recentUserMessages,
            Authentication authentication
    ) {
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        boolean vi = isVietnameseMessage(message);
        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

        String contextualRoomCode = resolveContextualRoomCode(parsed, recentUserMessages);
        Reservation target = findTargetReservationForAction(user.getId(), contextualRoomCode);
        if (target == null) {
            String reply = vi
                    ? "Mình chưa tìm thấy đặt phòng đang hoạt động để hủy. Bạn có thể cung cấp mã phòng (ví dụ: V5-020) hoặc kiểm tra lại lịch đặt phòng."
                    : "I couldn't find an active reservation to cancel. Please provide a room code (e.g. V5-020) or check your current bookings.";
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.CANCEL_RESERVATION)
                    .reply(reply)
                    .build();
        }

        String reason = extractCancelReason(message, vi);
        reservationService.cancelReservation(target.getId(), reason, authentication);

        String roomCode = target.getRoom() != null ? target.getRoom().getLocationCode() : "";
        String reply = vi
                ? "Đã hủy đặt phòng " + roomCode + " thành công."
                : "Successfully cancelled reservation for room " + roomCode + ".";

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.CANCEL_RESERVATION)
                .reply(reply)
                .build();
    }

    private ChatbotMessageResponse handleExtendReservation(
            String message,
            ChatbotMessageParser.ParseResult parsed,
            List<String> recentUserMessages,
            Authentication authentication
    ) {
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        boolean vi = isVietnameseMessage(message);
        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

        String contextualRoomCode = resolveContextualRoomCode(parsed, recentUserMessages);
        Reservation target = findTargetReservationForAction(user.getId(), contextualRoomCode);
        if (target == null) {
            String reply = vi
                    ? "Mình chưa tìm thấy đặt phòng đang hoạt động để gia hạn. Bạn có thể cung cấp mã phòng hoặc nói 'thêm 1 giờ cho phòng V5-020'."
                    : "I couldn't find an active reservation to extend. You can provide a room code, for example: 'extend 1 hour for room V5-020'.";
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.EXTEND_RESERVATION)
                    .reply(reply)
                    .build();
        }

        double hour = extractExtendHours(message);
        reservationService.extendReservation(target.getId(), hour, authentication);

        Reservation updated = reservationRepository.findById(target.getId()).orElse(target);

        String roomCode = updated.getRoom() != null ? updated.getRoom().getLocationCode() : "";
        String hourText = (Math.floor(hour) == hour) ? String.valueOf((int) hour) : String.valueOf(hour);
        String updatedWindow = formatReservationWindow(updated);
        String reply = vi
                ? "Đã gia hạn thêm " + hourText + " giờ cho phòng " + roomCode + " thành công."
                + (updatedWindow.isBlank() ? "" : " Khung giờ mới: " + updatedWindow + ".")
                : "Successfully extended room " + roomCode + " by " + hourText + " hour(s)."
                + (updatedWindow.isBlank() ? "" : " New time window: " + updatedWindow + ".");

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.EXTEND_RESERVATION)
                .reply(reply)
                .build();
    }

    private ChatbotMessageResponse handleReturnRoom(
            String message,
            ChatbotMessageParser.ParseResult parsed,
            List<String> recentUserMessages,
            Authentication authentication
    ) {
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        boolean vi = isVietnameseMessage(message);
        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

        String contextualRoomCode = resolveContextualRoomCode(parsed, recentUserMessages);
        Reservation target = findTargetReservationForAction(user.getId(), contextualRoomCode);
        if (target == null) {
            String reply = vi
                    ? "Mình chưa tìm thấy đặt phòng đang hoạt động để trả phòng. Bạn có thể cung cấp mã phòng hoặc kiểm tra lại lịch đặt phòng."
                    : "I couldn't find an active reservation to return. Please provide a room code (e.g. V5-020) or check your current bookings.";
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.RETURN_ROOM)
                    .reply(reply)
                    .build();
        }

        reservationService.returnRoom(target.getId(), authentication);

        String roomCode = target.getRoom() != null ? target.getRoom().getLocationCode() : "";
        String reply = vi
                ? "Đã trả phòng " + roomCode + " thành công."
                : "Successfully returned room " + roomCode + ".";

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.RETURN_ROOM)
                .reply(reply)
                .build();
    }

    private String resolveContextualRoomCode(ChatbotMessageParser.ParseResult parsed, List<String> recentUserMessages) {
        if (parsed != null && parsed.roomCode() != null && !parsed.roomCode().isBlank()) {
            return parsed.roomCode();
        }

        if (recentUserMessages == null || recentUserMessages.isEmpty()) {
            return null;
        }

        for (String msg : recentUserMessages) {
            if (msg == null || msg.isBlank()) continue;
            var p = parser.parse(msg);
            if (p.roomCode() != null && !p.roomCode().isBlank()) {
                return p.roomCode();
            }
        }
        return null;
    }

    private Reservation findTargetReservationForAction(String userId, String roomCode) {
        List<Reservation> candidates = List.of();
        if (roomCode != null && !roomCode.isBlank()) {
            candidates = reservationRepository.findActiveReservationsOfUserByRoomCode(userId, roomCode, ACTIVE_RESERVATION_STATUSES);
        }

        // Fallback to most recently active reservation of user when room code is missing or not found.
        if (candidates == null || candidates.isEmpty()) {
            candidates = reservationRepository.findActiveReservationsOfUser(userId, ACTIVE_RESERVATION_STATUSES);
        }

        return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
    }

    private String formatReservationWindow(Reservation reservation) {
        if (reservation == null || reservation.getStartTime() == null || reservation.getEndTime() == null) return "";
        return reservation.getStartTime().toLocalDate() + " "
                + reservation.getStartTime().toLocalTime().format(TIME_FMT)
                + " - "
                + reservation.getEndTime().toLocalTime().format(TIME_FMT);
    }

    private double extractExtendHours(String message) {
        String normalized = Objects.toString(message, "").toLowerCase(Locale.ROOT);

        // VI: "thêm X giờ/tiếng" hoặc "gia hạn X giờ"
        Pattern viPattern = Pattern.compile("(?:thêm|them|gia hạn|gia han|kéo dài|keo dai)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:giờ|gio|tiếng|tien)", Pattern.CASE_INSENSITIVE);
        Matcher viMatcher = viPattern.matcher(normalized);
        if (viMatcher.find()) {
            return parseHourOrDefault(viMatcher.group(1), 1.0);
        }

        // VI: "lên X tiếng/giờ"
        Pattern viUptoPattern = Pattern.compile("(?:lên|len)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:giờ|gio|tiếng|tien)", Pattern.CASE_INSENSITIVE);
        Matcher viUptoMatcher = viUptoPattern.matcher(normalized);
        if (viUptoMatcher.find()) {
            return parseHourOrDefault(viUptoMatcher.group(1), 1.0);
        }

        // EN: "extend/add X hours" hoặc "by X hours"
        Pattern enPattern = Pattern.compile("(?:extend|add|extra|more|by)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:hour|hours|hr|hrs)", Pattern.CASE_INSENSITIVE);
        Matcher enMatcher = enPattern.matcher(normalized);
        if (enMatcher.find()) {
            return parseHourOrDefault(enMatcher.group(1), 1.0);
        }

        // EN: "to/up to X hours"
        Pattern enUptPattern = Pattern.compile("(?:to|up\\s*to)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:hour|hours|hr|hrs)", Pattern.CASE_INSENSITIVE);
        Matcher enUptMatcher = enUptPattern.matcher(normalized);
        if (enUptMatcher.find()) {
            return parseHourOrDefault(enUptMatcher.group(1), 1.0);
        }

        Pattern trailingPattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:hour|hours|hr|hrs|giờ|gio|tiếng|tien)", Pattern.CASE_INSENSITIVE);
        Matcher trailingMatcher = trailingPattern.matcher(normalized);
        if (trailingMatcher.find()) {
            return parseHourOrDefault(trailingMatcher.group(1), 1.0);
        }

        return 1.0;
    }

    private double parseHourOrDefault(String raw, double defaultValue) {
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String extractCancelReason(String message, boolean vi) {
        String normalized = Objects.toString(message, "");
        Pattern p = Pattern.compile("(?:because|due to|vì|vi|do)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(normalized);
        if (m.find()) {
            String reason = m.group(1) != null ? m.group(1).trim() : "";
            if (!reason.isBlank()) return reason;
        }
        return vi ? "Hủy qua chatbot" : "Cancelled via chatbot";
    }

    @Transactional(readOnly = true)
    private ChatbotMessageResponse handleFacilityDetails(String message, ChatbotMessageParser.ParseResult parsed) {
        boolean vi = isVietnameseMessage(message);
        String normalized = parsed != null && parsed.normalizedMessage() != null
                ? parsed.normalizedMessage()
                : (message == null ? "" : message.trim().toLowerCase(Locale.ROOT));
        String folded = foldText(normalized);

        List<Building> buildings = buildingRepository.findAll().stream()
                .filter(b -> b != null && !b.isDeleted())
                .toList();
        List<Floor> floors = floorRepository.findAll().stream()
                .filter(f -> f != null && !f.isDeleted())
                .toList();
        List<Room> rooms = roomRepository.findAllWithDetails().stream()
                .filter(Objects::nonNull)
                .filter(r -> r.getFloor() == null || !r.getFloor().isDeleted())
                .filter(r -> r.getFloor() == null || r.getFloor().getBuilding() == null || !r.getFloor().getBuilding().isDeleted())
                .toList();

        Room room = resolveRoom(parsed, normalized, folded, rooms);
        Floor floor = resolveFloor(normalized, folded, floors);
        Building building = resolveBuilding(normalized, folded, buildings);

        if (room != null) {
            String amenities = room.getAmenities() == null || room.getAmenities().isEmpty()
                    ? (vi ? "không có" : "none")
                    : room.getAmenities().stream()
                    .map(Amenity::getName)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .limit(8)
                    .collect(Collectors.joining(", "));

            String buildingName = room.getFloor() != null && room.getFloor().getBuilding() != null
                    ? room.getFloor().getBuilding().getName()
                    : (vi ? "không xác định" : "unknown");
            String floorName = room.getFloor() != null
                    ? room.getFloor().getName()
                    : (vi ? "không xác định" : "unknown");
            String status = room.getStatus() != null ? room.getStatus().name() : "UNKNOWN";
            String capacity = room.getCapacity() != null
                    ? String.valueOf(room.getCapacity())
                    : (vi ? "không xác định" : "unknown");

            String reply = vi
                    ? "Chi tiết phòng " + room.getLocationCode() + ": tòa " + buildingName + ", tầng " + floorName
                    + ", trạng thái " + status + ", sức chứa " + capacity + ", tiện ích: " + amenities + "."
                    : "Room " + room.getLocationCode() + " details: building " + buildingName + ", floor " + floorName
                    + ", status " + status + ", capacity " + capacity + ", amenities: " + amenities + ".";

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.VIEW_FACILITY_DETAILS)
                    .reply(reply)
                    .roomDetail(toRoomDetailSafely(room))
                    .build();
        }

        boolean floorRequested = containsAnyEither(normalized, folded, "tầng", "tang", "floor") || floor != null;
        if (floorRequested) {
            if (floor != null) {
                List<Room> floorRooms = rooms.stream()
                        .filter(r -> r.getFloor() != null && Objects.equals(r.getFloor().getId(), floor.getId()))
                        .toList();
                long totalRooms = floorRooms.size();
                long availableRooms = floorRooms.stream().filter(r -> r.getStatus() == RoomStatus.AVAILABLE).count();
                long unavailableRooms = floorRooms.stream().filter(r -> r.getStatus() == RoomStatus.UNAVAILABLE).count();
                long brokenRooms = floorRooms.stream().filter(r -> r.getStatus() == RoomStatus.BROKEN).count();
                String buildingName = floor.getBuilding() != null
                        ? floor.getBuilding().getName()
                        : (vi ? "không xác định" : "unknown");

                String reply = vi
                        ? "Chi tiết tầng " + floor.getName() + ": thuộc tòa " + buildingName
                        + ", tổng số phòng " + totalRooms
                        + ", đang trống " + availableRooms
                        + ", đang sử dụng " + unavailableRooms
                        + ", hỏng " + brokenRooms + "."
                        : "Floor " + floor.getName() + " details: building " + buildingName
                        + ", total rooms " + totalRooms
                        + ", available " + availableRooms
                        + ", unavailable " + unavailableRooms
                        + ", broken " + brokenRooms + ".";

                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.VIEW_FACILITY_DETAILS)
                        .reply(reply)
                        .build();
            }

            String availableFloors = floors.stream()
                    .map(Floor::getName)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.isBlank())
                    .limit(8)
                    .collect(Collectors.joining(", "));

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.VIEW_FACILITY_DETAILS)
                    .reply(vi
                            ? (availableFloors.isBlank()
                            ? "Mình không tìm thấy thông tin tầng trong hệ thống."
                            : "Mình chưa xác định được bạn muốn xem tầng nào. Một số tầng hiện có: " + availableFloors + ".")
                            : (availableFloors.isBlank()
                            ? "I couldn't find floor information in the database."
                            : "I couldn't determine which floor you mean. Available floors include: " + availableFloors + "."))
                    .build();
        }

        boolean buildingRequested = containsAnyEither(normalized, folded, "tòa", "toà", "toa", "building") || building != null;
        if (buildingRequested) {
            if (building != null) {
                List<Floor> buildingFloors = floors.stream()
                        .filter(f -> f.getBuilding() != null && Objects.equals(f.getBuilding().getId(), building.getId()))
                        .toList();
                Set<String> floorIds = buildingFloors.stream().map(Floor::getId).filter(Objects::nonNull).collect(Collectors.toSet());
                long totalRooms = rooms.stream()
                        .filter(r -> r.getFloor() != null && floorIds.contains(r.getFloor().getId()))
                        .count();

                String reply = vi
                        ? "Chi tiết tòa " + building.getName() + ": địa chỉ " + Objects.toString(building.getAddress(), "không xác định")
                        + ", tổng số tầng " + buildingFloors.size() + ", tổng số phòng " + totalRooms + "."
                        : "Building " + building.getName() + " details: address " + Objects.toString(building.getAddress(), "unknown")
                        + ", total floors " + buildingFloors.size() + ", total rooms " + totalRooms + ".";

                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.VIEW_FACILITY_DETAILS)
                        .reply(reply)
                        .build();
            }

            String topBuildings = buildings.stream()
                    .map(Building::getName)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.isBlank())
                    .limit(8)
                    .collect(Collectors.joining(", "));

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.VIEW_FACILITY_DETAILS)
                    .reply(vi
                            ? (topBuildings.isBlank()
                            ? "Mình không tìm thấy thông tin tòa nhà trong hệ thống."
                            : "Mình chưa xác định được bạn muốn xem tòa nào. Các tòa hiện có: " + topBuildings + ".")
                            : (topBuildings.isBlank()
                            ? "I couldn't find building details in the database."
                            : "I couldn't determine which building you mean. Available buildings include: " + topBuildings + "."))
                    .build();
        }

        String sample = vi
                ? "Ví dụ: 'Xem chi tiết phòng AL-102' hoặc 'Chi tiết tòa A'."
                : "For example: 'Show details of room AL-102' or 'Details of building A'.";
        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.VIEW_FACILITY_DETAILS)
                .reply(vi
                        ? "Vui lòng cho biết bạn muốn xem chi tiết tòa, tầng hay phòng. " + sample
                        : "Please specify whether you want building, floor, or room details. " + sample)
                .build();
    }

    private Room resolveRoom(ChatbotMessageParser.ParseResult parsed, String normalized, String folded, List<Room> rooms) {
        if (parsed != null && parsed.roomCode() != null && !parsed.roomCode().isBlank()) {
            return rooms.stream()
                    .filter(r -> r.getLocationCode() != null && r.getLocationCode().equalsIgnoreCase(parsed.roomCode()))
                    .findFirst()
                    .orElse(null);
        }

        return rooms.stream()
                .filter(r -> r.getLocationCode() != null)
                .filter(r -> {
                    String code = r.getLocationCode().toLowerCase(Locale.ROOT);
                    String codeFolded = foldText(code);
                    return normalized.contains(code) || folded.contains(codeFolded) || normalized.contains(code.replace("-", " "));
                })
                .findFirst()
                .orElse(null);
    }

    private RoomDetailResponse toRoomDetailSafely(Room room) {
        if (room == null || room.getId() == null) return null;
        try {
            return roomService.getRoomDetail(room.getId());
        } catch (Exception e) {
            log.warn("Failed to build room detail payload for chatbot room {}", room.getId(), e);
            return null;
        }
    }

    private Floor resolveFloor(String normalized, String folded, List<Floor> floors) {
        return floors.stream()
                .filter(f -> f.getName() != null && !f.getName().isBlank())
                .filter(f -> {
                    String name = f.getName().toLowerCase(Locale.ROOT);
                    String foldedName = foldText(name);
                    return normalized.contains(name) || folded.contains(foldedName);
                })
                .findFirst()
                .orElse(null);
    }

    private Building resolveBuilding(String normalized, String folded, List<Building> buildings) {
        return buildings.stream()
                .filter(b -> b.getName() != null && !b.getName().isBlank())
                .filter(b -> {
                    String name = b.getName().toLowerCase(Locale.ROOT);
                    String foldedName = foldText(name);
                    String address = Objects.toString(b.getAddress(), "").toLowerCase(Locale.ROOT);
                    String foldedAddress = foldText(address);
                    return normalized.contains(name)
                            || folded.contains(foldedName)
                            || (!address.isBlank() && (normalized.contains(address) || folded.contains(foldedAddress)));
                })
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    private ChatbotMessageResponse handleSuggestRoomsByCapacity(String message, ChatbotMessageParser.ParseResult parsed) {
        Integer min = parsed != null ? parsed.minCapacity() : null;
        if (min == null || min <= 0) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.SUGGEST_ROOMS_BY_CAPACITY)
                    .reply("What capacity do you need? For example: 'Suggest rooms for 20 people'.")
                    .availableRooms(List.of())
                    .build();
        }

        List<Room> rooms = roomRepository.findAllWithDetails().stream()
                .filter(r -> r.getStatus() != RoomStatus.BROKEN)
                .filter(r -> r.getFloor() == null || !r.getFloor().isDeleted())
                .filter(r -> r.getFloor() == null || r.getFloor().getBuilding() == null || !r.getFloor().getBuilding().isDeleted())
                .filter(r -> r.getCapacity() != null && r.getCapacity() >= min)
                .sorted(Comparator.comparing(Room::getCapacity, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Room::getLocationCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        if (rooms.isEmpty()) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.SUGGEST_ROOMS_BY_CAPACITY)
                    .reply("I couldn't find any rooms that can accommodate " + min + "+ people.")
                    .availableRooms(List.of())
                    .build();
        }

        List<String> roomIds = rooms.stream().map(Room::getId).filter(Objects::nonNull).toList();
        Map<String, String> roomIdToImageUrl = roomIds.isEmpty()
                ? Map.of()
                : roomImageRepository.findByRoom_IdIn(roomIds).stream()
                .filter(ri -> ri.getRoom() != null && ri.getRoom().getId() != null)
                .filter(ri -> ri.getImageUrl() != null && !ri.getImageUrl().isBlank())
                .sorted(Comparator.comparing(RoomImage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toMap(
                        ri -> ri.getRoom().getId(),
                        RoomImage::getImageUrl,
                        (a, b) -> a
                ));

        List<ChatbotRoomItemResponse> suggested = rooms.stream()
                .map(r -> toRoomItem(r, List.of(), roomIdToImageUrl.get(r.getId())))
                .toList();

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.SUGGEST_ROOMS_BY_CAPACITY)
                .reply("Here are rooms that can accommodate " + min + "+ people:")
                .availableRooms(suggested)
                .build();
    }

    @Transactional(readOnly = true)
    private ChatbotMessageResponse handleAvailableRoomsToday(String message, ChatbotMessageParser.ParseResult parsed) {
        boolean vi = isVietnameseMessage(message);
        String normalized = parsed != null && parsed.normalizedMessage() != null
                ? parsed.normalizedMessage()
                : Objects.toString(message, "").trim().toLowerCase(Locale.ROOT);
        String folded = foldText(normalized);

        LocalDate day = parsed != null && parsed.date() != null ? parsed.date() : LocalDate.now();
        String dayPhraseVi = humanizeDateVi(day);
        String dayPhraseEn = humanizeDate(day);
        LocalDateTime startOfDay = day.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        LocalDateTime now = LocalDateTime.now();
        boolean instantMode = parsed == null || parsed.startTime() == null;
        LocalDateTime requestedStart = (parsed != null && parsed.startTime() != null)
                ? LocalDateTime.of(day, parsed.startTime())
                : now;
        LocalDateTime requestedEnd = (!instantMode && parsed != null && parsed.endTime() != null)
                ? LocalDateTime.of(day, parsed.endTime())
                : requestedStart.plusHours(1);
        if (!requestedEnd.isAfter(requestedStart)) {
            requestedEnd = requestedStart.plusHours(1);
        }
        if (requestedEnd.isAfter(endOfDay)) {
            requestedEnd = endOfDay;
        }

        LocalDateTime windowStart = max(startOfDay, max(now, requestedStart));

        if (!windowStart.isBefore(endOfDay)) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY)
                    .reply(vi
                            ? ("Khoảng thời gian của " + dayPhraseVi + " đã qua, vui lòng thử ngày/giờ khác.")
                            : ("The remaining time window for " + dayPhraseEn + " is over, please try a different date/time."))
                    .availableRooms(List.of())
                    .build();
        }

        List<Building> buildings = buildingRepository.findAll().stream()
                .filter(Objects::nonNull)
                .filter(b -> !b.isDeleted())
                .toList();
        Building matchedBuilding = resolveBuilding(normalized, folded, buildings);
        boolean buildingMentioned = containsAnyEither(normalized, folded, "tòa", "toà", "toa", "building");

        if (buildingMentioned && matchedBuilding == null) {
            String topBuildings = buildings.stream()
                    .map(Building::getName)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.isBlank())
                    .limit(8)
                    .collect(Collectors.joining(", "));
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY)
                    .reply(vi
                            ? (topBuildings.isBlank()
                            ? "Mình không tìm thấy dữ liệu tòa nhà trong hệ thống."
                            : "Mình chưa xác định được tòa bạn muốn xem. Các tòa hiện có: " + topBuildings + ".")
                            : (topBuildings.isBlank()
                            ? "I couldn't find building data in the database."
                            : "I couldn't determine which building you mean. Available buildings include: " + topBuildings + "."))
                    .availableRooms(List.of())
                    .build();
        }

        List<Room> rooms = roomRepository.findAllWithDetails().stream()
                .filter(r -> r.getStatus() != RoomStatus.BROKEN)
                .filter(r -> r.getFloor() == null || !r.getFloor().isDeleted())
                .filter(r -> r.getFloor() == null || r.getFloor().getBuilding() == null || !r.getFloor().getBuilding().isDeleted())
                .filter(r -> matchedBuilding == null
                        || (r.getFloor() != null
                        && r.getFloor().getBuilding() != null
                        && Objects.equals(r.getFloor().getBuilding().getId(), matchedBuilding.getId())))
                .collect(Collectors.toList());

        List<String> roomIds = rooms.stream().map(Room::getId).filter(Objects::nonNull).toList();

        Map<String, String> roomIdToImageUrl = roomIds.isEmpty()
                ? Map.of()
                : roomImageRepository.findByRoom_IdIn(roomIds).stream()
                .filter(ri -> ri.getRoom() != null && ri.getRoom().getId() != null)
                .filter(ri -> ri.getImageUrl() != null && !ri.getImageUrl().isBlank())
                .sorted(Comparator.comparing(RoomImage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toMap(
                        ri -> ri.getRoom().getId(),
                        RoomImage::getImageUrl,
                        (a, b) -> a
                ));

        List<ReservationStatus> blocking = List.of(ReservationStatus.PENDING, ReservationStatus.RESERVED, ReservationStatus.IN_USE);
        List<Reservation> overlaps = roomIds.isEmpty()
                ? List.of()
                : reservationRepository.findOverlappingReservationsForRooms(roomIds, blocking, windowStart, endOfDay);

        LocalDateTime pointEnd = requestedStart.plusMinutes(1);
        List<Reservation> overlapsAtPoint = roomIds.isEmpty()
                ? List.of()
                : reservationRepository.findOverlappingReservationsForRooms(roomIds, blocking, requestedStart, pointEnd);
        Set<String> busyAtPoint = overlapsAtPoint.stream()
                .map(Reservation::getRoom)
                .filter(Objects::nonNull)
                .map(Room::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Reservation> overlapsRequestedWindow = roomIds.isEmpty() || instantMode
                ? List.of()
                : reservationRepository.findOverlappingReservationsForRooms(roomIds, blocking, requestedStart, requestedEnd);
        Set<String> busyInRequestedWindow = overlapsRequestedWindow.stream()
                .map(Reservation::getRoom)
                .filter(Objects::nonNull)
                .map(Room::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, List<Reservation>> reservationsByRoom = overlaps.stream()
                .collect(Collectors.groupingBy(r -> r.getRoom().getId()));

        List<ChatbotRoomItemResponse> available = new ArrayList<>();

        for (Room room : rooms) {
            if (instantMode && busyAtPoint.contains(room.getId())) {
                continue;
            }
            if (!instantMode && busyInRequestedWindow.contains(room.getId())) {
                continue;
            }
            List<Reservation> busy = reservationsByRoom.getOrDefault(room.getId(), List.of());
            List<TimeRange> freeRanges = computeFreeRanges(windowStart, endOfDay, busy);
            if (freeRanges.isEmpty()) continue;

            List<String> availableSlots = instantMode
                    ? freeRanges.stream().limit(3).map(TimeRange::format).toList()
                    : List.of(requestedStart.toLocalTime().format(TIME_FMT) + "–" + requestedEnd.toLocalTime().format(TIME_FMT));

            available.add(toRoomItem(
                    room,
                    availableSlots,
                    roomIdToImageUrl.get(room.getId())
            ));
        }

        if (available.isEmpty()) {
            String scope = matchedBuilding != null ? matchedBuilding.getName() : null;
            String reply;
            if (instantMode) {
                reply = vi
                        ? (scope == null
                        ? "Hiện tại không có phòng trống. Bạn muốn thử mốc thời gian khác không?"
                        : "Hiện tại tòa " + scope + " không có phòng trống.")
                        : (scope == null
                        ? "There are no rooms available right now. Would you like to try another time?"
                        : "There are no rooms available right now in building " + scope + ".");
            } else {
                reply = vi
                        ? (scope == null
                        ? "Mình không tìm thấy phòng trống trong khung giờ bạn hỏi (" + requestedStart.toLocalTime().format(TIME_FMT)
                        + " - " + requestedEnd.toLocalTime().format(TIME_FMT) + ") " + dayPhraseVi + "."
                        : "Mình không tìm thấy phòng trống trong khung giờ bạn hỏi (" + requestedStart.toLocalTime().format(TIME_FMT)
                        + " - " + requestedEnd.toLocalTime().format(TIME_FMT) + ") " + dayPhraseVi + " ở tòa " + scope + ".")
                        : (scope == null
                        ? "I couldn't find rooms available in the requested time window (" + requestedStart.toLocalTime().format(TIME_FMT)
                        + " - " + requestedEnd.toLocalTime().format(TIME_FMT) + ") " + dayPhraseEn + "."
                        : "I couldn't find rooms available in the requested time window (" + requestedStart.toLocalTime().format(TIME_FMT)
                        + " - " + requestedEnd.toLocalTime().format(TIME_FMT) + ") " + dayPhraseEn + " in building " + scope + ".");
            }

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY)
                    .reply(reply)
                    .availableRooms(List.of())
                    .build();
        }

        String reply;
        String scope = matchedBuilding != null ? matchedBuilding.getName() : null;
        if (!instantMode && parsed != null && parsed.startTime() != null) {
            String t = parsed.startTime().format(TIME_FMT);
            String tEnd = requestedEnd.toLocalTime().format(TIME_FMT);
            if (vi) {
                reply = scope == null
                        ? "Các phòng trống trong khung giờ " + t + " - " + tEnd + " " + dayPhraseVi + ":"
                        : "Các phòng trống trong khung giờ " + t + " - " + tEnd + " " + dayPhraseVi + " tại tòa " + scope + ":";
            } else {
                reply = scope == null
                        ? "Rooms available in the time window " + t + " - " + tEnd + " " + dayPhraseEn + ":"
                        : "Rooms available in the time window " + t + " - " + tEnd + " " + dayPhraseEn + " in building " + scope + ":";
            }
        } else if (instantMode) {
            String nowText = requestedStart.format(TIME_FMT);
            if (vi) {
                reply = scope == null
                        ? "Các phòng đang trống tại thời điểm hiện tại (" + nowText + ") :"
                        : "Các phòng đang trống tại thời điểm hiện tại (" + nowText + ") ở tòa " + scope + ":";
            } else {
                reply = scope == null
                        ? "Rooms available right now (" + nowText + "):"
                        : "Rooms available right now (" + nowText + ") in building " + scope + ":";
            }
        } else {
            if (vi) {
                reply = scope == null
                        ? "Các phòng còn khung giờ trống trong " + dayPhraseVi + ":"
                        : "Các phòng còn khung giờ trống trong " + dayPhraseVi + " tại tòa " + scope + ":";
            } else {
                reply = scope == null
                        ? "Here are rooms that still have free time in " + dayPhraseEn + ":"
                        : "Here are rooms that still have free time in " + dayPhraseEn + " in building " + scope + ":";
            }
        }

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY)
                .reply(reply)
                .availableRooms(available)
                .build();
    }

    private ChatbotMessageResponse handleBookRoom(String message, ChatbotMessageParser.ParseResult parsed, Authentication authentication) {
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        LocalDate date = parsed.date() != null ? parsed.date() : LocalDate.now();
        LocalTime start = parsed.startTime();
        LocalTime end = parsed.endTime();

        if (start == null) {
            String roomHint = (parsed.roomCode() != null && !parsed.roomCode().isBlank())
                    ? parsed.roomCode()
                    : "a room";
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("What time should I book it for? For example: 'Book " + roomHint + " at 10AM today' or 'from 14:00 to 15:00 today'.")
                    .build();
        }

        if (end == null) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("I’m missing the end time. You can say 'from 10:00 to 11:00'.")
                    .build();
        }

        LocalDateTime startTime = LocalDateTime.of(date, start);
        LocalDateTime endTime = LocalDateTime.of(date, end);

        // When end time is auto-defaulted (for single-time utterances like "23:00 today"),
        // LocalTime may wrap to 00:00 and look invalid in the same date. Normalize it first.
        if (parsed.endTimeDefaulted() && !endTime.isAfter(startTime)) {
            LocalDateTime candidate = startTime.plusHours(1);
            LocalDateTime endOfDay = startTime.toLocalDate().atTime(23, 59);
            endTime = candidate.isAfter(endOfDay) ? endOfDay : candidate;
        }

        if (!startTime.isBefore(endTime)) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("The time range looks invalid. Please make sure the end time is after the start time.")
                    .build();
        }

        if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("Bookings must be within a single day. Please choose an end time on the same date.")
                    .build();
        }

        if (startTime.isBefore(LocalDateTime.now())) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("That start time is in the past. Please choose a future time.")
                    .build();
        }

        // If user didn't provide a room code, but did provide capacity + time, auto-pick a room and reserve.
        if (parsed.roomCode() == null || parsed.roomCode().isBlank()) {
            Integer minCapacity = parsed.minCapacity();
            if (minCapacity != null && minCapacity > 0) {
                return autoReserveByCapacity(message, date, start, end, minCapacity, authentication, parsed.endTimeDefaulted());
            }

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("Which room would you like to book? Please provide a room code like 'AL-102'.")
                    .build();
        }

        Room room = roomRepository.findByLocationCodeIgnoreCase(parsed.roomCode())
                .orElse(null);

        if (room == null) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("I can't find a room with code '" + parsed.roomCode() + "'. Please double-check the room code (e.g. AL-102).")
                    .build();
        }

        ReservationRequest reservationRequest = new ReservationRequest();
        reservationRequest.setRoomId(room.getId());
        reservationRequest.setStartTime(startTime);
        reservationRequest.setEndTime(endTime);
        reservationRequest.setPurpose("Meeting");
        reservationRequest.setNote("Booked via chatbot");

        try {
            ReservationResponse reservation = reservationService.reserveRoom(reservationRequest, authentication);

            String dayPhrase = humanizeDate(date);

            String reply = parsed.endTimeDefaulted()
                    ? pickByHash(message,
                    "Done — I booked " + room.getLocationCode() + " at " + start.format(TIME_FMT) + " " + dayPhrase + " for 1 hour.",
                    "All set. " + room.getLocationCode() + " is booked from " + start.format(TIME_FMT) + " to " + end.format(TIME_FMT) + " " + dayPhrase + ".")
                    : pickByHash(message,
                    "Booked successfully. You have " + room.getLocationCode() + " from " + start.format(TIME_FMT) + " to " + end.format(TIME_FMT) + " " + dayPhrase + ".",
                    "Confirmed — " + room.getLocationCode() + " is reserved from " + start.format(TIME_FMT) + " to " + end.format(TIME_FMT) + " " + dayPhrase + ".",
                    "Great — your booking for " + room.getLocationCode() + " is confirmed (" + start.format(TIME_FMT) + "–" + end.format(TIME_FMT) + ").")
                    ;

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply(reply)
                    .reservation(reservation)
                    .build();

        } catch (CustomException e) {
            if (e.getResponseCode() == ResponseCode.CANNOT_RESERVE_ROOM
                    || e.getResponseCode() == ResponseCode.RESERVATION_TIME_OVERLAP
                    || e.getResponseCode() == ResponseCode.USER_TIME_OVERLAP) {
                List<Room> alternatives = suggestionEngine.suggest(room, startTime, endTime, 5);

                List<ChatbotRoomItemResponse> alternativeResponses = alternatives.stream()
                        .map(r -> toRoomItem(r, List.of(start.format(TIME_FMT) + "–" + end.format(TIME_FMT))))
                        .collect(Collectors.toList());

                String baseMessage = e.getResponseCode().getMessage();
                String timeSlot = start.format(TIME_FMT) + "–" + end.format(TIME_FMT);
                String intro = baseMessage + " (" + room.getLocationCode() + ", " + timeSlot + ")";

                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .reply(intro)
                        .alternativeRooms(alternativeResponses)
                        .build();
            }
            throw e;
        }
    }

    private ChatbotMessageResponse autoReserveByCapacity(
            String message,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            int minCapacity,
            Authentication authentication,
            boolean endTimeDefaulted
    ) {
        LocalDateTime startTime = LocalDateTime.of(date, start);
        LocalDateTime endTime = LocalDateTime.of(date, end);

        List<Room> candidates = roomRepository.findAllWithDetails().stream()
                .filter(r -> r.getStatus() != RoomStatus.BROKEN)
                .filter(r -> r.getFloor() == null || !r.getFloor().isDeleted())
                .filter(r -> r.getFloor() == null || r.getFloor().getBuilding() == null || !r.getFloor().getBuilding().isDeleted())
                .filter(r -> r.getCapacity() != null && r.getCapacity() >= minCapacity)
                .sorted(Comparator.comparing(Room::getCapacity, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Room::getLocationCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        if (candidates.isEmpty()) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("I couldn't find any rooms that can accommodate " + minCapacity + "+ people.")
                    .build();
        }

        List<String> roomIds = candidates.stream().map(Room::getId).filter(Objects::nonNull).toList();

        List<ReservationStatus> blocking = List.of(ReservationStatus.PENDING, ReservationStatus.RESERVED, ReservationStatus.IN_USE);
        List<Reservation> overlaps = roomIds.isEmpty()
                ? List.of()
                : reservationRepository.findOverlappingReservationsForRooms(roomIds, blocking, startTime, endTime);

        Set<String> busyRoomIds = overlaps.stream()
                .map(r -> r.getRoom() != null ? r.getRoom().getId() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        String dayPhrase = humanizeDate(date);

        for (Room room : candidates) {
            if (room.getId() == null) continue;
            if (busyRoomIds.contains(room.getId())) continue;

            ReservationRequest reservationRequest = new ReservationRequest();
            reservationRequest.setRoomId(room.getId());
            reservationRequest.setStartTime(startTime);
            reservationRequest.setEndTime(endTime);
            reservationRequest.setPurpose("Meeting");
            reservationRequest.setNote("Booked via chatbot (auto by capacity)");

            try {
                ReservationResponse reservation = reservationService.reserveRoom(reservationRequest, authentication);

                String reply = endTimeDefaulted
                        ? "Done — I booked " + room.getLocationCode() + " at " + start.format(TIME_FMT) + " " + dayPhrase + " for 1 hour (capacity " + minCapacity + "+)."
                        : "Booked successfully. You have " + room.getLocationCode() + " from " + start.format(TIME_FMT) + " to " + end.format(TIME_FMT) + " " + dayPhrase + " (capacity " + minCapacity + "+).";

                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .reply(reply)
                        .reservation(reservation)
                        .build();
            } catch (CustomException e) {
                if (e.getResponseCode() == ResponseCode.CANNOT_RESERVE_ROOM
                        || e.getResponseCode() == ResponseCode.RESERVATION_TIME_OVERLAP
                        || e.getResponseCode() == ResponseCode.USER_TIME_OVERLAP) {
                    // Race condition: room got booked by someone else. Try the next candidate.
                    continue;
                }
                throw e;
            }
        }

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.BOOK_ROOM)
                .reply("I couldn't find an available room for " + start.format(TIME_FMT) + "–" + end.format(TIME_FMT) + " " + dayPhrase + " with capacity " + minCapacity + "+.")
                .build();
    }

    private String humanizeDate(LocalDate date) {
        if (date == null) return "";
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "today";
        if (date.equals(today.plusDays(1))) return "tomorrow";
        return "on " + date;
    }

    private String humanizeDateVi(LocalDate date) {
        if (date == null) return "";
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "hôm nay";
        if (date.equals(today.plusDays(1))) return "ngày mai";
        return "ngày " + date;
    }

    private ChatbotRoomItemResponse toRoomItem(Room room, List<String> timeSlots) {
        return toRoomItem(room, timeSlots, null);
    }

    private ChatbotRoomItemResponse toRoomItem(Room room, List<String> timeSlots, String imageUrlOverride) {
        List<String> amenityNames = room.getAmenities() == null ? List.of() : room.getAmenities().stream()
                .map(Amenity::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(5)
                .toList();

        String imageUrl = imageUrlOverride;
        if (imageUrl == null) {
            if (room.getImages() != null && !room.getImages().isEmpty()) {
                imageUrl = room.getImages().stream()
                        .map(RoomImage::getImageUrl)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            }
        }

        return ChatbotRoomItemResponse.builder()
                .roomId(room.getId())
                .roomCode(room.getLocationCode())
                .building(room.getFloor() != null && room.getFloor().getBuilding() != null ? room.getFloor().getBuilding().getName() : null)
                .floor(room.getFloor() != null ? room.getFloor().getName() : null)
                .capacity(room.getCapacity())
                .amenities(amenityNames)
                .imageUrl(imageUrl)
                .availableTimeSlots(timeSlots)
                .build();
    }

    private record TimeRange(LocalDateTime start, LocalDateTime end) {
        String format() {
            return start.format(TIME_FMT) + "–" + end.format(TIME_FMT);
        }
    }

    private List<TimeRange> computeFreeRanges(LocalDateTime windowStart, LocalDateTime windowEnd, List<Reservation> busyReservations) {
        if (!windowStart.isBefore(windowEnd)) return List.of();

        List<TimeRange> busyRanges = busyReservations == null ? List.of() : busyReservations.stream()
                .filter(r -> r.getStartTime() != null && r.getEndTime() != null)
                .map(r -> new TimeRange(
                        max(windowStart, r.getStartTime()),
                        min(windowEnd, r.getEndTime())
                ))
                .filter(tr -> tr.start.isBefore(tr.end))
                .sorted(Comparator.comparing(TimeRange::start))
                .toList();

        List<TimeRange> mergedBusy = new ArrayList<>();
        for (TimeRange r : busyRanges) {
            if (mergedBusy.isEmpty()) {
                mergedBusy.add(r);
                continue;
            }
            TimeRange last = mergedBusy.get(mergedBusy.size() - 1);
            if (!r.start.isAfter(last.end)) {
                mergedBusy.set(mergedBusy.size() - 1, new TimeRange(last.start, max(last.end, r.end)));
            } else {
                mergedBusy.add(r);
            }
        }

        List<TimeRange> free = new ArrayList<>();
        LocalDateTime cursor = windowStart;
        for (TimeRange b : mergedBusy) {
            if (cursor.isBefore(b.start)) {
                free.add(new TimeRange(cursor, b.start));
            }
            cursor = max(cursor, b.end);
        }
        if (cursor.isBefore(windowEnd)) {
            free.add(new TimeRange(cursor, windowEnd));
        }

        // filter out ultra-short slots (< 10 minutes) to keep output useful
        return free.stream()
                .filter(tr -> java.time.Duration.between(tr.start, tr.end).toMinutes() >= 10)
                .collect(Collectors.toList());
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static String pickByHash(String message, String... variants) {
        if (variants == null || variants.length == 0) return "";
        int idx = Math.abs(Objects.toString(message, "").hashCode()) % variants.length;
        return variants[idx];
    }

    private boolean containsAny(String source, String... needles) {
        if (source == null || source.isBlank() || needles == null || needles.length == 0) return false;
        for (String n : needles) {
            if (n != null && !n.isBlank() && source.contains(n.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyEither(String normalized, String folded, String... needles) {
        if (needles == null || needles.length == 0) return false;
        for (String n : needles) {
            if (n == null || n.isBlank()) continue;
            String lower = n.toLowerCase(Locale.ROOT);
            String foldedNeedle = foldText(lower);
            if ((normalized != null && normalized.contains(lower))
                    || (folded != null && folded.contains(foldedNeedle))) {
                return true;
            }
        }
        return false;
    }

    private String foldText(String input) {
        if (input == null) return "";
        String lower = input.toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private boolean isVietnameseMessage(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        String folded = foldText(lower);
        boolean hasVnChar = lower.matches(".*[ăâđêôơưáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ].*");
        return hasVnChar || containsAnyEither(lower, folded,
                "hôm", "hom", "ngày", "ngay", "đặt", "dat", "phòng", "phong", "tòa", "toa", "tầng", "tang", "chi tiết", "chi tiet");
    }
}