from fastapi import APIRouter, BackgroundTasks, Header
from datetime import datetime
from typing import Optional
import uuid

from pydantic import BaseModel
from app.schemas.meeting import MeetingProcessRequest, MeetingOutput, MeetingMinutes
from app.schemas.transcript import CleanedTranscript, TranscriptSegment
from app.schemas.action_item import ActionItem, Priority
from app.services.llm.prompt_runner import PromptRunner
from app.pipelines.fast_meeting_pipeline import FastMeetingPipeline
from app.core.logging import logger

router  = APIRouter(prefix="/meeting", tags=["meeting"])
_runner = PromptRunner()

# Pipeline singleton — lazy-initialized (STT model loads on first use)
_pipeline: Optional[FastMeetingPipeline] = None

def _get_pipeline() -> FastMeetingPipeline:
    global _pipeline
    if _pipeline is None:
        _pipeline = FastMeetingPipeline()
    return _pipeline


def _transcribe_audio(audio_path: str) -> Optional[str]:
    """Dùng OpenAI Whisper API để transcribe file audio → text tiếng Việt."""
    import os
    from pathlib import Path
    from app.core.config import settings

    if not audio_path or not Path(audio_path).exists():
        logger.warning(f"STT: file không tồn tại: {audio_path}")
        return None
    if not settings.openai_api_key:
        logger.warning("STT: OPENAI_API_KEY chưa được cấu hình")
        return None

    try:
        import openai
        client = openai.OpenAI(api_key=settings.openai_api_key)
        with open(audio_path, "rb") as f:
            result = client.audio.transcriptions.create(
                model="whisper-1",
                file=f,
                language="vi",
            )
        transcript = result.text.strip()
        logger.info(f"STT: transcribed {len(transcript)} chars from {Path(audio_path).name}")
        return transcript if transcript else None
    except Exception as e:
        logger.warning(f"STT failed: {e}")
        return None


def _build_context(request: "MeetingAnalyzeRequest", transcript: Optional[str] = None) -> str:
    """Tạo context string từ thông tin cuộc họp + transcript."""
    parts = [f"Cuộc họp: {request.meeting_title}"]
    if request.description:
        parts.append(f"Mô tả: {request.description}")
    if request.scheduled_start:
        parts.append(f"Thời gian: {request.scheduled_start}")
    if request.participants:
        parts.append(f"Người tham gia: {', '.join(request.participants)}")
    if transcript:
        parts.append(f"Nội dung ghi âm (transcript):\n{transcript}")
    elif request.transcript:
        parts.append(f"Transcript:\n{request.transcript[:3000]}")
    if request.notes:
        parts.append("Ghi chú:\n" + "\n".join(f"- {n}" for n in request.notes))
    if request.action_items:
        parts.append("Action items:\n" + "\n".join(f"- {a}" for a in request.action_items))
    return "\n\n".join(parts)

_ANALYZE_SYSTEM = """Bạn là AI trích xuất nhiệm vụ từ thông tin cuộc họp.

Dựa vào tiêu đề, mô tả, người tham gia và ghi chú cuộc họp, tạo 2-5 nhiệm vụ cụ thể cần thực hiện sau họp.

Trả về CHỈ một JSON array, không giải thích, không markdown. Mỗi phần tử có các trường:
- title: tên nhiệm vụ ngắn gọn (bắt buộc)
- description: mô tả chi tiết
- goal: mục tiêu cần đạt
- expected_result: kết quả kỳ vọng
- priority: "low" hoặc "high" hoặc "urgent" (KHÔNG dùng medium)
- due_at: ISO 8601 hoặc null (hạn 3-7 ngày sau cuộc họp)
- ai_confidence: số từ 0.0 đến 1.0
- ai_raw_text: trích dẫn từ thông tin cuộc họp làm căn cứ

Ví dụ output: [{"title":"Viết báo cáo","description":"...","goal":"...","expected_result":"...","priority":"high","due_at":"2026-05-14T17:00:00","ai_confidence":0.85,"ai_raw_text":"..."}]"""


class MeetingAnalyzeRequest(BaseModel):
    meeting_title: str
    description: Optional[str] = None
    participants: list[str] = []
    notes: list[str] = []
    action_items: list[str] = []
    transcript: Optional[str] = None
    scheduled_start: Optional[str] = None
    audio_path: Optional[str] = None   # đường dẫn file audio để transcribe


