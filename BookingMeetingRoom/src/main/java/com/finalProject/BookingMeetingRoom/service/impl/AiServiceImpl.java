package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.enums.SenderType;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Chat_history;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.AiChatRequest;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomSearchRequest;
import com.finalProject.BookingMeetingRoom.model.response.AiChatResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomSearchResponse;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.AiService;
import com.finalProject.BookingMeetingRoom.service.ChatHistoryService;
import com.finalProject.BookingMeetingRoom.service.ReservationService;
import com.finalProject.BookingMeetingRoom.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final Logger logger = LoggerFactory.getLogger(AiServiceImpl.class);

    private final RoomService roomService;
    private final ReservationService reservationService;
    private final RoomRepository roomRepository;
    private final ChatHistoryService chatHistoryService;
    private final UserRepository userRepository;

    @Override
    public AiChatResponse chat(AiChatRequest request, Authentication authentication) {
        try {
            String originalMessage = request.getMessage() == null ? "" : request.getMessage();
            String message = originalMessage.toLowerCase(Locale.ROOT);

            // ensure session id
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = UUID.randomUUID().toString();
            }

            // persist user message
            Chat_history userChat = new Chat_history();
            userChat.setSessionId(sessionId);
            userChat.setSender(SenderType.USER);
            userChat.setMessage(originalMessage);
            // set user if available
            if (authentication != null && authentication.getName() != null) {
                userRepository.findByEmail(authentication.getName()).ifPresent(userChat::setUser);
            }
            chatHistoryService.save(userChat);

            // 1. Nếu user muốn đặt phòng ("đặt phòng", "book", "reserve" ...)
            if (containsAny(message, "đặt phòng", "dat phong", "book", "reserve", "đặt lịch", "dat lich")
                    || looksLikeImmediateReservation(message)) {

                // Try to parse room and datetime from natural language when FE doesn't provide structured data
                String parsedRoomId = null;
                LocalDateTime parsedStart = null;
                LocalDateTime parsedEnd = null;

                // If request already contains structured values, use them
                if (request.getStartTime() != null && request.getEndTime() != null) {
                    parsedStart = request.getStartTime();
                    parsedEnd = request.getEndTime();
                } else {
                    parsedRoomId = parseRoomIdFromMessage(originalMessage);
                    parsedStart = parseDateTimeFromMessage(originalMessage);
                    if (parsedStart != null) {
                        parsedEnd = parsedStart.plusHours(1); // default 1 hour
                    }
                }

                // If we parsed a room + start, try to reserve immediately
                if (parsedRoomId != null && parsedStart != null && parsedEnd != null) {
                    try {
                        ReservationRequest reservationRequest = new ReservationRequest();
                        reservationRequest.setRoomId(parsedRoomId);
                        reservationRequest.setStartTime(parsedStart);
                        reservationRequest.setEndTime(parsedEnd);
                        reservationRequest.setPurpose("Đặt qua AI (NL parsing)");

                        ReservationResponse reservation = reservationService.reserveRoom(reservationRequest, authentication);
                        String reply = "Tôi đã giúp bạn đặt phòng " + parsedRoomId + " từ " + parsedStart + " đến " + parsedEnd + ". Mã: " + reservation.getId();

                        Chat_history botSuccess = new Chat_history();
                        botSuccess.setSessionId(sessionId);
                        botSuccess.setSender(SenderType.BOT);
                        botSuccess.setMessage(reply);
                        if (authentication != null && authentication.getName() != null) {
                            userRepository.findByEmail(authentication.getName()).ifPresent(botSuccess::setUser);
                        }
                        chatHistoryService.save(botSuccess);

                        return AiChatResponse.builder()
                                .reply(reply)
                                .reservationCreated(true)
                                .reservation(reservation)
                                .build();
                    } catch (CustomException ex) {
                        // reservation failed (room invalid or already booked)
                        String reply = "Không thể đặt phòng " + parsedRoomId + ". Lý do: " + ex.getResponseCode().getMessage();
                        Chat_history botFail = new Chat_history();
                        botFail.setSessionId(sessionId);
                        botFail.setSender(SenderType.BOT);
                        botFail.setMessage(reply);
                        if (authentication != null && authentication.getName() != null) {
                            userRepository.findByEmail(authentication.getName()).ifPresent(botFail::setUser);
                        }
                        chatHistoryService.save(botFail);
                        return AiChatResponse.builder()
                                .reply(reply)
                                .reservationCreated(false)
                                .build();
                    }
                }

                // fallback to structured booking flow (ask for missing info)
                AiChatResponse response = handleBookingIntent(request, authentication);
                // persist bot reply
                Chat_history botChat = new Chat_history();
                botChat.setSessionId(sessionId);
                botChat.setSender(SenderType.BOT);
                botChat.setMessage(response.getReply());
                if (authentication != null && authentication.getName() != null) {
                    userRepository.findByEmail(authentication.getName()).ifPresent(botChat::setUser);
                }
                chatHistoryService.save(botChat);
                return response;
            }

            // 2. Nếu user chỉ muốn gợi ý phòng trống theo sức chứa / tiện ích
            if (containsAny(message, "gợi ý", "goi y", "phòng trống", "phong trong", "suggest")) {
                AiChatResponse response = handleSuggestionIntent(request);
                Chat_history botChat = new Chat_history();
                botChat.setSessionId(sessionId);
                botChat.setSender(SenderType.BOT);
                botChat.setMessage(response.getReply());
                if (authentication != null && authentication.getName() != null) {
                    userRepository.findByEmail(authentication.getName()).ifPresent(botChat::setUser);
                }
                chatHistoryService.save(botChat);
                return response;
            }

            // 3. Mặc định: tư vấn chung
            String reply = "Xin chào, tôi có thể giúp bạn tìm và đặt phòng họp. " +
                    "Bạn có thể cho tôi biết thời gian (từ - đến), số người và các tiện ích mong muốn?";

            Chat_history botChat = new Chat_history();
            botChat.setSessionId(sessionId);
            botChat.setSender(SenderType.BOT);
            botChat.setMessage(reply);
            if (authentication != null && authentication.getName() != null) {
                userRepository.findByEmail(authentication.getName()).ifPresent(botChat::setUser);
            }
            chatHistoryService.save(botChat);

            return AiChatResponse.builder()
                    .reply(reply)
                    .reservationCreated(false)
                    .build();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public AiChatResponse reserveViaAi(ReservationRequest request, Authentication authentication) {
        try {
            // persist user intent message summarizing the reservation
            String sessionId = UUID.randomUUID().toString();
            Chat_history userChat = new Chat_history();
            userChat.setSessionId(sessionId);
            userChat.setSender(SenderType.USER);
            userChat.setMessage("Reserve request: roomId=" + request.getRoomId() + ", start=" + request.getStartTime() + ", end=" + request.getEndTime());
            if (authentication != null && authentication.getName() != null) {
                userRepository.findByEmail(authentication.getName()).ifPresent(userChat::setUser);
            }
            chatHistoryService.save(userChat);

            ReservationResponse reservation = reservationService.reserveRoom(request, authentication);
            String reply = "Tôi đã giúp bạn đặt phòng thành công. Mã đặt chỗ: " + reservation.getId();

            Chat_history botChat = new Chat_history();
            botChat.setSessionId(sessionId);
            botChat.setSender(SenderType.BOT);
            botChat.setMessage(reply);
            if (authentication != null && authentication.getName() != null) {
                userRepository.findByEmail(authentication.getName()).ifPresent(botChat::setUser);
            }
            chatHistoryService.save(botChat);

            return AiChatResponse.builder()
                    .reply(reply)
                    .reservationCreated(true)
                    .reservation(reservation)
                    .build();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    private AiChatResponse handleBookingIntent(AiChatRequest request, Authentication authentication) {
        if (request.getStartTime() == null || request.getEndTime() == null || request.getCapacity() == null) {
            String reply = "Bạn muốn đặt phòng, vui lòng cung cấp thời gian bắt đầu, thời gian kết thúc và số người tham dự.";
            return AiChatResponse.builder()
                    .reply(reply)
                    .reservationCreated(false)
                    .build();
        }

        // Tìm một phòng phù hợp theo capacity và còn trống
        List<Room> candidates = roomRepository.findAll().stream()
                .filter(r -> r.getCapacity() != null && r.getCapacity() >= request.getCapacity())
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return AiChatResponse.builder()
                    .reply("Hiện tại không tìm thấy phòng nào đủ sức chứa cho " + request.getCapacity() + " người.")
                    .reservationCreated(false)
                    .build();
        }

        // Dùng RoomService để kiểm tra trạng thái phòng theo thời gian
        // (ở đây demo: lấy phòng đầu tiên còn AVAILABLE)
        for (Room room : candidates) {
            RoomSearchRequest searchReq = new RoomSearchRequest();
            searchReq.setFloorId(room.getFloor().getId());
            searchReq.setStartTime(request.getStartTime());
            searchReq.setEndTime(request.getEndTime());

            List<RoomSearchResponse> result = roomService.searchRooms(searchReq);
            boolean available = result.stream()
                    .anyMatch(r -> r.getRoomId().equals(room.getId()) && r.getStatus() == RoomStatus.AVAILABLE);
            if (available) {
                // Tự động tạo reservation cho user
                ReservationRequest reservationRequest = new ReservationRequest();
                reservationRequest.setRoomId(room.getId());
                reservationRequest.setStartTime(request.getStartTime());
                reservationRequest.setEndTime(request.getEndTime());
                reservationRequest.setPurpose("Đặt qua AI");

                ReservationResponse reservation = reservationService.reserveRoom(reservationRequest, authentication);
                String reply = "Tôi đã đặt giúp bạn phòng " + room.getLocationCode() +
                        " từ " + request.getStartTime() + " đến " + request.getEndTime() + ".";

                return AiChatResponse.builder()
                        .reply(reply)
                        .reservationCreated(true)
                        .reservation(reservation)
                        .build();
            }
        }

        return AiChatResponse.builder()
                .reply("Xin lỗi, không còn phòng trống phù hợp trong khung giờ bạn yêu cầu.")
                .reservationCreated(false)
                .build();
    }

    private AiChatResponse handleSuggestionIntent(AiChatRequest request) {
        if (request.getStartTime() == null || request.getEndTime() == null) {
            String reply = "Để gợi ý phòng trống, vui lòng cho tôi biết thời gian bắt đầu và kết thúc.";
            return AiChatResponse.builder()
                    .reply(reply)
                    .reservationCreated(false)
                    .build();
        }

        // Ở đây demo: lấy toàn bộ phòng theo từng tầng, sau đó lọc theo capacity nếu có
        List<Room> allRooms = roomRepository.findAll();

        List<RoomSearchResponse> suggestions = allRooms.stream()
                .filter(room -> request.getCapacity() == null
                        || (room.getCapacity() != null && room.getCapacity() >= request.getCapacity()))
                .map(room -> {
                    RoomSearchRequest searchReq = new RoomSearchRequest();
                    searchReq.setFloorId(room.getFloor().getId());
                    searchReq.setStartTime(request.getStartTime());
                    searchReq.setEndTime(request.getEndTime());
                    List<RoomSearchResponse> result = roomService.searchRooms(searchReq);
                    return result.stream()
                            .filter(r -> r.getRoomId().equals(room.getId()) && r.getStatus() == RoomStatus.AVAILABLE)
                            .findFirst()
                            .orElse(null);
                })
                .filter(r -> r != null)
                .collect(Collectors.toList());

        if (suggestions.isEmpty()) {
            return AiChatResponse.builder()
                    .reply("Không tìm thấy phòng trống phù hợp trong thời gian bạn yêu cầu.")
                    .reservationCreated(false)
                    .build();
        }

        String reply = "Tôi đã tìm thấy một số phòng trống phù hợp. Bạn có thể chọn một trong các phòng sau để đặt:";
        return AiChatResponse.builder()
                .reply(reply)
                .suggestions(suggestions)
                .reservationCreated(false)
                .build();
    }

    private boolean containsAny(String message, String... keywords) {
        for (String k : keywords) {
            if (message.contains(k)) {
                return true;
            }
        }
        return false;
    }

    // Heuristics: check if message likely contains a room code + time phrase to try immediate reservation
    private boolean looksLikeImmediateReservation(String message) {
        return parseRoomIdFromMessage(message) != null && parseDateTimeFromMessage(message) != null;
    }

    private String parseRoomIdFromMessage(String message) {
        if (message == null) return null;
        // common room codes like Z101, A12, R-10 etc.
        Pattern p = Pattern.compile("\\b([A-Za-z]{1,2}[-]?\\d{1,4})\\b");
        Matcher m = p.matcher(message);
        if (m.find()) {
            return m.group(1).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private LocalDateTime parseDateTimeFromMessage(String message) {
        if (message == null) return null;
        // normalize
        String msg = message.toLowerCase(Locale.ROOT);

        // detect relative day: mai (tomorrow), hôm nay/t hom nay (today)
        LocalDate baseDate = LocalDate.now();
        if (msg.contains("mai")) {
            baseDate = baseDate.plusDays(1);
        } else if (msg.contains("hôm nay") || msg.contains("hom nay") || msg.contains("hien tai")) {
            baseDate = baseDate;
        } // else default to today

        // find hour like "10 giờ", "10h", "10:00"
        Pattern hourPattern = Pattern.compile("(\\d{1,2})\\s*(?:h|giờ|g)\\b");
        Matcher hm = hourPattern.matcher(msg);
        if (hm.find()) {
            int hour = Integer.parseInt(hm.group(1));
            // determine AM/PM from words
            if (msg.contains("sáng") && hour == 12) hour = 0;
            if (msg.contains("chiều") || msg.contains("tối") || msg.contains("pm")) {
                if (hour < 12) hour += 12;
            }
            LocalTime time = LocalTime.of(hour, 0);
            return LocalDateTime.of(baseDate, time);
        }

        // pattern with colon
        Pattern colonPattern = Pattern.compile("(\\d{1,2}):(\\d{2})");
        Matcher cm = colonPattern.matcher(msg);
        if (cm.find()) {
            int hour = Integer.parseInt(cm.group(1));
            int minute = Integer.parseInt(cm.group(2));
            if (msg.contains("chiều") || msg.contains("tối") || msg.contains("pm")) {
                if (hour < 12) hour += 12;
            }
            LocalTime time = LocalTime.of(hour, minute);
            return LocalDateTime.of(baseDate, time);
        }

        // phrases like "10 giờ sáng mai" may be detected by the hourPattern + "sáng" handled above
        return null;
    }
}
