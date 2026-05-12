from app.core.logging import logger
from app.schemas.transcript import RawTranscript, CleanedTranscript, TranscriptSegment
from app.services.speech.diarization import DiarSegment


def _overlap(a_start: float, a_end: float, b_start: float, b_end: float) -> float:
    return max(0.0, min(a_end, b_end) - max(a_start, b_start))


class TranscriptAligner:
    """
    Merges STT segments with diarization output via timestamp overlap.
    Produces a CleanedTranscript with speaker labels and confidence scores.
    """

    def align(
        self,
        raw_transcript: RawTranscript,
        diar_segments: list[DiarSegment],
    ) -> CleanedTranscript:
        """Assign speakers to STT segments using maximum timestamp overlap."""
        n_stt = len(raw_transcript.segments)
        n_diar = len(diar_segments)
        logger.info(f"Aligning {n_stt} STT segments with {n_diar} diarization segments")

        if n_stt == 0:
            return CleanedTranscript(
                segments=[],
                language=raw_transcript.language,
                duration_seconds=raw_transcript.duration_seconds,
                speaker_count=0,
                full_text="",
            )

        aligned: list[TranscriptSegment] = []
        for stt_seg in raw_transcript.segments:
            aligned.append(self._align_segment(stt_seg, diar_segments))

        unknown_count = sum(1 for s in aligned if s.speaker == "UNKNOWN")
        speaker_count = len({s.speaker for s in aligned if s.speaker != "UNKNOWN"})
        logger.info(f"Alignment done: {unknown_count} UNKNOWN segments, {speaker_count} distinct speakers")

        full_text = "\n".join(f"{s.speaker}: {s.text}" for s in aligned)

        return CleanedTranscript(
            segments=aligned,
            language=raw_transcript.language,
            duration_seconds=raw_transcript.duration_seconds,
            speaker_count=speaker_count,
            full_text=full_text,
        )

    def _align_segment(
        self,
        stt_seg: TranscriptSegment,
        diar_segments: list[DiarSegment],
    ) -> TranscriptSegment:
        """Create a new TranscriptSegment with speaker and confidence from diarization."""
        duration = max(stt_seg.end - stt_seg.start, 1e-6)

        best_speaker = "UNKNOWN"
        best_overlap = 0.0
        for diar in diar_segments:
            ov = _overlap(stt_seg.start, stt_seg.end, diar.start, diar.end)
            if ov > best_overlap:
                best_overlap = ov
                best_speaker = diar.speaker

        if best_overlap > 0:
            speaker = best_speaker
            speaker_confidence = best_overlap / duration
        else:
            speaker = "UNKNOWN"
            speaker_confidence = 0.0

        stt_conf = stt_seg.stt_confidence
        if stt_conf is not None:
            confidence = 0.6 * stt_conf + 0.4 * speaker_confidence
        else:
            confidence = speaker_confidence

        needs_review = False
        if speaker == "UNKNOWN":
            needs_review = True
        if speaker_confidence < 0.6:
            needs_review = True
        if confidence < 0.7:
            needs_review = True
        if stt_seg.needs_review:
            needs_review = True

        return TranscriptSegment(
            segment_id=stt_seg.segment_id,
            speaker=speaker,
            start=stt_seg.start,
            end=stt_seg.end,
            text=stt_seg.text,
            stt_confidence=stt_conf,
            speaker_confidence=speaker_confidence,
            confidence=confidence,
            needs_review=needs_review,
        )
