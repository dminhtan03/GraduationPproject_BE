package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.ExtractedTaskItem;
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
class ChunkedTaskExtractorTest {

    @Mock
    private AiLlmService aiLlmService;

    @Test
    void extract_shouldReturnEmpty_whenTranscriptTooShort() {
        ChunkedTaskExtractor extractor = new ChunkedTaskExtractor(aiLlmService);

        assertTrue(extractor.extract("short").isEmpty());
    }

    @Test
    void extract_shouldFilterDuplicatesAndLowConfidence() throws Exception {
        ChunkedTaskExtractor extractor = new ChunkedTaskExtractor(aiLlmService, "vi");
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode array = mapper.createArrayNode();

        ObjectNode lowConf = mapper.createObjectNode();
        lowConf.put("title", "Task A");
        lowConf.put("ai_confidence", 0.2);
        array.add(lowConf);

        ObjectNode good = mapper.createObjectNode();
        good.put("title", "Task B");
        good.put("ai_confidence", 0.8);
        array.add(good);

        ObjectNode dup = mapper.createObjectNode();
        dup.put("title", "Task B");
        dup.put("ai_confidence", 0.9);
        array.add(dup);

        when(aiLlmService.runJson(any(), any(), anyDouble())).thenReturn(array);

        List<ExtractedTaskItem> results = extractor.extract("this is a long enough transcript with many words for testing");

        assertEquals(1, results.size());
        assertEquals("Task B", results.get(0).getTitle());
    }
}
