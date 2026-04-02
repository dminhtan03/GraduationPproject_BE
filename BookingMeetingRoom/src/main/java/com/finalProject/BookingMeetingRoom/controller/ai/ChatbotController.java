package com.finalProject.BookingMeetingRoom.controller.ai;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.ChatbotMessageRequest;
import com.finalProject.BookingMeetingRoom.service.ChatbotService;
import com.finalProject.BookingMeetingRoom.service.SpeechToTextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final SpeechToTextService speechToTextService;

    @PostMapping("/message")
    public ResponseEntity<?> message(@RequestBody @Valid ChatbotMessageRequest request, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(chatbotService.handleMessage(request, authentication)));
    }

    /**
     * Voice booking endpoint.
     * - If `transcript` is provided, it will be used directly.
     * - Otherwise, `audio` will be transcribed by {@link SpeechToTextService}.
     */
    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> voice(
            @RequestPart(value = "audio", required = false) MultipartFile audio,
            @RequestPart(value = "transcript", required = false) String transcript,
            @RequestPart(value = "sessionId", required = false) String sessionId,
            @RequestPart(value = "language", required = false) String language,
            Authentication authentication
    ) {
        String finalTranscript = (transcript != null && !transcript.isBlank())
                ? transcript
                : (audio != null ? speechToTextService.transcribe(audio, language) : null);

        ChatbotMessageRequest req = ChatbotMessageRequest.builder()
                .message(finalTranscript)
                .sessionId(sessionId)
                .build();

        return ResponseEntity.ok(Response.ofSucceeded(chatbotService.handleMessage(req, authentication)));
    }
}
