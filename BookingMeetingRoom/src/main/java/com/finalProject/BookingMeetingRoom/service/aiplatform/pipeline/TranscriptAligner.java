package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.CleanedTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.RawTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.TranscriptSegment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TranscriptAligner {

    public CleanedTranscript align(RawTranscript raw, List<GapBasedDiarizer.DiarSegment> diar) {
        if (raw == null || raw.getSegments() == null) {
            return CleanedTranscript.builder()
                    .segments(List.of())
                    .language(raw != null ? raw.getLanguage() : null)
                    .durationSeconds(raw != null ? raw.getDurationSeconds() : null)
                    .speakerCount(0)
                    .fullText("")
                    .build();
        }

        List<TranscriptSegment> aligned = new ArrayList<>();
        for (TranscriptSegment seg : raw.getSegments()) {
            TranscriptSegment copy = TranscriptSegment.builder()
                    .segmentId(seg.getSegmentId() != null ? seg.getSegmentId() : UUID.randomUUID().toString())
                    .speaker(resolveSpeaker(seg, diar))
                    .start(seg.getStart())
                    .end(seg.getEnd())
                    .text(seg.getText())
                    .sttConfidence(seg.getSttConfidence())
                    .speakerConfidence(resolveSpeakerConfidence(seg, diar))
                    .confidence(seg.getConfidence())
                    .needsReview(false)
                    .build();
            aligned.add(copy);
        }

        String fullText = buildFullText(aligned);
        int speakerCount = countSpeakers(aligned);

        return CleanedTranscript.builder()
                .segments(aligned)
                .language(raw.getLanguage())
                .durationSeconds(raw.getDurationSeconds())
                .speakerCount(speakerCount)
                .fullText(fullText)
                .build();
    }

    private String resolveSpeaker(TranscriptSegment seg, List<GapBasedDiarizer.DiarSegment> diar) {
        GapBasedDiarizer.DiarSegment best = findBestOverlap(seg, diar);
        return best != null ? best.getSpeaker() : "UNKNOWN";
    }

    private Double resolveSpeakerConfidence(TranscriptSegment seg, List<GapBasedDiarizer.DiarSegment> diar) {
        GapBasedDiarizer.DiarSegment best = findBestOverlap(seg, diar);
        return best != null ? best.getConfidence() : 0.5;
    }

    private GapBasedDiarizer.DiarSegment findBestOverlap(TranscriptSegment seg, List<GapBasedDiarizer.DiarSegment> diar) {
        if (seg == null || diar == null || diar.isEmpty()) {
            return null;
        }
        double s = seg.getStart() != null ? seg.getStart() : 0.0;
        double e = seg.getEnd() != null ? seg.getEnd() : s;
        double best = 0.0;
        GapBasedDiarizer.DiarSegment winner = null;
        for (GapBasedDiarizer.DiarSegment d : diar) {
            double ds = d.getStart() != null ? d.getStart() : 0.0;
            double de = d.getEnd() != null ? d.getEnd() : ds;
            double overlap = Math.max(0.0, Math.min(e, de) - Math.max(s, ds));
            if (overlap > best) {
                best = overlap;
                winner = d;
            }
        }
        return winner;
    }

    private int countSpeakers(List<TranscriptSegment> segments) {
        Set<String> speakers = new HashSet<>();
        for (TranscriptSegment seg : segments) {
            if (seg.getSpeaker() != null && !seg.getSpeaker().isBlank()) {
                speakers.add(seg.getSpeaker());
            }
        }
        return speakers.size();
    }

    private String buildFullText(List<TranscriptSegment> segments) {
        StringBuilder sb = new StringBuilder();
        for (TranscriptSegment seg : segments) {
            if (seg.getText() == null || seg.getText().isBlank()) {
                continue;
            }
            String speaker = seg.getSpeaker() != null ? seg.getSpeaker() : "UNKNOWN";
            sb.append(speaker).append(": ").append(seg.getText().trim()).append("\n");
        }
        return sb.toString().trim();
    }
}