class ExtractedTaskItem(BaseModel):
    title: str
    description: Optional[str] = None
    goal: Optional[str] = None
    expected_result: Optional[str] = None
    priority: str = "medium"
    due_at: Optional[str] = None
    assigner_user_id: Optional[str] = None
    assignee_user_id: Optional[str] = None
    ai_confidence: Optional[float] = None
    ai_raw_text: Optional[str] = None


@router.post("/analyze", response_model=list[ExtractedTaskItem])
def analyze_meeting(
    request: MeetingAnalyzeRequest,
    authorization: Optional[str] = Header(default=None),
) -> list[ExtractedTaskItem]:
    """
    Trích xuất nhiệm vụ từ cuộc họp.
    Nếu có audio_path → chạy FastMeetingPipeline (STT + diarize + LLM).
    Nếu không có     → dùng LLM-only với metadata cuộc họp.
    """
    logger.info(f"Meeting analyze: title={request.meeting_title}, has_audio={bool(request.audio_path)}")

    # ── Fast path: full pipeline khi có audio ────────────────────────────────
    if request.audio_path:
        try:
            result = _get_pipeline().run(
                audio_path=request.audio_path,
                meeting_title=request.meeting_title,
            )
            if result.stt_failed:
                logger.info("Pipeline STT failed — falling back to LLM-only")
            else:
                _PRI_MAP = {"low": "low", "high": "high", "urgent": "urgent", "medium": "low"}

                # Prefer richer ChunkedTaskExtractor output when available
                if result.extracted_tasks:
                    logger.info(f"Using extracted_tasks: {len(result.extracted_tasks)} tasks")
                    tasks = []
                    for t in result.extracted_tasks:
                        if not isinstance(t, dict) or not t.get("title"):
                            continue
                        tasks.append(ExtractedTaskItem(
                            title=t["title"],
                            description=t.get("description"),
                            goal=t.get("goal"),
                            expected_result=t.get("expected_result"),
                            priority=_PRI_MAP.get((t.get("priority") or "low").lower(), "low"),
                            due_at=t.get("due_at"),
                            assigner_user_id=t.get("assigner_user_id"),
                            assignee_user_id=t.get("assignee_user_id"),
                            ai_confidence=t.get("ai_confidence", 0.7),
                            ai_raw_text=t.get("ai_raw_text"),
                        ))
                    if tasks:
                        return tasks

                # Fallback to legacy ActionItem list
                if result.action_items:
                    logger.info(f"Using action_items fallback: {len(result.action_items)} tasks")
                    return [
                        ExtractedTaskItem(
                            title=a.title,
                            description=a.description,
                            priority=_PRI_MAP.get(getattr(a.priority, "value", "low"), "low"),
                            due_at=a.due_date,
                            ai_confidence=a.confidence,
                            ai_raw_text=a.source_speaker,
                        )
                        for a in result.action_items
                    ]

                logger.info("Pipeline returned no tasks — falling back to LLM-only")
        except Exception as exc:
            logger.warning(f"Pipeline failed, falling back: {exc}")

    # ── Fallback: LLM-only với metadata ─────────────────────────────────────
    import json as _json
    transcript_text = _transcribe_audio(request.audio_path) if request.audio_path else None
    user_prompt = _build_context(request, transcript_text)
    raw = _runner.run_text(system_prompt=_ANALYZE_SYSTEM, user_prompt=user_prompt)
    logger.info(f"Meeting analyze LLM raw: {(raw or '')[:200]}")

    if not raw:
        return []

    text = raw.strip()
    start = text.find("[")
    end   = text.rfind("]") + 1
    if start == -1 or end <= 0:
        return []
    try:
        items = _json.loads(text[start:end])
    except (_json.JSONDecodeError, ValueError):
        return []

    _PRI_MAP = {"low": "low", "high": "high", "urgent": "urgent", "medium": "low"}
    tasks = []
    for item in (items if isinstance(items, list) else []):
        if not isinstance(item, dict) or not item.get("title"):
            continue
        raw_pri = item.get("priority", "low") or "low"
        tasks.append(ExtractedTaskItem(
            title=item["title"],
            description=item.get("description"),
            goal=item.get("goal"),
            expected_result=item.get("expected_result"),
            priority=_PRI_MAP.get(raw_pri.lower(), "low"),
            due_at=item.get("due_at"),
            ai_confidence=item.get("ai_confidence", 0.7),
            ai_raw_text=item.get("ai_raw_text"),
        ))
    logger.info(f"Meeting analyze: {len(tasks)} tasks (LLM-only fallback)")
    return tasks


