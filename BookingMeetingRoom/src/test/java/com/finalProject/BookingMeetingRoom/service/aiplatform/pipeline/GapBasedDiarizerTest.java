package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.RawTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.TranscriptSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GapBasedDiarizerTest {

    @Test
    void diarizeFromTranscript_shouldReturnEmpty_forNullOrEmpty() {
        GapBasedDiarizer diarizer = new GapBasedDiarizer();

        assertTrue(diarizer.diarizeFromTranscript(null).isEmpty());
        assertTrue(diarizer.diarizeFromTranscript(RawTranscript.builder().segments(List.of()).build()).isEmpty());
    }

    @Test
    void diarizeFromTranscript_shouldRotateSpeakerOnGap() {
        GapBasedDiarizer diarizer = new GapBasedDiarizer();
        RawTranscript raw = RawTranscript.builder()
                .segments(List.of(
                        TranscriptSegment.builder().start(0.0).end(1.0).text("a").build(),
                        TranscriptSegment.builder().start(2.1).end(3.0).text("b").build()
                ))
                .build();

        var diar = diarizer.diarizeFromTranscript(raw);

        assertEquals(2, diar.size());
        assertEquals("Nguoi 1", diar.get(0).getSpeaker());
        assertEquals("Nguoi 2", diar.get(1).getSpeaker());
    }
}
