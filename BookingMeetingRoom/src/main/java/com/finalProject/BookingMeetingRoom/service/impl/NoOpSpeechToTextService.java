package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.service.SpeechToTextService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class NoOpSpeechToTextService implements SpeechToTextService {

    @Override
    public String transcribe(MultipartFile audio, String languageHint) {
        throw new CustomException(ResponseCode.VALIDATION_FAILED,
                "Speech-to-text is not configured on the backend. Please send a transcript instead.");
    }
}
