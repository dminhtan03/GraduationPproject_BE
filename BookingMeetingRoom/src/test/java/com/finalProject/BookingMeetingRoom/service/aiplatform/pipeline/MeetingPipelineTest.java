package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.MeetingOutput;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.RawTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.TranscriptSegment;
import com.finalProject.BookingMeetingRoom.service.aiplatform.AiLlmService;
import com.finalProject.BookingMeetingRoom.service.aiplatform.AiSttService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingPipelineTest {

    @Mock
    private AiSttService aiSttService;

    @Mock
    private AiLlmService aiLlmService;

    @Test
    void run_shouldReturnCompletedOutput_onSuccess() {
        RawTranscript raw = RawTranscript.builder()
                .segments(List.of(TranscriptSegment.builder().start(0.0).end(1.0).text("hello").build()))
                .language("vi")
                .durationSeconds(1.0)
                .build();

        when(aiSttService.transcribeRaw(any(Path.class), eq("vi"))).thenReturn(raw);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode minutes = mapper.createObjectNode();
        minutes.put("title", "Meeting");
        minutes.put("summary", "Summary");
        ArrayNode actions = mapper.createArrayNode();
        ObjectNode actionItem = mapper.createObjectNode();
        actionItem.put("title", "Prepare report");
        actions.add(actionItem);
        ObjectNode actionRoot = mapper.createObjectNode();
        actionRoot.set("items", actions);

        when(aiLlmService.runJson(any(), any(), anyDouble()))
                .thenReturn(minutes)
                .thenReturn(actionRoot);

        MeetingPipeline pipeline = new MeetingPipeline(aiSttService, aiLlmService);
        MeetingOutput output = pipeline.run("audio.wav", "Meeting", "vi");

        assertEquals("completed", output.getStatus());
        assertNotNull(output.getTranscript());
        assertNotNull(output.getMinutes());
        assertTrue(output.getActionItems().size() == 1);
    }

    @Test
    void run_shouldReturnFailedOutput_onException() {
        when(aiSttService.transcribeRaw(any(Path.class), eq("vi")))
                .thenThrow(new RuntimeException("boom"));

        MeetingPipeline pipeline = new MeetingPipeline(aiSttService, aiLlmService);
        MeetingOutput output = pipeline.run("audio.wav", "Meeting", "vi");

        assertEquals("failed", output.getStatus());
        assertEquals("boom", output.getError());
    }
}
