package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.CleanedTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.MeetingMinutes;
import com.finalProject.BookingMeetingRoom.service.aiplatform.AiLlmService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinutesGeneratorTest {

    @Mock
    private AiLlmService aiLlmService;

    @Test
    void generate_shouldReturnEmptyMinutes_whenTranscriptMissing() {
        MinutesGenerator generator = new MinutesGenerator(aiLlmService);

        MeetingMinutes result = generator.generate(CleanedTranscript.builder().fullText(" ").build(), "Meeting");

        assertEquals("Meeting", result.getTitle());
        assertTrue(result.getSummary().contains("Not enough data"));
    }

    @Test
    void generate_shouldFallbackSummary_whenSummaryBlank() throws Exception {
        MinutesGenerator generator = new MinutesGenerator(aiLlmService);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        node.put("title", "Weekly");
        node.put("summary", " ");

        when(aiLlmService.runJson(any(), any(), anyDouble())).thenReturn(node);

        String longText = "x".repeat(220);
        CleanedTranscript transcript = CleanedTranscript.builder().fullText(longText).build();
        MeetingMinutes result = generator.generate(transcript, "Meeting");

        assertEquals("Weekly", result.getTitle());
        assertNotNull(result.getSummary());
        assertEquals(200, result.getSummary().length());
    }
}
