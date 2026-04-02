package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.ChatbotIntent;
import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.common.utils.ChatbotMessageParser;
import com.finalProject.BookingMeetingRoom.model.entity.Amenity;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.RoomImage;
import com.finalProject.BookingMeetingRoom.model.request.ChatbotMessageRequest;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotMessageResponse;
import com.finalProject.BookingMeetingRoom.model.response.ChatbotRoomItemResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomImageRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.ChatHistoryService;
import com.finalProject.BookingMeetingRoom.service.ChatbotService;
import com.finalProject.BookingMeetingRoom.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatbotServiceImpl implements ChatbotService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final RoomRepository roomRepository;
    private final RoomImageRepository roomImageRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final ChatbotRoomSuggestionEngine suggestionEngine;
    private final UserRepository userRepository;
    private final ChatHistoryService chatHistoryService;

    private final ChatbotMessageParser parser = new ChatbotMessageParser();

    @Override
    public ChatbotMessageResponse handleMessage(ChatbotMessageRequest request, Authentication authentication) {
        try {
            String message = request != null ? request.getMessage() : null;
            String sessionId = chatHistoryService.ensureSessionId(request != null ? request.getSessionId() : null);

            // Fetch recent USER messages before logging the current one (avoid echoing it as context).
            List<String> recentUserMessages = chatHistoryService.getRecentMessages(sessionId, SenderType.USER, 5);

            var user = (authentication != null)
                ? userRepository.findByEmail(authentication.getName()).orElse(null)
                : null;

            // Log USER message (best-effort)
            chatHistoryService.log(user, sessionId, SenderType.USER, message);

            if (message == null || message.isBlank()) {
            ChatbotMessageResponse res = ChatbotMessageResponse.builder()
                .sessionId(sessionId)
                        .intent(ChatbotIntent.FALLBACK)
                        .reply("Please type a message. For example: 'Today available rooms?' or 'Book AL-102 at 10AM today'.")
                        .build();
            chatHistoryService.log(user, sessionId, SenderType.BOT, res.getReply());
            return res;
            }

            ChatbotMessageParser.ParseResult parsed = parser.parse(message);
            ChatbotMessageParser.ParseResult effectiveParsed = mergeWithContext(parsed, recentUserMessages);

            ChatbotMessageResponse response = switch (effectiveParsed.intent()) {
                case CHECK_AVAILABLE_ROOMS_TODAY -> handleAvailableRoomsToday(message, effectiveParsed);
                case SUGGEST_ROOMS_BY_CAPACITY -> handleSuggestRoomsByCapacity(message, effectiveParsed);
                case BOOK_ROOM -> handleBookRoom(message, effectiveParsed, authentication);
                default -> handleFallback(message);
            };

            if (response != null) {
                response.setSessionId(sessionId);
                chatHistoryService.log(user, sessionId, SenderType.BOT, response.getReply());
            }
            return response;
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected chatbot error", e);
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
        String reply = pickByHash(message,
                "I can help you check available rooms today or book a room. Try: 'Today available rooms?' or 'Book AL-102 at 10AM today'.",
                "Tell me what you need: (1) available rooms today, or (2) book a specific room (e.g. 'Book AL-102 from 14:00 to 15:00 today').",
                "I can search available rooms today and create bookings for you. What would you like to do?"
        );

        return ChatbotMessageResponse.builder()
                .intent(ChatbotIntent.FALLBACK)
                .reply(reply)
                .build();
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
        LocalDate day = parsed != null && parsed.date() != null ? parsed.date() : LocalDate.now();
        LocalDateTime startOfDay = day.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime requestedStart = (parsed != null && parsed.startTime() != null)
                ? LocalDateTime.of(day, parsed.startTime())
                : now;

        LocalDateTime windowStart = max(startOfDay, max(now, requestedStart));

        if (!windowStart.isBefore(endOfDay)) {
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY)
                    .reply("Today is almost over — please try a different date/time.")
                    .availableRooms(List.of())
                    .build();
        }

        List<Room> rooms = roomRepository.findAllWithDetails().stream()
                .filter(r -> r.getStatus() != RoomStatus.BROKEN)
                .filter(r -> r.getFloor() == null || !r.getFloor().isDeleted())
                .filter(r -> r.getFloor() == null || r.getFloor().getBuilding() == null || !r.getFloor().getBuilding().isDeleted())
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

        Map<String, List<Reservation>> reservationsByRoom = overlaps.stream()
                .collect(Collectors.groupingBy(r -> r.getRoom().getId()));

        List<ChatbotRoomItemResponse> available = new ArrayList<>();

        for (Room room : rooms) {
            List<Reservation> busy = reservationsByRoom.getOrDefault(room.getId(), List.of());
            List<TimeRange> freeRanges = computeFreeRanges(windowStart, endOfDay, busy);
            if (freeRanges.isEmpty()) continue;

                available.add(toRoomItem(
                    room,
                    freeRanges.stream().limit(3).map(TimeRange::format).toList(),
                    roomIdToImageUrl.get(room.getId())
                ));
        }

        if (available.isEmpty()) {
            String reply = pickByHash(message,
                    "I couldn’t find any rooms available for the rest of today. Want to try a different time or date?",
                    "All rooms look booked for the remaining hours today. You can ask for another date/time window.",
                    "No available rooms for the rest of today. Try another time slot or tomorrow."
            );

            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY)
                    .reply(reply)
                    .availableRooms(List.of())
                    .build();
        }

        String reply;
        if (parsed != null && parsed.startTime() != null) {
            String t = parsed.startTime().format(TIME_FMT);
            reply = pickByHash(message,
                "Rooms available as of " + t + " today:",
                "Here are rooms that have free time from " + t + " today:",
                "These rooms are available starting " + t + " today:"
            );
        } else {
            reply = pickByHash(message,
                "Here are rooms that still have free time today:",
                "I found some rooms with open slots today:",
                "These rooms are available later today:"
            );
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
            return ChatbotMessageResponse.builder()
                    .intent(ChatbotIntent.BOOK_ROOM)
                    .reply("What time should I book it for? For example: 'Book " + parsed.roomCode() + " at 10AM today' or 'from 14:00 to 15:00 today'.")
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
}
