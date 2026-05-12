from pydantic import BaseModel, Field, model_validator
from typing import Optional


class TranscriptSegment(BaseModel):
    """One speaker turn from STT + diarization alignment."""

    segment_id: Optional[str] = Field(None, description="Optional unique identifier for this segment, used for tracing and mapping.")
    speaker: str = Field(..., description="Speaker label, e.g. SPEAKER_00")
    start: float = Field(..., ge=0.0, description="Segment start time in seconds")
    end: float = Field(..., ge=0.0, description="Segment end time in seconds")
    text: str = Field(..., description="Transcribed text for this segment")
    confidence: Optional[float] = Field(None, ge=0.0, le=1.0, description="Overall confidence score")
    stt_confidence: Optional[float] = Field(None, ge=0.0, le=1.0, description="STT model confidence")
    speaker_confidence: Optional[float] = Field(None, ge=0.0, le=1.0, description="Diarization speaker confidence")
    needs_review: bool = Field(False, description="Flag segments that are uncertain or low-confidence")

    @model_validator(mode="after")
    def validate_segment(self) -> "TranscriptSegment":
        if self.end < self.start:
            raise ValueError(f"end ({self.end}) must be >= start ({self.start})")
        if not self.text.strip():
            raise ValueError("text must not be empty or whitespace-only")
        # Mark low-confidence segments for human review
        if not self.needs_review:
            if self.confidence is not None and self.confidence < 0.7:
                self.needs_review = True
            elif self.speaker_confidence is not None and self.speaker_confidence < 0.6:
                self.needs_review = True
        return self


class RawTranscript(BaseModel):
    """Direct output from STT, before speaker assignment."""

    segments: list[TranscriptSegment]
    language: str = "vi"
    duration_seconds: float = Field(..., ge=0.0, description="Total audio duration in seconds")


class CleanedTranscript(BaseModel):
    """Aligned and LLM-cleaned transcript with identified speakers."""

    segments: list[TranscriptSegment]
    language: str = "vi"
    duration_seconds: float = Field(..., ge=0.0, description="Total audio duration in seconds")
    speaker_count: int = Field(..., ge=0, description="Number of distinct speakers detected")
    full_text: str = Field(..., description="Full merged transcript as a single string")
