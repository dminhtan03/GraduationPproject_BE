package com.finalProject.BookingMeetingRoom.controller.ai;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.AiChatRequest;
import com.finalProject.BookingMeetingRoom.model.request.ReservationRequest;
import com.finalProject.BookingMeetingRoom.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /**
     * API chat tổng quát với AI: tư vấn, gợi ý phòng, hoặc đặt phòng giúp user.
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody AiChatRequest request, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(aiService.chat(request, authentication)));
    }

    /**
     * API đặt phòng thông qua AI khi FE đã parse được đầy đủ thông tin đặt phòng.
     */
    @PostMapping("/reserve")
    public ResponseEntity<?> reserveViaAi(@RequestBody ReservationRequest request, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(aiService.reserveViaAi(request, authentication)));
    }
}
