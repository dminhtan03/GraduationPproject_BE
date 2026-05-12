from pydantic import BaseModel
from typing import Optional


class MeetingAgentInput(BaseModel):
    text: Optional[str] = None
    audio_path: Optional[str] = None


class MeetingAgentResult(BaseModel):
    success: bool
    message: str
    data: Optional[dict] = None


class ExtractedTaskItem(BaseModel):
    title: str
    description: Optional[str] = None
    goal: Optional[str] = None
    expected_result: Optional[str] = None
    priority: Optional[str] = None
    due_at: Optional[str] = None
    assigner_user_id: Optional[str] = None
    assignee_user_id: Optional[str] = None
    ai_confidence: float
    ai_raw_text: str
