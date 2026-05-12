from app.schemas.transcript import CleanedTranscript
from app.schemas.meeting import MeetingMinutes
from app.services.llm.prompt_runner import PromptRunner
from app.core.logging import logger

_DECISION_KEYWORDS = {"quyết định", "chốt", "agree", "thống nhất"}
_RISK_KEYWORDS = {"rủi ro", "risk", "vấn đề", "issue"}

_PROMPT = """Bạn là hệ thống tạo biên bản cuộc họp chuyên nghiệp.

Nhiệm vụ:
- Tóm tắt nội dung cuộc họp theo hướng công việc
- Trích xuất quyết định kỹ thuật / nghiệp vụ (nếu có)
- Tóm tắt các điểm thảo luận chính về sản phẩm / dự án
- Xác định rủi ro kỹ thuật (nếu có)
- Xác định câu hỏi còn mở (nếu có)

QUY TẮC QUAN TRỌNG:
- Chỉ sử dụng thông tin có trong transcript
- Không được thêm thông tin không tồn tại
- Không suy diễn ngoài nội dung đã nói
- Nếu không chắc → để trống
- Không bịa decision, không bịa risk

BỎ QUA hoàn toàn:
- Câu xã giao, chào hỏi đầu giờ
- Phản hồi ngắn không có nội dung (Đúng rồi, Ok, Có, Xong rồi)
- Nói chuyện về ăn uống, giải trí, phim ảnh, cá nhân
- Câu chưa hoàn chỉnh hoặc lạc đề

CHỈ đưa vào tóm tắt:
- Cập nhật tiến độ công việc cụ thể
- Quyết định kỹ thuật / nghiệp vụ có người đề xuất
- Phân công nhiệm vụ (ai làm gì, khi nào)
- Vấn đề kỹ thuật và hướng giải quyết
- Mốc thời gian / deadline được đề cập

Output JSON:
{{
  "title": "...",
  "summary": "...",
  "key_decisions": [],
  "discussion_points": [],
  "risks": [],
  "open_questions": []
}}

Transcript:
{transcript}"""


class MeetingMinutesGenerator:
    """
    Generates MeetingMinutes from a CleanedTranscript via LLM.
    Only uses information present in the transcript — no hallucination.
    Falls back to safe empty values on any failure.
    """

    def __init__(self):
        self._runner = PromptRunner()

    def generate(self, transcript: CleanedTranscript, meeting_title: str = "Cuộc họp") -> MeetingMinutes:
        if not transcript.full_text.strip():
            return self._empty_minutes(meeting_title)

        prompt = _PROMPT.format(transcript=transcript.full_text)
        data = self._runner.run_json(
            system_prompt="You are a meeting minutes generator. Respond only with valid JSON.",
            user_prompt=prompt,
            temperature=0.2,
        )
        if data is None:
            logger.warning("MeetingMinutesGenerator: LLM returned no data — returning empty minutes")
            return self._empty_minutes(meeting_title)

        title = str(data.get("title", "") or meeting_title).strip() or meeting_title
        summary = " ".join(str(data.get("summary", "") or "").split())
        key_decisions: list[str] = [
            s.strip() for s in data.get("key_decisions", [])
            if isinstance(s, str) and s.strip()
        ]
        discussion_points: list[str] = [
            s.strip() for s in data.get("discussion_points", [])
            if isinstance(s, str) and s.strip()
        ]
        risks: list[str] = [
            s.strip() for s in data.get("risks", [])
            if isinstance(s, str) and s.strip()
        ]
        open_questions: list[str] = [
            s.strip() for s in data.get("open_questions", [])
            if isinstance(s, str) and s.strip()
        ]

        # Guard: empty summary → use first 1-2 sentences from transcript
        if not summary:
            logger.warning("MeetingMinutesGenerator: LLM returned empty summary — using transcript fallback")
            summary = self._first_sentences(transcript.full_text, n=2)

        # Guard: summary much longer than transcript → hallucination risk
        if len(summary) > 2 * len(transcript.full_text):
            logger.warning("Summary too long vs transcript — fallback to transcript sentences")
            summary = self._first_sentences(transcript.full_text, n=2)

        # Guard: key_decisions present but no decision signal in transcript
        if key_decisions and not self._contains_any(transcript.full_text, _DECISION_KEYWORDS):
            logger.warning("MeetingMinutesGenerator: key_decisions hallucinated (no decision signal) — clearing")
            key_decisions = []

        # Guard: risks present but no risk signal in transcript
        if risks and not self._contains_any(transcript.full_text, _RISK_KEYWORDS):
            logger.warning("MeetingMinutesGenerator: risks hallucinated (no risk signal) — clearing")
            risks = []

        logger.info(f"MeetingMinutesGenerator: generated minutes — {len(key_decisions)} decisions, {len(risks)} risks")
        return MeetingMinutes(
            title=title,
            summary=summary,
            key_decisions=key_decisions,
            discussion_points=discussion_points,
            risks=risks,
            open_questions=open_questions,
        )

    def _empty_minutes(self, meeting_title: str) -> MeetingMinutes:
        return MeetingMinutes(
            title=meeting_title,
            summary="Không có đủ thông tin để tạo biên bản.",
            key_decisions=[],
            discussion_points=[],
            risks=[],
            open_questions=[],
        )

    @staticmethod
    def _contains_any(text: str, keywords: set[str]) -> bool:
        text_lower = text.lower()
        return any(kw in text_lower for kw in keywords)

    @staticmethod
    def _first_sentences(text: str, n: int = 2) -> str:
        """Return the first n sentences from text as a fallback summary."""
        import re
        sentences = re.split(r"(?<=[.!?])\s+", text.strip())
        return " ".join(sentences[:n]) if sentences else text[:200]


# Backward-compatible alias — pipeline imports MinutesGenerator
MinutesGenerator = MeetingMinutesGenerator
