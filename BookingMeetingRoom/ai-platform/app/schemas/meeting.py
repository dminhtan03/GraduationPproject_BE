from pydantic import BaseModel, Field, model_validator
from typing import Optional, Literal
from datetime import datetime
from app.schemas.transcript import CleanedTranscript
from app.schemas.action_item import ActionItem

VALID_STATUSES = {"pending", "running", "completed", "failed"}


class MeetingProcessRequest(BaseModel):
    audio_path: str = Field(..., description="Path to the audio file to process")
    meeting_title: Optional[str] = Field(None, description="Optional meeting title")
    participants: Optional[list[str]] = Field(None, description="Known participant names")
    language: str = Field("vi", description="Primary language of the meeting")

    @model_validator(mode="after")
    def validate_request(self) -> "MeetingProcessRequest":
        if not self.audio_path.strip():
            raise ValueError("audio_path must not be empty")
        if not self.language.strip():
            raise ValueError("language must not be empty")
        if self.meeting_title is not None and not self.meeting_title.strip():
            self.meeting_title = None
        return self


class MeetingMinutes(BaseModel):
    """Structured meeting minutes generated from the cleaned transcript."""

    title: str = Field(..., description="Meeting title")
    summary: str = Field(..., description="High-level summary of the meeting")
    key_decisions: list[str] = Field(default_factory=list)
    discussion_points: list[str] = Field(default_factory=list)
    risks: list[str] = Field(default_factory=list)
    open_questions: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_minutes(self) -> "MeetingMinutes":
        if not self.title.strip():
            raise ValueError("title must not be empty")
        if not self.summary.strip():
            raise ValueError("summary must not be empty")
        self.title = self.title.strip()
        self.summary = self.summary.strip()
        return self


class MeetingOutput(BaseModel):
    """Final structured output from the Meeting Agent pipeline."""

    job_id: str = Field(..., description="Unique job identifier")
    audio_path: str = Field(..., description="Source audio file path")
    status: str = Field("completed", description="Job status: pending | running | completed | failed")
    processed_at: datetime = Field(default_factory=datetime.utcnow)

    # Pipeline outputs
    transcript: Optional[CleanedTranscript] = None
    minutes: Optional[MeetingMinutes] = None
    action_items: list[ActionItem] = Field(default_factory=list)

    # Metadata
    duration_seconds: Optional[float] = None
    speaker_count: Optional[int] = None
    language: str = "vi"
    error: Optional[str] = None

    @model_validator(mode="after")
    def validate_output(self) -> "MeetingOutput":
        if not self.job_id.strip():
            raise ValueError("job_id must not be empty")
        if not self.audio_path.strip():
            raise ValueError("audio_path must not be empty")
        if self.status not in VALID_STATUSES:
            raise ValueError(f"status must be one of {VALID_STATUSES}, got '{self.status}'")
        if self.status == "failed" and not self.error:
            raise ValueError("error must be set when status is 'failed'")
        if self.status == "completed" and not (self.transcript or self.minutes or self.action_items):
            raise ValueError("completed job must have at least one of: transcript, minutes, action_items")
        return self
