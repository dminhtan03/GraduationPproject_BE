package com.finalProject.BookingMeetingRoom.service.aiplatform.pipeline;

import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.CleanedTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.TranscriptSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TranscriptCleaner {

    private static final Pattern FILLER = Pattern.compile("\\b(uh|um|er|ah|ok)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTI_PUNCT = Pattern.compile("([.,!?]){2,}");

    public CleanedTranscript clean(CleanedTranscript transcript) {
        if (transcript == null || transcript.getSegments() == null) {
            return transcript;
        }
        List<TranscriptSegment> cleaned = new ArrayList<>();
        for (TranscriptSegment seg : transcript.getSegments()) {
            if (seg == null || seg.getText() == null) {
                continue;
            }
            String text = seg.getText().trim();
            if (text.isBlank()) {
                continue;
            }
            text = FILLER.matcher(text).replaceAll("").trim();
            text = MULTI_PUNCT.matcher(text).replaceAll("$1").trim();
            if (text.isBlank()) {
                continue;
            }
            seg.setText(text);
            cleaned.add(seg);
        }
        transcript.setSegments(cleaned);
        return transcript;
    }
}
