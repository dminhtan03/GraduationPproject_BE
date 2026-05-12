from typing import Optional
from pydantic import BaseModel, Field


class ConversationState(BaseModel):
    session_id: str
    active_agent: Optional[str] = None
    pending_action: Optional[str] = None
    payload: dict = Field(default_factory=dict)
    missing_fields: list[str] = Field(default_factory=list)
    status: str = "idle"  # idle | pending | completed | cancelled


# Simple in-memory store (MVP)
_STATE_STORE: dict[str, ConversationState] = {}


def get_state(session_id: str) -> Optional[ConversationState]:
    return _STATE_STORE.get(session_id)


def set_state(session_id: str, state: ConversationState) -> None:
    _STATE_STORE[session_id] = state


def clear_state(session_id: str) -> None:
    _STATE_STORE.pop(session_id, None)
