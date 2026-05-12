from pydantic import BaseModel
from typing import Literal

IntentType = Literal["task", "meeting", "summary", "unknown", "off_topic"]


class IntentResult(BaseModel):
    intent: IntentType
    text: str
    confidence: float = 1.0
