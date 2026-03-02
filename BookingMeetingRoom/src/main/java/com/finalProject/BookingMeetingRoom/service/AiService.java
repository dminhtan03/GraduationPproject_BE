package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.request.AiChatRequest;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.model.response.AiChatResponse;
import org.springframework.security.core.Authentication;

public interface AiService {

    /**
     * Xử lý hội thoại với AI: tư vấn, gợi ý phòng, hoặc đặt chỗ giúp user.
     */
    AiChatResponse chat(AiChatRequest request, Authentication authentication);

    /**
     * Tạo reservation thông qua AI (trường hợp FE đã parse được thông tin).
     */
    AiChatResponse reserveViaAi(ReservationRequest request, Authentication authentication);
}
