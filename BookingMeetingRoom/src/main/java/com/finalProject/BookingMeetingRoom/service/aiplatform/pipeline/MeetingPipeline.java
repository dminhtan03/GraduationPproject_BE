package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.CleanedTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.MeetingMinutes;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.MeetingOutput;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.RawTranscript;
import com.finalProject.BookingMeetingRoom.service.aiplatform.AiLlmService;
import com.finalProject.BookingMeetingRoom.service.aiplatform.AiSttService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class MeetingPipeline {

    private final AiSttService aiSttService;
    private final AiLlmService aiLlmService;

    public MeetingOutput run(String audioPath, String meetingTitle, String language) {
        String jobId = UUID.randomUUID().toString();
        try {
            RawTranscript raw = aiSttService.transcribeRaw(Path.of(audioPath), language != null ? language : "vi");
            GapBasedDiarizer diarizer = new GapBasedDiarizer();
            List<GapBasedDiarizer.DiarSegment> diar = diarizer.diarizeFromTranscript(raw);

            TranscriptAligner aligner = new TranscriptAligner();
            CleanedTranscript aligned = aligner.align(raw, diar);

            TranscriptFilter filter = new TranscriptFilter();
            CleanedTranscript filtered = filter.filter(aligned);

            TranscriptCleaner cleaner = new TranscriptCleaner();
            CleanedTranscript cleaned = cleaner.clean(filtered);

            MinutesGenerator minutesGenerator = new MinutesGenerator(aiLlmService);
            MeetingMinutes minutes = minutesGenerator.generate(cleaned, meetingTitle);

            ActionItemExtractor actionExtractor = new ActionItemExtractor(aiLlmService);

            return MeetingOutput.builder()
                    .jobId(jobId)
                    .audioPath(audioPath)
                    .status("completed")
                    .processedAt(Instant.now())
                    .transcript(cleaned)
                    .minutes(minutes)
                    .actionItems(actionExtractor.extract(cleaned))
                    .durationSeconds(cleaned.getDurationSeconds())
                    .speakerCount(cleaned.getSpeakerCount())
                    .language(language)
                    .build();
        } catch (Exception ex) {
            log.warn("Meeting pipeline failed: {}", ex.getMessage());
            return MeetingOutput.builder()
                    .jobId(jobId)
                    .audioPath(audioPath)
                    .status("failed")
                    .processedAt(Instant.now())
                    .language(language)
                    .error(ex.getMessage())
                    .build();
        }
    }
}
