package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.ActionItem;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.CleanedTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.TranscriptSegment;
import com.finalProject.BookingMeetingRoom.service.aiplatform.AiLlmService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionItemExtractorTest {

    @Mock
    private AiLlmService aiLlmService;

    @Test
    void extract_shouldReturnEmpty_whenTranscriptMissing() {
        ActionItemExtractor extractor = new ActionItemExtractor(aiLlmService);

        assertTrue(extractor.extract(null).isEmpty());
        assertTrue(extractor.extract(CleanedTranscript.builder().segments(List.of()).build()).isEmpty());
    }

    @Test
    void extract_shouldReturnItems_fromJson() throws Exception {
        ActionItemExtractor extractor = new ActionItemExtractor(aiLlmService);
        CleanedTranscript transcript = CleanedTranscript.builder()
                .segments(List.of(TranscriptSegment.builder().segmentId("s1").speaker("A").text("do it").build()))
                .build();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode items = mapper.createArrayNode();
        ObjectNode shortTitle = mapper.createObjectNode();
        shortTitle.put("title", "do");
        items.add(shortTitle);
        ObjectNode good = mapper.createObjectNode();
        good.put("title", "Prepare report");
        good.put("description", "weekly summary");
        good.put("assignee", "alex");
        good.put("due_date", "2026-06-10");
        ArrayNode ids = mapper.createArrayNode();
        ids.add("s1");
        good.set("source_segment_ids", ids);
        items.add(good);
        root.set("items", items);

        when(aiLlmService.runJson(any(), any(), anyDouble())).thenReturn(root);

        List<ActionItem> results = extractor.extract(transcript);

        assertEquals(1, results.size());
        assertEquals("Prepare report", results.get(0).getTitle());
        assertEquals(1, results.get(0).getSourceSegmentIds().size());
    }

    @Test
    void extract_shouldReturnEmpty_whenJsonInvalid() {
        ActionItemExtractor extractor = new ActionItemExtractor(aiLlmService);
        CleanedTranscript transcript = CleanedTranscript.builder()
                .segments(List.of(TranscriptSegment.builder().segmentId("s1").speaker("A").text("do it").build()))
                .build();

        when(aiLlmService.runJson(any(), any(), anyDouble())).thenReturn((JsonNode) null);

        assertTrue(extractor.extract(transcript).isEmpty());
    }
}
