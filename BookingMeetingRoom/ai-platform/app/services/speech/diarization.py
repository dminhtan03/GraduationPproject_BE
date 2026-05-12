from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional
from app.core.config import settings
from app.core.logging import logger


@dataclass
class DiarSegment:
    speaker: str
    start: float
    end: float
    confidence: Optional[float] = None


class BaseDiarizer:
    """Abstract base for all diarization providers."""

    def diarize(self, audio_path: str) -> list[DiarSegment]:
        raise NotImplementedError


class SpeakerDiarizer(BaseDiarizer):
    """
    Speaker diarization using pyannote.audio.
    Falls back to stub output if the package is not installed or HF_TOKEN is missing.
    """

    def __init__(self):
        self._pipeline = None
        self._stub_mode = False
        self.hf_token = settings.hf_token
        self.model_name = settings.diarization_model
        logger.info(f"SpeakerDiarizer ready (model={self.model_name})")

        if not self.hf_token:
            logger.warning("HF_TOKEN not set — SpeakerDiarizer will return stub output")
            self._stub_mode = True
            return

        try:
            import pyannote.audio  # noqa: F401 — check import only, lazy-load in _load_pipeline
        except ImportError:
            logger.warning("pyannote.audio not installed — SpeakerDiarizer will return stub output")
            self._stub_mode = True

    def _load_pipeline(self) -> None:
        """Lazy-load the pyannote diarization pipeline on first use."""
        if self._pipeline is not None:
            return
        try:
            from pyannote.audio import Pipeline
            self._pipeline = Pipeline.from_pretrained(
                self.model_name,
                use_auth_token=self.hf_token,
            )
            logger.info("pyannote.audio pipeline loaded")
        except Exception as exc:
            raise RuntimeError(f"Failed to load pyannote pipeline: {exc}") from exc

    def diarize(self, audio_path: str) -> list[DiarSegment]:
        """
        Run speaker diarization and return time-sorted DiarSegment list.
        Returns stub segments when file is missing or running in stub mode.
        """
        if not Path(audio_path).exists():
            logger.warning(f"Audio file not found: {audio_path} — returning stub diarization")
            return self._stub_segments()

        if self._stub_mode:
            logger.warning("SpeakerDiarizer in stub mode — returning stub diarization")
            return self._stub_segments()

        self._load_pipeline()

        logger.info(f"SpeakerDiarizer diarizing: {audio_path}")
        try:
            diarization = self._pipeline(audio_path)
        except Exception as exc:
            raise RuntimeError(f"pyannote diarization failed: {exc}") from exc

        segments: list[DiarSegment] = [
            DiarSegment(
                speaker=speaker,
                start=round(float(turn.start), 3),
                end=round(float(turn.end), 3),
                confidence=None,
            )
            for turn, _, speaker in diarization.itertracks(yield_label=True)
        ]
        segments.sort(key=lambda s: s.start)

        speakers = {s.speaker for s in segments}
        logger.info(f"Diarization complete: {len(segments)} segments, {len(speakers)} speakers")
        return segments

    def _stub_segments(self) -> list[DiarSegment]:
        return [
            DiarSegment(speaker="SPEAKER_00", start=0.0, end=5.0),
            DiarSegment(speaker="SPEAKER_01", start=5.0, end=10.0),
        ]


class GapBasedDiarizer:
    """
    Speaker diarization based on silence gaps between transcript segments.

    Algorithm:
        - Silence gap > GAP_THRESHOLD → speaker changed
        - Cycles through Người 1 … Người N, then back to 1
          (approximates turn-taking pattern in Vietnamese business meetings)
        - Works entirely from transcript timestamps — no audio analysis needed
        - Degrades gracefully: if all segments are continuous, all get Người 1

    Limitations:
        - Cannot distinguish when the same person resumes after a break
        - Use pyannote.audio for production-grade accuracy
    """

    GAP_THRESHOLD = 0.9   # seconds — gap > this triggers speaker change
    MAX_SPEAKERS  = 6     # maximum before cycling back to Người 1

    def diarize(self, audio_path: str) -> list[DiarSegment]:
        """Fallback when called without transcript — returns empty (use diarize_from_transcript)."""
        logger.warning("GapBasedDiarizer.diarize() called without transcript — returning empty")
        return []

    def diarize_from_transcript(self, transcript) -> list[DiarSegment]:
        """
        Derive speaker segments from RawTranscript timestamps.
        Each diarization segment has the exact same start/end as the STT segment,
        so TranscriptAligner gets 100% overlap → perfect assignment.
        """
        from app.schemas.transcript import RawTranscript  # avoid circular at module level
        segments = transcript.segments
        if not segments:
            return []

        result: list[DiarSegment] = []
        speaker_num = 1
        prev_end: float = segments[0].end

        # First segment always = Người 1
        result.append(DiarSegment(speaker=f"Người {speaker_num}", start=segments[0].start, end=segments[0].end))

        for seg in segments[1:]:
            gap = seg.start - prev_end
            if gap > self.GAP_THRESHOLD:
                speaker_num = (speaker_num % self.MAX_SPEAKERS) + 1
            result.append(DiarSegment(speaker=f"Người {speaker_num}", start=seg.start, end=seg.end))
            prev_end = seg.end

        speakers_used = len({s.speaker for s in result})
        logger.info(f"GapBasedDiarizer: {len(result)} segments, {speakers_used} speakers detected")
        return result


def get_diarizer() -> BaseDiarizer:
    """Factory: return the configured diarization backend."""
    return SpeakerDiarizer()
