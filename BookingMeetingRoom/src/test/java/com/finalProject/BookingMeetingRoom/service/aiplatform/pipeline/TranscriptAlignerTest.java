package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.CleanedTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.RawTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.TranscriptSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptAlignerTest {

    @Test
    void align_shouldReturnEmptyCleaned_whenRawNull() {
        TranscriptAligner aligner = new TranscriptAligner();

        CleanedTranscript result = aligner.align(null, null);

        assertNotNull(result);
        assertEquals(0, result.getSpeakerCount());
        assertEquals("", result.getFullText());
    }

    @Test
    void align_shouldAssignSpeakerFromDiarization() {
        TranscriptAligner aligner = new TranscriptAligner();
        RawTranscript raw = RawTranscript.builder()
                .segments(List.of(
                        TranscriptSegment.builder().segmentId("s1").start(0.0).end(1.0).text("hello").build(),
                        TranscriptSegment.builder().segmentId("s2").start(2.0).end(3.0).text("world").build()
                ))
                .language("vi")
                .durationSeconds(3.0)
                .build();

        List<GapBasedDiarizer.DiarSegment> diar = List.of(
                new GapBasedDiarizer.DiarSegment("Speaker A", 0.0, 1.5, 0.8),
                new GapBasedDiarizer.DiarSegment("Speaker B", 2.0, 3.5, 0.9)
        );

        CleanedTranscript result = aligner.align(raw, diar);

        assertEquals(2, result.getSegments().size());
        assertEquals("Speaker A", result.getSegments().get(0).getSpeaker());
        assertEquals("Speaker B", result.getSegments().get(1).getSpeaker());
        assertEquals(2, result.getSpeakerCount());
        assertTrue(result.getFullText().contains("Speaker A: hello"));
    }

    @Test
    void align_shouldFallbackToUnknownSpeaker_whenNoDiarization() {
        TranscriptAligner aligner = new TranscriptAligner();
        RawTranscript raw = RawTranscript.builder()
                .segments(List.of(TranscriptSegment.builder().start(0.0).end(1.0).text("hello").build()))
                .build();

        CleanedTranscript result = aligner.align(raw, List.of());

        assertEquals("UNKNOWN", result.getSegments().get(0).getSpeaker());
        assertEquals(0.5, result.getSegments().get(0).getSpeakerConfidence());
    }
}
