from pydantic import BaseModel
from typing import Optional


class TaskAgentInput(BaseModel):
    text: str
    user_id: Optional[str] = None
    organization_id: Optional[str] = None


class TaskAgentResult(BaseModel):
    success: bool
    message: str
    task_id: Optional[str] = None
    raw_input: str
    data: Optional[dict] = None
