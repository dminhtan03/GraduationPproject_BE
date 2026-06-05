package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.CleanedTranscript;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

class TranscriptFilterTest {

    @Test
    void filter_shouldReturnSameInstance() {
        TranscriptFilter filter = new TranscriptFilter();
        CleanedTranscript input = CleanedTranscript.builder().segments(List.of()).build();

        CleanedTranscript result = filter.filter(input);

        assertSame(input, result);
    }
}
