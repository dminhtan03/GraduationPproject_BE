package com.finalProject.BookingMeetingRoom.service;

import org.springframework.web.multipart.MultipartFile;

public interface SpeechToTextService {

    String transcribe(MultipartFile audio, String languageHint);
}
