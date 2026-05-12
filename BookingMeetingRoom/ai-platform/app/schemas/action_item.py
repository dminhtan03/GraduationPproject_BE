from pydantic import BaseModel, Field, model_validator
from typing import Optional
from enum import Enum


class Priority(str, Enum):
    low = "low"
    medium = "medium"
    high = "high"


class ActionItem(BaseModel):
    """An action item extracted from a meeting transcript by the Meeting Agent."""

    title: str = Field(..., description="Short action item title")
    description: Optional[str] = Field(None, description="Detail or context")
    assignee: Optional[str] = Field(None, description="Person responsible")
    due_date: Optional[str] = Field(None, description="Due date as string, e.g. '2026-05-01'")
    priority: Priority = Priority.medium
    confidence: Optional[float] = Field(None, ge=0.0, le=1.0, description="LLM extraction confidence score")
    source_speaker: Optional[str] = Field(None, description="Speaker who mentioned this item")
    source_segment_ids: list[str] = Field(default_factory=list, description="Transcript segment IDs this item was extracted from")
    needs_review: bool = Field(False, description="Flag items that are uncertain or missing key info")

    @model_validator(mode="after")
    def validate_action_item(self) -> "ActionItem":
        if not self.title.strip():
            raise ValueError("title must not be empty or whitespace-only")
        # Normalize empty due_date string to None
        if self.due_date is not None and not self.due_date.strip():
            self.due_date = None
        # Mark items that need human review
        if not self.needs_review:
            if self.confidence is not None and self.confidence < 0.7:
                self.needs_review = True
            elif not self.assignee or not self.assignee.strip():
                self.needs_review = True
        return self