# ── Meeting summarize ─────────────────────────────────────────────────────────

class MeetingSummaryResponse(BaseModel):
    transcript: str   # formatted "Người N: text" lines
    summary: str      # paragraph summary


@router.post("/summarize", response_model=MeetingSummaryResponse)
def summarize_meeting(
    request: MeetingAnalyzeRequest,
    authorization: Optional[str] = Header(default=None),
) -> MeetingSummaryResponse:
    """
    Tóm tắt nội dung cuộc họp.
    Nếu có audio_path → chạy FastMeetingPipeline để có transcript phân giọng nói.
    Nếu không có     → dùng LLM với metadata.
    """
    logger.info(f"Meeting summarize: title={request.meeting_title}, has_audio={bool(request.audio_path)}")

    # ── Fast path: full pipeline khi có audio ────────────────────────────────
    if request.audio_path:
        try:
            result = _get_pipeline().run(
                audio_path=request.audio_path,
                meeting_title=request.meeting_title,
            )
            if not result.stt_failed and result.transcript_text:
                logger.info(f"Meeting summarize: pipeline done, {result.speaker_count} speakers")
                return MeetingSummaryResponse(
                    transcript=result.transcript_text,
                    summary=result.summary,
                )
            logger.info("Pipeline STT failed or empty — falling back to LLM-only")
        except Exception as exc:
            logger.warning(f"Pipeline failed, falling back: {exc}")

    # ── Fallback: LLM-only ───────────────────────────────────────────────────
    transcript_text = _transcribe_audio(request.audio_path) if request.audio_path else None
    user_prompt = _build_context(request, transcript_text)

    _SUMMARIZE_SYSTEM = (
        "Bạn là AI tóm tắt cuộc họp bằng tiếng Việt. "
        "Viết tóm tắt 3-6 câu tự nhiên. Không dùng bullet point."
    )
    summary = _runner.run_text(system_prompt=_SUMMARIZE_SYSTEM, user_prompt=user_prompt)
    if not summary:
        parts = [f'Cuộc họp "{request.meeting_title}"']
        if request.participants:
            parts.append(f"có sự tham gia của {', '.join(request.participants[:3])}")
        if request.scheduled_start:
            parts.append(f"diễn ra vào {request.scheduled_start[:10]}")
        summary = " ".join(parts) + "."

    return MeetingSummaryResponse(
        transcript=transcript_text or "",
        summary=summary,
    )


@router.post("/process", response_model=MeetingOutput)
async def process_meeting(
    request: MeetingProcessRequest,
    background_tasks: BackgroundTasks,
) -> MeetingOutput:
    job_id = str(uuid.uuid4())
    logger.info(f"Meeting process: job_id={job_id}")

    mock_output = MeetingOutput(
        job_id=job_id,
        audio_path=request.audio_path,
        status="completed",
        processed_at=datetime.utcnow(),
        transcript=CleanedTranscript(
            segments=[
                TranscriptSegment(speaker="SPEAKER_00", start=0.0, end=5.2,
                    text="Chào mừng mọi người đến với cuộc họp hôm nay.", confidence=0.95),
                TranscriptSegment(speaker="SPEAKER_01", start=5.5, end=10.3,
                    text="Cảm ơn. Chúng ta bắt đầu với mục đầu tiên nhé.", confidence=0.92),
            ],
            language="vi", duration_seconds=120.0, speaker_count=2,
            full_text="Chào mừng mọi người đến với cuộc họp hôm nay. Cảm ơn. Chúng ta bắt đầu với mục đầu tiên nhé.",
        ),
        minutes=MeetingMinutes(
            title=request.meeting_title or "Cuộc họp",
            summary="Cuộc họp thảo luận về các vấn đề chính của dự án.",
            key_decisions=["Quyết định tiếp tục phát triển tính năng A"],
            discussion_points=["Tiến độ dự án", "Phân công nhiệm vụ"],
        ),
        action_items=[
            ActionItem(title="Hoàn thiện tài liệu thiết kế",
                description="Cập nhật tài liệu theo quyết định cuộc họp",
                assignee="SPEAKER_00", due_date="2026-05-10",
                priority=Priority.high, source_speaker="SPEAKER_00")
        ],
        duration_seconds=120.0, speaker_count=2, language=request.language,
    )
    return mock_output
