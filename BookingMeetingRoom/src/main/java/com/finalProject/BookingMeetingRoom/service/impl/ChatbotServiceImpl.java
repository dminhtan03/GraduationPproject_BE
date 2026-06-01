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
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.RoomImage;
import com.finalProject.BookingMeetingRoom.model.request.ChatbotMessageRequest;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotMessageResponse;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotMenuOptionResponse;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotBookingItemResponse;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotRoomItemResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomDetailResponse;
import com.finalProject.BookingMeetingRoom.repository.BuildingRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES = List.of(ReservationStatus.RESERVED, ReservationStatus.IN_USE);

    private static final Map<ChatbotIntent, List<String>> MENU_KEYWORDS = Map.of(
        ChatbotIntent.BOOK_ROOM, List.of(
            "đặt phòng", "đặt"
        ),
        ChatbotIntent.CANCEL_RESERVATION, List.of(
            "hủy phòng", "huỷ phòng", "hủy đặt"
        ),
        ChatbotIntent.EXTEND_RESERVATION, List.of(
            "thêm giờ", "gia hạn"
        ),
        ChatbotIntent.LOOKUP, List.of(
            "tra cứu", "xem"
        )
    );

    private final RoomRepository roomRepository;
    private final BuildingRepository buildingRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final ChatbotRoomSuggestionEngine suggestionEngine;
    private final UserRepository userRepository;
    private final ChatHistoryService chatHistoryService;
    private final ChatbotLlmService chatbotLlmService;
    private final RoomService roomService;

    private final ChatbotMessageParser parser = new ChatbotMessageParser();
    private final Map<String, BookingFlowState> bookingFlowStates = new ConcurrentHashMap<>();
    private final Map<String, ExtendFlowState> extendFlowStates = new ConcurrentHashMap<>();
    private final Map<String, CancelFlowState> cancelFlowStates = new ConcurrentHashMap<>();
    private final Map<String, LookupFlowState> lookupFlowStates = new ConcurrentHashMap<>();
    private static final Pattern ROOM_CODE_VALIDATION_PATTERN = Pattern.compile("(?i)\\b[a-z]{1,5}\\d{0,3}\\s*[-_]\\s*\\d{1,4}\\b");

    @Override
    public ChatbotMessageResponse handleMessage(ChatbotMessageRequest request, Authentication authentication) {
        try {
            String message = request != null ? request.getMessage() : null;
            String sessionId = chatHistoryService.ensureSessionId(request != null ? request.getSessionId() : null);

            // Da tat lich su: khong doc/luu tin nhan truoc de lam ngu canh.
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
            if (resolveLookupAction(message, parsed) != LookupAction.NONE) {
                menuIntent = ChatbotIntent.LOOKUP;
            }
            if (menuIntent == null && parsed.intent() == ChatbotIntent.FALLBACK) {
                menuIntent = detectMenuIntentFromHistory(recentUserMessages);
            }

            BookingFlowState flowState = bookingFlowStates.get(sessionId);
            if (flowState != null && menuIntent != null && menuIntent != ChatbotIntent.BOOK_ROOM) {
                bookingFlowStates.remove(sessionId);
                flowState = null;
            }

            if (flowState != null) {
                ChatbotMessageResponse flowResponse = handleBookingFlow(sessionId, message, parsed, flowState, authentication);
                if (flowResponse != null) {
                    flowResponse.setSessionId(sessionId);
                    return flowResponse;
                }
            }

            ExtendFlowState extendFlowState = extendFlowStates.get(sessionId);
            if (extendFlowState != null
                    && menuIntent != null
                    && menuIntent != ChatbotIntent.EXTEND_RESERVATION
                    && extractSelectionIndex(message) == null) {
                extendFlowStates.remove(sessionId);
                extendFlowState = null;
            }

            if (extendFlowState != null) {
                ChatbotMessageResponse flowResponse = handleExtendFlow(sessionId, message, parsed, extendFlowState, authentication);
                if (flowResponse != null) {
                    flowResponse.setSessionId(sessionId);
                    return flowResponse;
                }
            }

            CancelFlowState cancelFlowState = cancelFlowStates.get(sessionId);
            if (cancelFlowState != null
                    && menuIntent != null
                    && menuIntent != ChatbotIntent.CANCEL_RESERVATION
                    && extractSelectionIndex(message) == null) {
                cancelFlowStates.remove(sessionId);
                cancelFlowState = null;
            }

            if (cancelFlowState != null) {
                ChatbotMessageResponse flowResponse = handleCancelFlow(sessionId, message, parsed, cancelFlowState, authentication);
                if (flowResponse != null) {
                    flowResponse.setSessionId(sessionId);
                    return flowResponse;
                }
            }

            LookupFlowState lookupFlowState = lookupFlowStates.get(sessionId);
            if (lookupFlowState != null && menuIntent != null && menuIntent != ChatbotIntent.LOOKUP) {
                lookupFlowStates.remove(sessionId);
                lookupFlowState = null;
            }

            if (lookupFlowState != null) {
                LookupAction action = resolveLookupAction(message, parsed);
                if (action != LookupAction.NONE) {
                    boolean shouldKeep = (lookupFlowState.step == LookupStep.ASK_ROOM_CODE && action == LookupAction.ROOM_DETAIL)
                            || (lookupFlowState.step == LookupStep.ASK_CAPACITY_RANGE && action == LookupAction.CAPACITY_RANGE);
                    if (!shouldKeep) {
                        lookupFlowStates.remove(sessionId);
                        lookupFlowState = null;
                    }
                }
            }

            if (lookupFlowState != null) {
                ChatbotMessageResponse flowResponse = handleLookupFlow(sessionId, message, parsed, lookupFlowState, authentication);
                if (flowResponse != null) {
                    flowResponse.setSessionId(sessionId);
                    return flowResponse;
                }
            }

            ChatbotMessageParser.ParseResult mergedCurrent = menuIntent != null
                    ? overrideIntent(parsed, menuIntent)
                    : parsed;

            ChatbotMessageParser.ParseResult effectiveParsed = mergeWithContext(mergedCurrent, recentUserMessages);

            ChatbotMessageResponse response;
            try {
                response = switch (effectiveParsed.intent()) {
                    case BOOK_ROOM -> handleBookRoom(message, effectiveParsed, authentication, sessionId);
                    case CANCEL_RESERVATION -> handleCancelReservation(message, effectiveParsed, recentUserMessages, authentication, sessionId);
                    case EXTEND_RESERVATION -> handleExtendReservation(message, effectiveParsed, recentUserMessages, authentication, sessionId);
                    case LOOKUP -> handleLookup(message, effectiveParsed, authentication, sessionId);
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
            log.error("Loi khong mong doi cua chatbot", e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
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

        // Doc tin nhan ngu canh tu moi den cu.
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
        String reply = "Vui lòng chọn chức năng: (1) Đặt phòng, (2) Hủy phòng, (3) Thêm giờ, (4) Tra cứu.";

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.FALLBACK)
                .reply(reply)
            .menuOptions(buildMenuOptions())
                .build();
    }

        private List<ChatbotMenuOptionResponse> buildMenuOptions() {
        return List.of(
                ChatbotMenuOptionResponse.builder()
                        .code("1")
                .label("Đặt phòng")
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .build(),
                ChatbotMenuOptionResponse.builder()
                        .code("2")
                .label("Hủy phòng")
                        .intent(ChatbotIntent.CANCEL_RESERVATION)
                        .build(),
                ChatbotMenuOptionResponse.builder()
                        .code("3")
                .label("Thêm giờ")
                        .intent(ChatbotIntent.EXTEND_RESERVATION)
                        .build(),
                ChatbotMenuOptionResponse.builder()
                        .code("4")
                .label("Tra cứu")
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

        if (compact.matches("^(chọn|lựa chọn)\\s*[1-4]$")
            || compact.matches("^[1-4]\\s*(chọn|lựa chọn)$")) {
            String digit = compact.replaceAll("[^1-4]", "");
            return mapMenuNumber(digit);
        }

        return matchMenuIntentByKeywords(normalized, folded);
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

    private ChatbotIntent matchMenuIntentByKeywords(String normalized, String folded) {
        for (Map.Entry<ChatbotIntent, List<String>> entry : MENU_KEYWORDS.entrySet()) {
            for (String key : entry.getValue()) {
                if (key == null || key.isBlank()) continue;
                String keyNorm = key.toLowerCase(Locale.ROOT);
                String keyFold = foldText(keyNorm);
                if ((normalized != null && normalized.contains(keyNorm))
                        || (folded != null && folded.contains(keyFold))) {
                    return entry.getKey();
                }
            }
        }
        return null;
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

    private ChatbotMessageResponse handleBookingFlow(
            String sessionId,
            String message,
            ChatbotMessageParser.ParseResult parsed,
            BookingFlowState flowState,
            Authentication authentication
    ) {
        if (flowState == null) return null;   

        if (flowState.step == BookingStep.ASK_BUILDING) {
            List<Building> buildings = loadActiveBuildings();
            String normalized = parsed != null && parsed.normalizedMessage() != null
                    ? parsed.normalizedMessage()
                    : Objects.toString(message, "").trim().toLowerCase(Locale.ROOT);
            String folded = foldText(normalized);

            Building selected = resolveBuildingForBooking(normalized, folded, buildings);
            if (selected == null) {
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .reply(buildBuildingPrompt(buildings))
                .menuOptions(buildBuildingMenuOptions(buildings))
                        .build();
            }

            flowState.buildingId = selected.getId();
            flowState.step = BookingStep.ASK_TIME;

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("Bạn muốn đặt khi nào?")
                    .build();
        }

        if (flowState.step == BookingStep.ASK_TIME) {
            LocalDate date = parsed != null ? parsed.date() : null;
            LocalTime start = parsed != null ? parsed.startTime() : null;
            if (date == null || start == null) {
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .reply("Mình chưa nhận được thời gian. Bạn có thể nói: 'Ngày mai lúc 2h'.")
                        .build();
            }

            flowState.date = date;
            flowState.startTime = start;
            flowState.step = BookingStep.ASK_DURATION;

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("Trong bao lâu?")
                    .build();
        }

        if (flowState.step == BookingStep.ASK_DURATION) {
            Double hours = extractDurationHours(message);
            if (hours == null || hours <= 0) {
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .reply("Bạn muốn đặt trong bao lâu? Ví dụ: '2 tiếng'.")
                        .build();
            }

            LocalDate date = flowState.date;
            LocalTime start = flowState.startTime;
            if (date == null || start == null) {
                bookingFlowStates.remove(sessionId);
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .reply("Mình bị thiếu thời gian đặt. Vui lòng bắt đầu lại: 'Đặt phòng'.")
                        .build();
            }

            flowState.durationHours = hours;
            flowState.step = BookingStep.ASK_CAPACITY;

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("Cho bao nhiêu người?")
                    .build();
        }

        if (flowState.step == BookingStep.ASK_CAPACITY) {
            int[] range = extractCapacityRange(message);
            Integer minCapacity = parsed != null ? parsed.minCapacity() : null;
            if ((minCapacity == null || minCapacity <= 0) && range == null) {
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .reply("Bạn cần khoảng bao nhiêu người? Ví dụ: '5-20 người'.")
                        .menuOptions(buildCapacityRangeMenuOptions())
                        .build();
            }

            LocalDate date = flowState.date;
            LocalTime start = flowState.startTime;
            Double hours = flowState.durationHours;
            if (date == null || start == null || hours == null || hours <= 0) {
                bookingFlowStates.remove(sessionId);
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .reply("Mình bị thiếu thông tin đặt phòng. Vui lòng bắt đầu lại: 'Đặt phòng'.")
                        .build();
            }

            LocalTime end = addHoursToTime(start, hours);
            if (end == null) {
                bookingFlowStates.remove(sessionId);
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .reply("Thời lượng không hợp lệ. Vui lòng bắt đầu lại: 'Đặt phòng'.")
                        .build();
            }

                ChatbotMessageResponse reserveResponse;
                if (range != null) {
                int min = Math.min(range[0], range[1]);
                int max = Math.max(range[0], range[1]);
                reserveResponse = autoReserveByCapacityRange(
                    message,
                    date,
                    start,
                    end,
                    min,
                    max,
                    flowState.buildingId,
                    authentication,
                    false
                );
                } else {
                reserveResponse = autoReserveByCapacity(
                    message,
                    date,
                    start,
                    end,
                    minCapacity,
                    flowState.buildingId,
                    authentication,
                    false
                );
                }

            if (reserveResponse != null && reserveResponse.getReservation() != null) {
                bookingFlowStates.remove(sessionId);
            }

            return reserveResponse;
        }

        return null;
    }

    private ChatbotMessageResponse handleExtendFlow(
            String sessionId,
            String message,
            ChatbotMessageParser.ParseResult parsed,
            ExtendFlowState flowState,
            Authentication authentication
    ) {
        if (flowState == null) return null;
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        if (flowState.step == ExtendStep.ASK_SELECTION) {
            List<Reservation> options = flowState.reservations == null ? List.of() : flowState.reservations;
            if (options.isEmpty()) {
                extendFlowStates.remove(sessionId);
                String reply = "Bạn hiện không có lịch đặt phòng nào để gia hạn.";
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.EXTEND_RESERVATION)
                        .reply(reply)
                        .message(reply)
                        .build();
            }

            Integer index = extractSelectionIndex(message);
            Reservation target = null;

            if (index != null && index >= 1 && index <= options.size()) {
                target = options.get(index - 1);
            } else if (parsed != null && parsed.roomCode() != null && !parsed.roomCode().isBlank()) {
                String roomCode = parsed.roomCode();
                target = options.stream()
                        .filter(r -> r.getRoom() != null)
                        .filter(r -> r.getRoom().getLocationCode() != null)
                        .filter(r -> r.getRoom().getLocationCode().equalsIgnoreCase(roomCode))
                        .findFirst()
                        .orElse(null);
            }

            if (target == null) {
                String reply = "Vui lòng chọn số thứ tự hoặc nhập đúng mã phòng để gia hạn.";
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.EXTEND_RESERVATION)
                        .reply(reply)
                        .message(reply)
                        .items(buildBookingItems(options))
                        .build();
            }

            flowState.roomCode = target.getRoom() != null ? target.getRoom().getLocationCode() : null;
            flowState.step = ExtendStep.ASK_DURATION;

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.EXTEND_RESERVATION)
                    .reply("Bạn muốn thêm bao lâu?")
                    .build();
        }

        if (flowState.step == ExtendStep.ASK_ROOM) {
            String roomCode = parsed != null ? parsed.roomCode() : null;
            if (roomCode == null || roomCode.isBlank()) {
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.EXTEND_RESERVATION)
                        .reply("Mình chưa nhận được mã phòng. Vui lòng nhập mã phòng (ví dụ: A-203).")
                        .build();
            }

            var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
            Reservation target = findActiveReservationByRoomCode(user.getId(), roomCode);
            if (target == null) {
            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.EXTEND_RESERVATION)
                .reply("Mình chưa tìm thấy đặt phòng đang hoạt động cho phòng này. Vui lòng nhập lại mã phòng.")
                .build();
            }

            flowState.roomCode = roomCode;
            flowState.step = ExtendStep.ASK_DURATION;

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.EXTEND_RESERVATION)
                    .reply("Bạn muốn thêm bao lâu?")
                    .build();
        }

        if (flowState.step == ExtendStep.ASK_DURATION) {
            Double hours = extractDurationHours(message);
            if (hours == null || hours <= 0) {
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.EXTEND_RESERVATION)
                        .reply("Vui lòng nhập thời lượng (ví dụ: '30 phút' hoặc '2 tiếng').")
                        .build();
            }

            String roomCode = flowState.roomCode;
            if (roomCode == null || roomCode.isBlank()) {
                extendFlowStates.remove(sessionId);
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.EXTEND_RESERVATION)
                        .reply("Mình bị thiếu mã phòng. Vui lòng bắt đầu lại: 'Thêm giờ'.")
                        .build();
            }

            extendFlowStates.remove(sessionId);
            return extendReservationWithRoomCode(message, roomCode, hours, authentication, sessionId);
        }

        return null;
    }

    private Double extractDurationHours(String message) {
        String normalized = Objects.toString(message, "").toLowerCase(Locale.ROOT);

        Pattern hoursMinutesPattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:giờ|gio|tiếng|tieng)\\s*(\\d{1,3})\\s*(?:phút|phut)", Pattern.CASE_INSENSITIVE);
        Matcher hoursMinutesMatcher = hoursMinutesPattern.matcher(normalized);
        if (hoursMinutesMatcher.find()) {
            double hours = parseHourOrDefault(hoursMinutesMatcher.group(1), 0.0);
            double minutes = parseHourOrDefault(hoursMinutesMatcher.group(2), 0.0);
            double total = hours + (minutes / 60.0);
            return total > 0 ? total : null;
        }

        Pattern rangePattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*[-–]\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:giờ|gio|tiếng|tien)", Pattern.CASE_INSENSITIVE);
        Matcher rangeMatcher = rangePattern.matcher(normalized);
        if (rangeMatcher.find()) {
            double from = parseHourOrDefault(rangeMatcher.group(1), 0.0);
            double to = parseHourOrDefault(rangeMatcher.group(2), 0.0);
            double pick = Math.max(from, to);
            return pick > 0 ? pick : null;
        }

        Pattern hoursPattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:giờ|gio|tiếng|tien)", Pattern.CASE_INSENSITIVE);
        Matcher hoursMatcher = hoursPattern.matcher(normalized);
        if (hoursMatcher.find()) {
            return parseHourOrDefault(hoursMatcher.group(1), 0.0);
        }

        Pattern minutesPattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:phút|phut)", Pattern.CASE_INSENSITIVE);
        Matcher minutesMatcher = minutesPattern.matcher(normalized);
        if (minutesMatcher.find()) {
            double minutes = parseHourOrDefault(minutesMatcher.group(1), 0.0) * 1.0;
            return minutes > 0 ? minutes / 60.0 : 0.0;
        }

        return null;
    }

    private LocalTime addHoursToTime(LocalTime start, double hours) {
        if (start == null || hours <= 0) return null;
        long minutes = Math.round(hours * 60.0);
        if (minutes <= 0) return null;
        LocalTime end = start.plusMinutes(minutes);
        return end.equals(start) ? null : end;
    }

    private ChatbotMessageResponse toBookingLockedResponse(String message, ChatbotIntent intent, Object data) {
        String unlockAt = "";
        if (data instanceof LocalDateTime dt) {
            unlockAt = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }

        String reply = unlockAt.isBlank()
                ? "Chức năng đặt phòng hiện đang bị khóa do vượt quá số lần hủy cho phép. Vui lòng thử lại sau."
                : "Chức năng đặt phòng hiện đang bị khóa do vượt quá số lần hủy cho phép. Thời gian mở khóa dự kiến: " + unlockAt + ".";

        return ChatbotMessageResponse.builder()
                .intent(intent != null ? intent : ChatbotIntent.FALLBACK)
                .reply(reply)
                .build();
    }

    private ChatbotMessageResponse toBusinessErrorResponse(String message, ChatbotIntent intent, CustomException e) {
        String localized = localizeResponseCode(e.getResponseCode());
        String businessMessage = localized != null ? localized : null;

        if (businessMessage == null || businessMessage.isBlank()) {
            businessMessage = "Đã xảy ra lỗi khi xử lý yêu cầu. Vui lòng thử lại.";
        }

        return ChatbotMessageResponse.builder()
                .intent(intent != null ? intent : ChatbotIntent.FALLBACK)
                .reply(businessMessage)
                .build();
    }

    private ChatbotMessageResponse handleLookup(
            String message,
            ChatbotMessageParser.ParseResult parsed,
            Authentication authentication,
            String sessionId
    ) {
        LookupAction action = resolveLookupAction(message, parsed);
        if (action == LookupAction.NONE) {
            return buildLookupMenuResponse();
        }

        return switch (action) {
            case HISTORY -> {
                lookupFlowStates.remove(sessionId);
                yield handleLookupHistory(authentication);
            }
            case AVAILABLE_NOW -> {
                lookupFlowStates.remove(sessionId);
                yield handleLookupAvailableNow();
            }
            case ROOM_DETAIL -> handleLookupRoomDetail(message, parsed, sessionId);
            case CAPACITY_RANGE -> handleLookupCapacityRange(message, parsed, sessionId);
            default -> buildLookupMenuResponse();
        };
    }

    private ChatbotMessageResponse buildLookupMenuResponse() {
        String message = "Vui lòng chọn mục tra cứu:";
        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.LOOKUP)
                .message(message)
                .menuOptions(buildLookupMenuOptions())
                .build();
    }

    private List<ChatbotMenuOptionResponse> buildLookupMenuOptions() {
        return List.of(
                ChatbotMenuOptionResponse.builder()
                        .code("1")
                        .label("Lịch sử đặt phòng của tôi")
                        .intent(ChatbotIntent.LOOKUP)
                        .build(),
                ChatbotMenuOptionResponse.builder()
                        .code("2")
                        .label("Phòng còn trống")
                        .intent(ChatbotIntent.LOOKUP)
                        .build(),
                ChatbotMenuOptionResponse.builder()
                        .code("3")
                        .label("Chi tiết phòng")
                        .intent(ChatbotIntent.LOOKUP)
                        .build(),
                ChatbotMenuOptionResponse.builder()
                        .code("4")
                        .label("Tìm kiếm theo sức chứa")
                        .intent(ChatbotIntent.LOOKUP)
                        .build()
        );
    }

    private LookupAction resolveLookupAction(String message, ChatbotMessageParser.ParseResult parsed) {
        String normalized = parsed != null && parsed.normalizedMessage() != null
                ? parsed.normalizedMessage()
                : Objects.toString(message, "").trim().toLowerCase(Locale.ROOT);

        String compact = normalized.replaceAll("\\s+", " ").trim();
        if (compact.matches("^[1-4]$")) {
            return mapLookupNumber(compact);
        }
        if (compact.matches("^(chọn|lựa chọn)\\s*[1-4]$")
                || compact.matches("^[1-4]\\s*(chọn|lựa chọn)$")) {
            String digit = compact.replaceAll("[^1-4]", "");
            return mapLookupNumber(digit);
        }

        if (compact.contains("lịch sử đặt phòng")) return LookupAction.HISTORY;
        if (compact.contains("phòng còn trống") || compact.contains("phòng trống")) return LookupAction.AVAILABLE_NOW;
        if (compact.contains("chi tiết phòng")) return LookupAction.ROOM_DETAIL;
        if (compact.contains("tìm kiếm theo sức chứa") || compact.contains("sức chứa")) return LookupAction.CAPACITY_RANGE;

        return LookupAction.NONE;
    }

    private LookupAction mapLookupNumber(String digit) {
        if (digit == null || digit.isBlank()) return LookupAction.NONE;
        return switch (digit) {
            case "1" -> LookupAction.HISTORY;
            case "2" -> LookupAction.AVAILABLE_NOW;
            case "3" -> LookupAction.ROOM_DETAIL;
            case "4" -> LookupAction.CAPACITY_RANGE;
            default -> LookupAction.NONE;
        };
    }

    private ChatbotMessageResponse handleLookupHistory(Authentication authentication) {
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        var page = reservationService.getReservationHistory(null, null, 0, 10, authentication);
        List<ReservationResponse> history = page != null ? page.getContent() : List.of();
        if (history == null || history.isEmpty()) {
            String reply = "Bạn chưa có lịch sử đặt phòng.";
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.LOOKUP)
                    .reply(reply)
                    .message(reply)
                    .build();
        }

        List<ChatbotBookingItemResponse> items = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            ReservationResponse res = history.get(i);
            String roomCode = res.getRoom() != null ? Objects.toString(res.getRoom().getLocationCode(), "") : "";
            String start = res.getStartTime() != null ? res.getStartTime().format(DATE_TIME_FMT) : "";
            String end = res.getEndTime() != null ? res.getEndTime().format(DATE_TIME_FMT) : "";
            String status = localizeReservationStatus(res.getStatus());
            String label = (i + 1) + ". " + roomCode + " | " + start + " - " + end + (status.isBlank() ? "" : " | " + status);

            items.add(ChatbotBookingItemResponse.builder()
                    .id(res.getId())
                    .label(label)
                    .roomCode(roomCode)
                    .startTime(start)
                    .endTime(end)
                    .build());
        }

        String message = "Lịch sử đặt phòng của bạn:";
        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.LOOKUP)
                .message(message)
                .items(items)
                .build();
    }

    private ChatbotMessageResponse handleLookupAvailableNow() {
        List<Room> rooms = roomRepository.findAllWithDetails().stream()
                .filter(Objects::nonNull)
                .filter(r -> r.getStatus() == RoomStatus.AVAILABLE)
                .filter(r -> r.getFloor() == null || !r.getFloor().isDeleted())
                .filter(r -> r.getFloor() == null || r.getFloor().getBuilding() == null || !r.getFloor().getBuilding().isDeleted())
                .sorted(Comparator.comparing(Room::getLocationCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        if (rooms.isEmpty()) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY)
                    .reply("Hiện tại không có phòng trống.")
                    .availableRooms(List.of())
                    .build();
        }

        List<ChatbotRoomItemResponse> available = rooms.stream()
                .map(r -> toRoomItem(r, List.of()))
                .toList();

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY)
                .reply("Danh sách phòng còn trống:")
                .availableRooms(available)
                .build();
    }

    private ChatbotMessageResponse handleLookupRoomDetail(
            String message,
            ChatbotMessageParser.ParseResult parsed,
            String sessionId
    ) {
        String roomCode = resolveRoomCodeForDetail(message, parsed);
        if (roomCode == null || roomCode.isBlank()) {
            LookupFlowState state = new LookupFlowState();
            state.step = LookupStep.ASK_ROOM_CODE;
            lookupFlowStates.put(sessionId, state);

                String reply = "Vui lòng nhập location code.";
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.LOOKUP)
                    .reply(reply)
                    .message(reply)
                    .build();
        }

        Room room = roomRepository.findByLocationCodeIgnoreCase(roomCode).orElse(null);
        if (room == null) {
            LookupFlowState state = new LookupFlowState();
            state.step = LookupStep.ASK_ROOM_CODE;
            lookupFlowStates.put(sessionId, state);

                String reply = "Phòng không hợp lệ hoặc không tồn tại, vui lòng nhập lại";
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.LOOKUP)
                    .reply(reply)
                    .message(reply)
                    .build();
        }

        lookupFlowStates.remove(sessionId);
        RoomDetailResponse detail = roomService.getRoomDetail(room.getId());
        String reply = "Chi tiết phòng " + room.getLocationCode() + ":";
        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.VIEW_FACILITY_DETAILS)
                .reply(reply)
                .roomDetail(detail)
                .build();
    }

    private ChatbotMessageResponse handleLookupCapacityRange(
            String message,
            ChatbotMessageParser.ParseResult parsed,
            String sessionId
    ) {
        int[] range = extractCapacityRange(message);
        if (range == null) {
            LookupFlowState state = new LookupFlowState();
            state.step = LookupStep.ASK_CAPACITY_RANGE;
            lookupFlowStates.put(sessionId, state);

            String reply = "Vui lòng nhập khoảng sức chứa, ví dụ: 5-20 người.";
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.LOOKUP)
                    .reply(reply)
                    .message(reply)
                    .build();
        }

        int min = Math.min(range[0], range[1]);
        int max = Math.max(range[0], range[1]);

        List<Room> rooms = roomRepository.findAllWithDetails().stream()
            .filter(r -> r.getStatus() == RoomStatus.AVAILABLE)
                .filter(r -> r.getFloor() == null || !r.getFloor().isDeleted())
                .filter(r -> r.getFloor() == null || r.getFloor().getBuilding() == null || !r.getFloor().getBuilding().isDeleted())
                .filter(r -> r.getCapacity() != null && r.getCapacity() >= min && r.getCapacity() <= max)
                .sorted(Comparator.comparing(Room::getCapacity, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Room::getLocationCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        if (rooms.isEmpty()) {
            String reply = "Không tìm thấy phòng phù hợp cho khoảng " + min + "-" + max + " người.";
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.SUGGEST_ROOMS_BY_CAPACITY)
                    .reply(reply)
                    .availableRooms(List.of())
                    .build();
        }

        List<ChatbotRoomItemResponse> suggested = rooms.stream()
                .map(r -> toRoomItem(r, List.of()))
                .toList();

        lookupFlowStates.remove(sessionId);

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.SUGGEST_ROOMS_BY_CAPACITY)
                .reply("Danh sách phòng phù hợp cho khoảng " + min + "-" + max + " người:")
                .availableRooms(suggested)
                .build();
    }

    private ChatbotMessageResponse handleLookupFlow(
            String sessionId,
            String message,
            ChatbotMessageParser.ParseResult parsed,
            LookupFlowState flowState,
            Authentication authentication
    ) {
        if (flowState == null) return null;

        if (flowState.step == LookupStep.ASK_ROOM_CODE) {
            return handleLookupRoomDetail(message, parsed, sessionId);
        }

        if (flowState.step == LookupStep.ASK_CAPACITY_RANGE) {
            return handleLookupCapacityRange(message, parsed, sessionId);
        }

        return null;
    }

    private int[] extractCapacityRange(String message) {
        if (message == null) return null;
        Matcher m = Pattern.compile("(\\d{1,3})\\s*[-–]\\s*(\\d{1,3})(?:\\s*người)?").matcher(message.toLowerCase(Locale.ROOT));
        if (m.find()) {
            try {
                int a = Integer.parseInt(m.group(1));
                int b = Integer.parseInt(m.group(2));
                if (a > 0 && b > 0) return new int[] {a, b};
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String resolveRoomCodeForDetail(String message, ChatbotMessageParser.ParseResult parsed) {
        String roomCode = parsed != null ? parsed.roomCode() : null;
        if (roomCode != null && !roomCode.isBlank()) return roomCode;

        String normalized = parsed != null && parsed.normalizedMessage() != null
            ? parsed.normalizedMessage()
            : Objects.toString(message, "").trim().toLowerCase(Locale.ROOT);

        String compact = normalized.replaceAll("\\s+", " ").trim();
        if (compact.matches("^[1-4]$")
            || compact.matches("^(chọn|lựa chọn)\\s*[1-4]$")
            || compact.matches("^[1-4]\\s*(chọn|lựa chọn)$")
            || compact.contains("chi tiết phòng")) {
            return null;
        }

        return chatbotLlmService.parse(message, List.of())
                .map(ChatbotMessageParser.ParseResult::roomCode)
            .filter(code -> code != null && !code.isBlank())
            .filter(code -> ROOM_CODE_VALIDATION_PATTERN.matcher(code).find())
                .orElse(null);
    }

    private ChatbotMessageResponse handleCancelReservation(
            String message,
            ChatbotMessageParser.ParseResult parsed,
            List<String> recentUserMessages,
            Authentication authentication,
            String sessionId
    ) {
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

        String contextualRoomCode = resolveContextualRoomCode(parsed, recentUserMessages);

        if (contextualRoomCode != null && !contextualRoomCode.isBlank()) {
            Reservation target = findTargetReservationForAction(user.getId(), contextualRoomCode);
            if (target == null) {
                String reply = "Mình chưa tìm thấy đặt phòng đang hoạt động cho phòng này. Vui lòng kiểm tra lại mã phòng.";
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.CANCEL_RESERVATION)
                        .reply(reply)
                        .build();
            }

            String reason = extractCancelReason(message);
            reservationService.cancelReservation(target.getId(), reason, authentication);

            String roomCode = target.getRoom() != null ? target.getRoom().getLocationCode() : "";
            String reply = "Đã hủy đặt phòng " + roomCode + " thành công.";

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.CANCEL_RESERVATION)
                    .reply(reply)
                    .build();
        }

        List<Reservation> active = reservationRepository.findActiveReservationsOfUser(
                user.getId(),
                ACTIVE_RESERVATION_STATUSES
        );

        if (active == null || active.isEmpty()) {
            String reply = "Bạn hiện không có lịch đặt phòng nào để hủy.";
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.CANCEL_RESERVATION)
                    .reply(reply)
                    .message(reply)
                    .build();
        }

        if (active.size() == 1) {
            Reservation target = active.get(0);
            String reason = extractCancelReason(message);
            reservationService.cancelReservation(target.getId(), reason, authentication);

            String roomCode = target.getRoom() != null ? target.getRoom().getLocationCode() : "";
            String reply = "Đã hủy đặt phòng " + roomCode + " thành công.";

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.CANCEL_RESERVATION)
                    .reply(reply)
                    .build();
        }

        active = active.stream()
                .sorted(Comparator.comparing(Reservation::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        CancelFlowState state = new CancelFlowState();
        state.step = CancelStep.ASK_SELECTION;
        state.reservations = active;
        cancelFlowStates.put(sessionId, state);

        String messageText = "Bạn đang có các lịch đặt phòng sau, bạn muốn hủy lịch nào:";
        String reply = null;
        List<ChatbotBookingItemResponse> items = buildBookingItems(active);
        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.CANCEL_RESERVATION)
            .reply(reply)
            .message(messageText)
            .items(items)
                .build();
    }

    private ChatbotMessageResponse handleCancelFlow(
            String sessionId,
            String message,
            ChatbotMessageParser.ParseResult parsed,
            CancelFlowState flowState,
            Authentication authentication
    ) {
        if (flowState == null) return null;
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        if (flowState.step == CancelStep.ASK_SELECTION) {
            List<Reservation> options = flowState.reservations == null ? List.of() : flowState.reservations;
            if (options.isEmpty()) {
                cancelFlowStates.remove(sessionId);
                return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.CANCEL_RESERVATION)
                    .reply("Bạn hiện không có lịch đặt phòng nào để hủy.")
                    .message("Bạn hiện không có lịch đặt phòng nào để hủy.")
                    .build();
            }

            Integer index = extractSelectionIndex(message);
            Reservation target = null;

            if (index != null && index >= 1 && index <= options.size()) {
                target = options.get(index - 1);
            } else if (parsed != null && parsed.roomCode() != null && !parsed.roomCode().isBlank()) {
                String roomCode = parsed.roomCode();
                target = options.stream()
                        .filter(r -> r.getRoom() != null)
                        .filter(r -> r.getRoom().getLocationCode() != null)
                        .filter(r -> r.getRoom().getLocationCode().equalsIgnoreCase(roomCode))
                        .findFirst()
                        .orElse(null);
            }

            if (target == null) {
                String reply = "Vui lòng chọn số thứ tự hoặc nhập đúng mã phòng để hủy.";
                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.CANCEL_RESERVATION)
                        .reply(reply)
                        .message(reply)
                        .items(buildBookingItems(options))
                        .build();
            }

            cancelFlowStates.remove(sessionId);
            String reason = extractCancelReason(message);
            reservationService.cancelReservation(target.getId(), reason, authentication);

            String roomCode = target.getRoom() != null ? target.getRoom().getLocationCode() : "";
            String reply = "Đã hủy đặt phòng " + roomCode + " thành công.";

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.CANCEL_RESERVATION)
                    .reply(reply)
                    .build();
        }

        return null;
    }

    private Integer extractSelectionIndex(String message) {
        if (message == null) return null;
        String trimmed = message.trim();
        Matcher m = Pattern.compile("(?:^|\\D)(\\d{1,2})(?:$|\\D)").matcher(trimmed);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private List<ChatbotBookingItemResponse> buildBookingItems(List<Reservation> reservations) {
        if (reservations == null || reservations.isEmpty()) return List.of();
        List<ChatbotBookingItemResponse> items = new ArrayList<>();
        for (int i = 0; i < reservations.size(); i++) {
            Reservation r = reservations.get(i);
            String roomCode = r.getRoom() != null ? Objects.toString(r.getRoom().getLocationCode(), "") : "";
            String start = r.getStartTime() != null ? r.getStartTime().format(DATE_TIME_FMT) : "";
            String end = r.getEndTime() != null ? r.getEndTime().format(DATE_TIME_FMT) : "";
            String label = (i + 1) + ". " + roomCode + " | " + start + " - " + end;

            items.add(ChatbotBookingItemResponse.builder()
                    .id(r.getId())
                    .label(label)
                    .roomCode(roomCode)
                    .startTime(start)
                    .endTime(end)
                    .build());
        }
        return items;
    }

    private String formatReservationTimeRange(Reservation reservation) {
        if (reservation == null || reservation.getStartTime() == null || reservation.getEndTime() == null) {
            return "";
        }
        return reservation.getStartTime().format(DATE_TIME_FMT)
                + " - "
                + reservation.getEndTime().format(DATE_TIME_FMT);
    }

    private ChatbotMessageResponse handleExtendReservation(
            String message,
            ChatbotMessageParser.ParseResult parsed,
            List<String> recentUserMessages,
            Authentication authentication,
            String sessionId
    ) {
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        if ((parsed == null || parsed.roomCode() == null || parsed.roomCode().isBlank())
                && extractDurationHours(message) == null) {
            return startExtendFlow(sessionId, authentication);
        }

        var user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

        String contextualRoomCode = resolveContextualRoomCode(parsed, recentUserMessages);
        Reservation target = findTargetReservationForAction(user.getId(), contextualRoomCode);
        if (target == null) {
            String reply = "Mình chưa tìm thấy đặt phòng đang hoạt động để gia hạn. Bạn có thể cung cấp mã phòng hoặc nói 'thêm 1 giờ cho phòng V5-020'.";
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
        String reply = "Đã gia hạn thêm " + hourText + " giờ cho phòng " + roomCode + " thành công."
            + (updatedWindow.isBlank() ? "" : " Khung giờ mới: " + updatedWindow + ".");

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.EXTEND_RESERVATION)
                .reply(reply)
                .build();
    }

        private ChatbotMessageResponse startExtendFlow(String sessionId, Authentication authentication) {
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        var user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

        List<Reservation> active = reservationRepository.findActiveReservationsOfUser(
            user.getId(),
            ACTIVE_RESERVATION_STATUSES
        );

        if (active == null || active.isEmpty()) {
                String reply = "Bạn hiện không có lịch đặt phòng nào để gia hạn.";
            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.EXTEND_RESERVATION)
                .reply(reply)
                .message(reply)
                .build();
        }

        ExtendFlowState state = new ExtendFlowState();

        if (active.size() == 1) {
            Reservation target = active.get(0);
            state.step = ExtendStep.ASK_DURATION;
            state.roomCode = target.getRoom() != null ? target.getRoom().getLocationCode() : null;
            extendFlowStates.put(sessionId, state);

            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.EXTEND_RESERVATION)
                .reply("Bạn muốn thêm bao lâu?")
                .build();
        }

        active = active.stream()
            .sorted(Comparator.comparing(Reservation::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        state.step = ExtendStep.ASK_SELECTION;
        state.reservations = active;
        extendFlowStates.put(sessionId, state);

        String messageText = "Bạn đang có các lịch đặt phòng sau, bạn muốn thêm giờ lịch nào:";
        List<ChatbotBookingItemResponse> items = buildBookingItems(active);

        return ChatbotMessageResponse.builder()
            .intent(ChatbotIntent.EXTEND_RESERVATION)
            .reply(null)
            .message(messageText)
            .items(items)
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

        // Neu thieu/khong tim thay ma phong, lay booking gan nhat dang hoat dong.
        if (candidates == null || candidates.isEmpty()) {
            candidates = reservationRepository.findActiveReservationsOfUser(userId, ACTIVE_RESERVATION_STATUSES);
        }

        return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
    }

    private Reservation findActiveReservationByRoomCode(String userId, String roomCode) {
        if (userId == null || userId.isBlank() || roomCode == null || roomCode.isBlank()) return null;
        List<Reservation> candidates = reservationRepository.findActiveReservationsOfUserByRoomCode(
                userId,
                roomCode,
                ACTIVE_RESERVATION_STATUSES
        );
        return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
    }

    private String formatReservationWindow(Reservation reservation) {
        if (reservation == null || reservation.getStartTime() == null || reservation.getEndTime() == null) return "";
        return reservation.getStartTime().format(DATE_TIME_FMT)
            + " - "
            + reservation.getEndTime().format(DATE_TIME_FMT);
    }

    private String formatDateTime(LocalDate date, LocalTime time) {
        if (date == null || time == null) return "";
        return LocalDateTime.of(date, time).format(DATE_TIME_FMT);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DATE_TIME_FMT);
    }

    private double extractExtendHours(String message) {
        String normalized = Objects.toString(message, "").toLowerCase(Locale.ROOT);

        // Mau cau: "thêm X giờ/tiếng" hoặc "gia hạn X giờ"
        Pattern viPattern = Pattern.compile("(?:thêm|them|gia hạn|gia han|kéo dài|keo dai)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:giờ|gio|tiếng|tien)", Pattern.CASE_INSENSITIVE);
        Matcher viMatcher = viPattern.matcher(normalized);
        if (viMatcher.find()) {
            return parseHourOrDefault(viMatcher.group(1), 1.0);
        }

        // Mau cau: "lên X tiếng/giờ"
        Pattern viUptoPattern = Pattern.compile("(?:lên|len)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:giờ|gio|tiếng|tien)", Pattern.CASE_INSENSITIVE);
        Matcher viUptoMatcher = viUptoPattern.matcher(normalized);
        if (viUptoMatcher.find()) {
            return parseHourOrDefault(viUptoMatcher.group(1), 1.0);
        }

        Pattern trailingPattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:giờ|gio|tiếng|tien)", Pattern.CASE_INSENSITIVE);
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

    private String extractCancelReason(String message) {
        String normalized = Objects.toString(message, "");
        Pattern p = Pattern.compile("(?:vì|vi|do)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(normalized);
        if (m.find()) {
            String reason = m.group(1) != null ? m.group(1).trim() : "";
            if (!reason.isBlank()) return reason;
        }
        return "Hủy qua chatbot";
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

    private ChatbotMessageResponse handleBookRoom(String message, ChatbotMessageParser.ParseResult parsed, Authentication authentication, String sessionId) {
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        if (!bookingFlowStates.containsKey(sessionId) && detectMenuIntent(message) == ChatbotIntent.BOOK_ROOM) {
            return startBookingFlow(sessionId);
        }

        if (parsed != null && parsed.startTime() == null && parsed.endTime() == null
                && (parsed.roomCode() == null || parsed.roomCode().isBlank())
                && (parsed.minCapacity() == null || parsed.minCapacity() <= 0)) {
            return startBookingFlow(sessionId);
        }

        LocalDate date = parsed.date() != null ? parsed.date() : LocalDate.now();
        LocalTime start = parsed.startTime();
        LocalTime end = parsed.endTime();

        if (start == null) {
            String roomHint = (parsed.roomCode() != null && !parsed.roomCode().isBlank())
                    ? parsed.roomCode()
                : "một phòng";
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                .reply("Bạn muốn đặt phòng vào thời gian nào? Ví dụ: 'Đặt " + roomHint + " lúc 10:00 hôm nay' hoặc 'từ 14:00 đến 15:00 hôm nay'.")
                    .build();
        }

        if (end == null) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                .reply("Mình chưa thấy thời gian kết thúc. Bạn có thể nói: 'từ 10:00 đến 11:00'.")
                    .build();
        }

        LocalDateTime startTime = LocalDateTime.of(date, start);
        LocalDateTime endTime = LocalDateTime.of(date, end);

        // Neu gio ket thuc bi tu dong them (vi du: "23:00 hom nay"),
        // LocalTime co the quay ve 00:00 va bi hieu sai. Can chuan hoa truoc.
        if (parsed.endTimeDefaulted() && !endTime.isAfter(startTime)) {
            LocalDateTime candidate = startTime.plusHours(1);
            LocalDateTime endOfDay = startTime.toLocalDate().atTime(23, 59);
            endTime = candidate.isAfter(endOfDay) ? endOfDay : candidate;
        }

        if (!startTime.isBefore(endTime)) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("Khung giờ không hợp lệ. Vui lòng đảm bảo giờ kết thúc sau giờ bắt đầu.")
                    .build();
        }

        if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("Đặt phòng phải trong cùng một ngày. Vui lòng chọn giờ kết thúc cùng ngày.")
                    .build();
        }

        if (startTime.isBefore(LocalDateTime.now())) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("Giờ bắt đầu đã ở quá khứ. Vui lòng chọn thời gian trong tương lai.")
                    .build();
        }

        // Neu nguoi dung khong nhap ma phong, nhung co suc chua + thoi gian, tu chon phong va dat.
        if (parsed.roomCode() == null || parsed.roomCode().isBlank()) {
            Integer minCapacity = parsed.minCapacity();
            if (minCapacity != null && minCapacity > 0) {
                return autoReserveByCapacity(message, date, start, end, minCapacity, null, authentication, parsed.endTimeDefaulted());
            }

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("Bạn muốn đặt phòng nào? Vui lòng cung cấp mã phòng như 'AL-102'.")
                    .build();
        }

        Room room = roomRepository.findByLocationCodeIgnoreCase(parsed.roomCode())
                .orElse(null);

        if (room == null) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("Mình không tìm thấy phòng với mã '" + parsed.roomCode() + "'. Vui lòng kiểm tra lại mã phòng (ví dụ: AL-102).")
                    .build();
        }

        ReservationRequest reservationRequest = new ReservationRequest();
        reservationRequest.setRoomId(room.getId());
        reservationRequest.setStartTime(startTime);
        reservationRequest.setEndTime(endTime);
        reservationRequest.setPurpose("Họp");
        reservationRequest.setNote("Đặt qua chatbot");

        String startLabel = formatDateTime(startTime);
        String endLabel = formatDateTime(endTime);

        try {
            ReservationResponse reservation = reservationService.reserveRoom(reservationRequest, authentication);
            String reply = parsed.endTimeDefaulted()
                    ? pickByHash(message,
                    "Đã đặt phòng " + room.getLocationCode() + " lúc " + startLabel + " trong 1 giờ.",
                    "Hoàn tất. " + room.getLocationCode() + " đã được đặt từ " + startLabel + " đến " + endLabel + ".")
                    : pickByHash(message,
                    "Đặt phòng thành công. Bạn đã có " + room.getLocationCode() + " từ " + startLabel + " đến " + endLabel + ".",
                    "Xác nhận: " + room.getLocationCode() + " đã được đặt từ " + startLabel + " đến " + endLabel + ".",
                    "Hoàn tất — phòng " + room.getLocationCode() + " đã được đặt (" + startLabel + "–" + endLabel + ").");

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

                String timeSlot = startLabel + "–" + endLabel;
                List<ChatbotRoomItemResponse> alternativeResponses = alternatives.stream()
                    .map(r -> toRoomItem(r, List.of(timeSlot)))
                    .collect(Collectors.toList());

                String baseMessage = localizeResponseCode(e.getResponseCode());
                if (baseMessage == null || baseMessage.isBlank()) {
                    baseMessage = "Không thể đặt phòng trong khung giờ này.";
                }
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

    private List<Building> loadActiveBuildings() {
        return buildingRepository.findAll().stream()
                .filter(Objects::nonNull)
                .filter(b -> !b.isDeleted())
                .toList();
    }

    private String buildBuildingPrompt(List<Building> buildings) {
        if (buildings == null || buildings.isEmpty()) {
            return "Bạn muốn đặt phòng ở tòa nào? Hiện chưa có dữ liệu tòa nhà.";
        }

        String names = buildings.stream()
                .map(Building::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .limit(10)
                .collect(Collectors.joining(", "));

        return names.isBlank()
                ? "Bạn muốn đặt phòng ở tòa nào?"
                : "Bạn muốn đặt phòng ở tòa nào? Các tòa hiện có: " + names + ".";
    }

    private ChatbotMessageResponse startBookingFlow(String sessionId) {
        BookingFlowState state = new BookingFlowState();
        state.step = BookingStep.ASK_BUILDING;
        bookingFlowStates.put(sessionId, state);

        List<Building> buildings = loadActiveBuildings();

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.BOOK_ROOM)
                .reply(buildBuildingPrompt(buildings))
                .menuOptions(buildBuildingMenuOptions(buildings))
                .build();
    }

    private Building resolveBuildingForBooking(String normalized, String folded, List<Building> buildings) {
        if (buildings == null || buildings.isEmpty()) return null;

        String compact = Objects.toString(normalized, "").trim();
        if (!compact.isBlank()) {
            for (Building b : buildings) {
                if (b != null && b.getId() != null && b.getId().equalsIgnoreCase(compact)) {
                    return b;
                }
            }
        }

        return resolveBuilding(normalized, folded, buildings);
    }

    private List<ChatbotMenuOptionResponse> buildBuildingMenuOptions(List<Building> buildings) {
        if (buildings == null || buildings.isEmpty()) return List.of();

        return buildings.stream()
                .filter(Objects::nonNull)
                .filter(b -> b.getId() != null && b.getName() != null && !b.getName().isBlank())
                .limit(10)
                .map(b -> ChatbotMenuOptionResponse.builder()
                        .code(b.getId())
                        .label(b.getName())
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .build())
                .toList();
    }

        private List<ChatbotMenuOptionResponse> buildCapacityRangeMenuOptions() {
        return List.of(
            ChatbotMenuOptionResponse.builder().code("5-20").label("5 - 20 người").intent(ChatbotIntent.BOOK_ROOM).build(),
            ChatbotMenuOptionResponse.builder().code("20-40").label("20 - 40 người").intent(ChatbotIntent.BOOK_ROOM).build(),
            ChatbotMenuOptionResponse.builder().code("40-60").label("40 - 60 người").intent(ChatbotIntent.BOOK_ROOM).build(),
            ChatbotMenuOptionResponse.builder().code("60-80").label("60 - 80 người").intent(ChatbotIntent.BOOK_ROOM).build()
        );
        }
       
    private ChatbotMessageResponse autoReserveByCapacity(
            String message,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            int minCapacity,
            String buildingId,
            Authentication authentication,
            boolean endTimeDefaulted
    ) {
        LocalDateTime startTime = LocalDateTime.of(date, start);
        LocalDateTime endTime = LocalDateTime.of(date, end);

        List<Room> candidates = roomRepository.findAllWithDetails().stream()
                .filter(r -> r.getStatus() != RoomStatus.BROKEN)
                .filter(r -> r.getFloor() == null || !r.getFloor().isDeleted())
                .filter(r -> r.getFloor() == null || r.getFloor().getBuilding() == null || !r.getFloor().getBuilding().isDeleted())
            .filter(r -> buildingId == null || buildingId.isBlank()
                || (r.getFloor() != null
                && r.getFloor().getBuilding() != null
                && Objects.equals(r.getFloor().getBuilding().getId(), buildingId)))
                .filter(r -> r.getCapacity() != null && r.getCapacity() >= minCapacity)
                .sorted(Comparator.comparing(Room::getCapacity, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Room::getLocationCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        if (candidates.isEmpty()) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                .reply("Mình không tìm thấy phòng phù hợp cho " + minCapacity + "+ người. Vui lòng chọn lại số người.")
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

        String startLabel = formatDateTime(startTime);
        String endLabel = formatDateTime(endTime);

        for (Room room : candidates) {
            if (room.getId() == null) continue;
            if (busyRoomIds.contains(room.getId())) continue;

            ReservationRequest reservationRequest = new ReservationRequest();
            reservationRequest.setRoomId(room.getId());
            reservationRequest.setStartTime(startTime);
            reservationRequest.setEndTime(endTime);
            reservationRequest.setPurpose("Họp");
            reservationRequest.setNote("Đặt qua chatbot (tự chọn theo sức chứa)");

            try {
                ReservationResponse reservation = reservationService.reserveRoom(reservationRequest, authentication);

                String reply = endTimeDefaulted
                    ? "Đã đặt phòng " + room.getLocationCode() + " lúc " + startLabel + " trong 1 giờ (sức chứa " + minCapacity + "+)."
                    : "Đặt phòng thành công. Bạn đã có " + room.getLocationCode() + " từ " + startLabel + " đến " + endLabel + " (sức chứa " + minCapacity + "+).";

                return ChatbotMessageResponse.builder()
                        .intent(ChatbotIntent.BOOK_ROOM)
                        .reply(reply)
                        .reservation(reservation)
                        .build();
            } catch (CustomException e) {
                if (e.getResponseCode() == ResponseCode.CANNOT_RESERVE_ROOM
                        || e.getResponseCode() == ResponseCode.RESERVATION_TIME_OVERLAP
                        || e.getResponseCode() == ResponseCode.USER_TIME_OVERLAP) {
                    // Trung lich do phong vua bi dat boi nguoi khac, thu phong tiep theo.
                    continue;
                }
                throw e;
            }
        }

        return ChatbotMessageResponse.builder()
            .intent(ChatbotIntent.BOOK_ROOM)
            .reply("Mình không tìm thấy phòng trống cho khung giờ " + startLabel + "–" + endLabel + " với sức chứa " + minCapacity + "+. Vui lòng chọn lại số người.")
            .build();
    }

        private ChatbotMessageResponse autoReserveByCapacityRange(
            String message,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            int minCapacity,
            int maxCapacity,
            String buildingId,
            Authentication authentication,
            boolean endTimeDefaulted
        ) {
        LocalDateTime startTime = LocalDateTime.of(date, start);
        LocalDateTime endTime = LocalDateTime.of(date, end);

        List<Room> candidates = roomRepository.findAllWithDetails().stream()
            .filter(r -> r.getStatus() != RoomStatus.BROKEN)
            .filter(r -> r.getFloor() == null || !r.getFloor().isDeleted())
            .filter(r -> r.getFloor() == null || r.getFloor().getBuilding() == null || !r.getFloor().getBuilding().isDeleted())
            .filter(r -> buildingId == null || buildingId.isBlank()
                || (r.getFloor() != null
                && r.getFloor().getBuilding() != null
                && Objects.equals(r.getFloor().getBuilding().getId(), buildingId)))
            .filter(r -> r.getCapacity() != null && r.getCapacity() >= minCapacity && r.getCapacity() <= maxCapacity)
            .sorted(Comparator.comparing(Room::getCapacity, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Room::getLocationCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();

        if (candidates.isEmpty()) {
            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.BOOK_ROOM)
                .reply("Mình không tìm thấy phòng phù hợp cho khoảng " + minCapacity + "-" + maxCapacity + " người. Vui lòng chọn lại số người.")
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

        String startLabel = formatDateTime(startTime);
        String endLabel = formatDateTime(endTime);

        for (Room room : candidates) {
            if (room.getId() == null) continue;
            if (busyRoomIds.contains(room.getId())) continue;

            ReservationRequest reservationRequest = new ReservationRequest();
            reservationRequest.setRoomId(room.getId());
            reservationRequest.setStartTime(startTime);
            reservationRequest.setEndTime(endTime);
            reservationRequest.setPurpose("Họp");
            reservationRequest.setNote("Đặt qua chatbot (tự chọn theo sức chứa)");

            try {
            ReservationResponse reservation = reservationService.reserveRoom(reservationRequest, authentication);

                String reply = endTimeDefaulted
                    ? "Đã đặt phòng " + room.getLocationCode() + " lúc " + startLabel + " trong 1 giờ (" + minCapacity + "-" + maxCapacity + " người)."
                    : "Đặt phòng thành công. Bạn đã có " + room.getLocationCode() + " từ " + startLabel + " đến " + endLabel + " (" + minCapacity + "-" + maxCapacity + " người).";

            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.BOOK_ROOM)
                .reply(reply)
                .reservation(reservation)
                .build();
            } catch (CustomException e) {
            if (e.getResponseCode() == ResponseCode.CANNOT_RESERVE_ROOM
                || e.getResponseCode() == ResponseCode.RESERVATION_TIME_OVERLAP
                || e.getResponseCode() == ResponseCode.USER_TIME_OVERLAP) {
                continue;
            }
            throw e;
            }
        }

        return ChatbotMessageResponse.builder()
            .intent(ChatbotIntent.BOOK_ROOM)
            .reply("Mình không tìm thấy phòng trống cho khung giờ " + startLabel + "–" + endLabel + " với khoảng " + minCapacity + "-" + maxCapacity + " người. Vui lòng chọn lại số người.")
            .build();
        }

        private ChatbotMessageResponse autoReserveAny(
            String message,
            LocalDate date,
            LocalTime start,
            double hours,
            String buildingId,
            Authentication authentication
        ) {
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        if (buildingId == null || buildingId.isBlank()) {
            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.BOOK_ROOM)
                .reply("Mình chưa nhận được tòa nhà bạn muốn đặt.")
                .build();
        }

        if (hours <= 0) {
            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.BOOK_ROOM)
                .reply("Thời lượng không hợp lệ. Vui lòng nhập số giờ hợp lệ.")
                .build();
        }

        LocalDateTime startTime = LocalDateTime.of(date, start);
        LocalDateTime endTime = startTime.plusMinutes((long) Math.round(hours * 60.0));

        if (!startTime.isBefore(endTime)) {
            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.BOOK_ROOM)
                .reply("Khung giờ không hợp lệ. Vui lòng kiểm tra lại thời lượng.")
                .build();
        }

        if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.BOOK_ROOM)
                .reply("Đặt phòng phải trong cùng một ngày. Vui lòng chọn thời lượng ngắn hơn.")
                .build();
        }

        if (startTime.isBefore(LocalDateTime.now())) {
            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.BOOK_ROOM)
                .reply("Giờ bắt đầu đã ở quá khứ. Vui lòng chọn thời gian trong tương lai.")
                .build();
        }

        List<Room> candidates = roomRepository.findAllWithDetails().stream()
            .filter(r -> r.getStatus() != RoomStatus.BROKEN)
            .filter(r -> r.getFloor() == null || !r.getFloor().isDeleted())
            .filter(r -> r.getFloor() == null || r.getFloor().getBuilding() == null || !r.getFloor().getBuilding().isDeleted())
            .filter(r -> r.getFloor() != null
                && r.getFloor().getBuilding() != null
                && Objects.equals(r.getFloor().getBuilding().getId(), buildingId))
            .sorted(Comparator.comparing(Room::getLocationCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();

        if (candidates.isEmpty()) {
            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.BOOK_ROOM)
                .reply("Hiện tại không có phòng phù hợp ở tòa bạn chọn.")
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

        String startLabel = formatDateTime(startTime);
        String endLabel = formatDateTime(endTime);
        for (Room room : candidates) {
            if (room.getId() == null) continue;
            if (busyRoomIds.contains(room.getId())) continue;

            ReservationRequest reservationRequest = new ReservationRequest();
            reservationRequest.setRoomId(room.getId());
            reservationRequest.setStartTime(startTime);
            reservationRequest.setEndTime(endTime);
            reservationRequest.setPurpose("Họp");
            reservationRequest.setNote("Đặt qua chatbot (tự chọn)");

            try {
                ReservationResponse reservation = reservationService.reserveRoom(reservationRequest, authentication);
                String reply = "Đặt 1 phòng bất kì phù hợp với tiêu chí. Phòng " + room.getLocationCode() + " từ "
                    + startLabel + " đến " + endLabel + ".";

            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.BOOK_ROOM)
                .reply(reply)
                .reservation(reservation)
                .build();
            } catch (CustomException e) {
            if (e.getResponseCode() == ResponseCode.CANNOT_RESERVE_ROOM
                || e.getResponseCode() == ResponseCode.RESERVATION_TIME_OVERLAP
                || e.getResponseCode() == ResponseCode.USER_TIME_OVERLAP) {
                continue;
            }
            throw e;
            }
        }

        return ChatbotMessageResponse.builder()
            .intent(ChatbotIntent.BOOK_ROOM)
            .reply("Mình không tìm thấy phòng trống cho khung giờ bạn yêu cầu.")
            .build();
        }

        private ChatbotMessageResponse extendReservationWithRoomCode(
            String message,
            String roomCode,
            double hours,
            Authentication authentication,
            String sessionId
        ) {
        if (authentication == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        var user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));

        Reservation target = findTargetReservationForAction(user.getId(), roomCode);
        if (target == null) {
            ExtendFlowState retry = new ExtendFlowState();
            retry.step = ExtendStep.ASK_ROOM;
            extendFlowStates.put(sessionId, retry);

            return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.EXTEND_RESERVATION)
                .reply("Mình chưa tìm thấy đặt phòng đang hoạt động cho phòng này. Vui lòng nhập lại mã phòng.")
                .build();
        }

        reservationService.extendReservation(target.getId(), hours, authentication);

        Reservation updated = reservationRepository.findById(target.getId()).orElse(target);
        String updatedWindow = formatReservationWindow(updated);
        String hourText = (Math.floor(hours) == hours) ? String.valueOf((int) hours) : String.valueOf(hours);

        String reply = "Đã gia hạn thêm " + hourText + " giờ cho phòng " + roomCode + " thành công."
            + (updatedWindow.isBlank() ? "" : " Khung giờ mới: " + updatedWindow + ".");

        return ChatbotMessageResponse.builder()
            .intent(ChatbotIntent.EXTEND_RESERVATION)
            .reply(reply)
            .build();
        }

    private String localizeResponseCode(ResponseCode code) {
        if (code == null) return null;
        return switch (code) {
            case CANNOT_RESERVE_ROOM -> "Khung giờ này đã có người đặt phòng.";
            case RESERVATION_TIME_OVERLAP -> "Khung giờ bị trùng với đặt phòng khác.";
            case USER_TIME_OVERLAP -> "Bạn đã có lịch đặt phòng trong khung giờ này.";
            case ROOM_NOT_AVAILABLE -> "Phòng này hiện không khả dụng.";
            case ROOM_BROKEN -> "Phòng đang bị hỏng và không thể sử dụng.";
            case ROOM_IN_ACADEMIC_SCHEDULE -> "Phòng đang trong thời gian học cố định.";
            case ACADEMIC_SCHEDULE_OVERLAP -> "Lịch học cố định bị trùng khung thời gian cho phòng này.";
            case RESERVATION_NOT_IN_USE -> "Đặt phòng hiện không ở trạng thái sử dụng.";
            case RESERVATION_NOT_RESERVED -> "Đặt phòng này chưa ở trạng thái đã đặt.";
            case RESERVATION_INVALID_STATUS -> "Trạng thái đặt phòng không hợp lệ.";
            case RESERVATION_INVALID_HOUR -> "Thời lượng không hợp lệ.";
            case RESERVATION_NOT_EXTEND_AFTER_MIDNIGHT -> "Không thể gia hạn qua nửa đêm.";
            case USER_TIME_EXCEEDED -> "Bạn đã vượt quá thời lượng đặt phòng trong ngày.";
            case ACCESS_DENIED -> "Bạn không có quyền thực hiện thao tác này.";
            case USER_NOT_FOUND -> "Không tìm thấy người dùng.";
            case ROOM_NOT_FOUND -> "Không tìm thấy phòng.";
            default -> null;
        };
    }

    private String localizeReservationStatus(ReservationStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PENDING -> "Chờ xác nhận";
            case RESERVED -> "Đã đặt";
            case IN_USE -> "Đang sử dụng";
            case COMPLETED -> "Hoàn thành";
            case CANCELLED -> "Đã hủy";
            case NO_SHOW -> "Không đến";
            case PAYING -> "Đang thanh toán";
            case FAILED -> "Thất bại";
            case FORCE_CANCELLED -> "Bị hủy";
        };
    }

    private String humanizeDateVi(LocalDate date) {
        if (date == null) return "";
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "hôm nay";
        if (date.equals(today.plusDays(1))) return "ngày mai";
        return "ngày " + date;
    }

    private enum BookingStep {
        ASK_BUILDING,
        ASK_TIME,
        ASK_DURATION,
        ASK_CAPACITY
    }

    private static class BookingFlowState {
        private BookingStep step;
        private String buildingId;
        private LocalDate date;
        private LocalTime startTime;
        private Double durationHours;
    }

    private enum ExtendStep {
        ASK_SELECTION,
        ASK_ROOM,
        ASK_DURATION
    }

    private static class ExtendFlowState {
        private ExtendStep step;
        private String roomCode;
        private List<Reservation> reservations;
    }

    private enum CancelStep {
        ASK_SELECTION
    }

    private static class CancelFlowState {
        private CancelStep step;
        private List<Reservation> reservations;
    }

    private enum LookupAction {
        NONE,
        HISTORY,
        AVAILABLE_NOW,
        ROOM_DETAIL,
        CAPACITY_RANGE
    }

    private enum LookupStep {
        ASK_ROOM_CODE,
        ASK_CAPACITY_RANGE
    }

    private static class LookupFlowState {
        private LookupStep step;
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

}