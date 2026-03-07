package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.AiChatRequest;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomSearchRequest;
import com.finalProject.BookingMeetingRoom.model.response.AiChatResponse;
import com.finalProject.BookingMeetingRoom.model.response.ReservationResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomSearchResponse;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.AiService;
import com.finalProject.BookingMeetingRoom.service.ReservationService;
import com.finalProject.BookingMeetingRoom.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final Logger logger = LoggerFactory.getLogger(AiServiceImpl.class);

    private final RoomService roomService;
    private final ReservationService reservationService;
    private final RoomRepository roomRepository;

    @Override
    public AiChatResponse chat(AiChatRequest request, Authentication authentication) {
        try {
            String message = request.getMessage() != null ? request.getMessage().toLowerCase(Locale.ROOT) : "";

            // 1. Nếu user muốn đặt phòng ("đặt phòng", "book", "reserve" ...)
            if (containsAny(message, "đặt phòng", "dat phong", "book", "reserve", "đặt lịch", "dat lich")) {
                return handleBookingIntent(request, authentication);
            }

            // 2. Nếu user chỉ muốn gợi ý phòng trống theo sức chứa / tiện ích
            if (containsAny(message, "gợi ý", "goi y", "phòng trống", "phong trong", "suggest")) {
                return handleSuggestionIntent(request);
            }

            // 3. Mặc định: tư vấn chung
            String reply = "Xin chào, tôi có thể giúp bạn tìm và đặt phòng họp. " +
                    "Bạn có thể cho tôi biết thời gian (từ - đến), số người và các tiện ích mong muốn?";

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
            ReservationResponse reservation = reservationService.reserveRoom(request, authentication);
            String reply = "Tôi đã giúp bạn đặt phòng thành công. Mã đặt chỗ: " + reservation.getId();
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
}
