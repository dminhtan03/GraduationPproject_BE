package com.finalProject.BookingMeetingRoom.service.speech;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.service.impl.NoOpSpeechToTextService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoOpSpeechToTextServiceTest {

    private final NoOpSpeechToTextService service = new NoOpSpeechToTextService();

    @Test
    void transcribe_shouldThrowValidationFailed() {
        MockMultipartFile audio = new MockMultipartFile("audio", "a.wav", "audio/wav", "x".getBytes());

        CustomException ex = assertThrows(CustomException.class,
                () -> service.transcribe(audio, "vi"));

        assertEquals(ResponseCode.VALIDATION_FAILED, ex.getResponseCode());
    }
}
