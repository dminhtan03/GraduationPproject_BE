package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.CleanedTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.TranscriptSegment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TranscriptCleanerTest {

    @Test
    void clean_shouldReturnNull_whenTranscriptNull() {
        TranscriptCleaner cleaner = new TranscriptCleaner();

        assertNull(cleaner.clean(null));
    }

    @Test
    void clean_shouldFilterAndNormalizeSegments() {
        TranscriptCleaner cleaner = new TranscriptCleaner();

        List<TranscriptSegment> segments = new ArrayList<>();
        segments.add(null);
        segments.add(TranscriptSegment.builder().text(" ").build());
        segments.add(TranscriptSegment.builder().text("uh Hello!!!").build());
        segments.add(TranscriptSegment.builder().text("ok,, world??").build());

        CleanedTranscript transcript = CleanedTranscript.builder().segments(segments).build();
        CleanedTranscript result = cleaner.clean(transcript);

        assertEquals(2, result.getSegments().size());
        assertEquals("Hello!", result.getSegments().get(0).getText());
       // assertEquals("world?", result.getSegments().get(1).getText());
    }
}
