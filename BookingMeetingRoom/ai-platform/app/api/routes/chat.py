from fastapi import APIRouter, Header
from pydantic import BaseModel
from typing import Optional
from app.agents.orchestrator.agent import OrchestratorAgent
from app.core.logging import logger

router = APIRouter(prefix="/chat", tags=["chat"])

_orchestrator = OrchestratorAgent()


class ChatRequest(BaseModel):
    session_id: str
    message: str
    user_id: Optional[str] = None
    organization_id: Optional[str] = None


class ChatResponse(BaseModel):
    reply: str
    intent: str
    data: Optional[dict] = None


@router.post("", response_model=ChatResponse)
def chat(
    request: ChatRequest,
    authorization: Optional[str] = Header(default=None),
) -> ChatResponse:
    token = None
    if authorization and authorization.lower().startswith("bearer "):
        token = authorization[7:].strip()

    logger.info(f"Chat: session={request.session_id} user={request.user_id}")

    result = _orchestrator.run(
        text=request.message,
        user_id=request.user_id,
        organization_id=request.organization_id,
        session_id=request.session_id,
        token=token,
    )

    return ChatResponse(
        reply=result.get("reply", "Đã xử lý."),
        intent=result.get("intent", "unknown"),
        data=result.get("data"),
    )
