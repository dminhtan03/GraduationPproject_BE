package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.RawTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.TranscriptSegment;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public class GapBasedDiarizer {

    private static final double GAP_THRESHOLD = 0.9;
    private static final int MAX_SPEAKERS = 6;

    public List<DiarSegment> diarizeFromTranscript(RawTranscript transcript) {
        List<DiarSegment> result = new ArrayList<>();
        if (transcript == null || transcript.getSegments() == null || transcript.getSegments().isEmpty()) {
            return result;
        }

        List<TranscriptSegment> segments = transcript.getSegments();
        int speakerNum = 1;
        TranscriptSegment first = segments.get(0);
        result.add(new DiarSegment("Nguoi " + speakerNum, first.getStart(), first.getEnd(), 0.9));
        double prevEnd = first.getEnd() != null ? first.getEnd() : 0.0;

        for (int i = 1; i < segments.size(); i++) {
            TranscriptSegment seg = segments.get(i);
            double start = seg.getStart() != null ? seg.getStart() : prevEnd;
            double end = seg.getEnd() != null ? seg.getEnd() : start;
            double gap = start - prevEnd;
            if (gap > GAP_THRESHOLD) {
                speakerNum = (speakerNum % MAX_SPEAKERS) + 1;
            }
            result.add(new DiarSegment("Nguoi " + speakerNum, start, end, 0.9));
            prevEnd = end;
        }

        return result;
    }

    @Data
    @AllArgsConstructor
    public static class DiarSegment {
        private String speaker;
        private Double start;
        private Double end;
        private Double confidence;
    }
}
