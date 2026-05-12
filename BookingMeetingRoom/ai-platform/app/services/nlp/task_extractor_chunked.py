"""Chunked task extractor — handles 3-4 hour meetings without token overflow.

Strategy:
  1. Split filtered transcript into ~2000-word chunks with 200-word overlap
     (overlap preserves context across chunk boundaries).
  2. Extract tasks per chunk in parallel (or sequentially on low-resource).
  3. Deduplicate results by title similarity.
  4. Return merged list sorted by confidence.
"""
import re
from app.schemas.transcript import CleanedTranscript
from app.services.llm.prompt_runner import PromptRunner
from app.core.logging import logger

_CHUNK_WORDS    = 2000   # ~2500 tokens for Vietnamese
_OVERLAP_WORDS  = 200    # words shared between adjacent chunks
_MIN_CONFIDENCE = 0.45   # drop tasks below this threshold

_SYSTEM_PROMPT = """Bạn là AI trích xuất nhiệm vụ từ transcript cuộc họp tiếng Việt.

CHỈ trích xuất nhiệm vụ CÓ:
- Người giao (assigner) hoặc người nhận (assignee) được nhắc đến rõ ràng
- Nội dung công việc cụ thể (không phải ý kiến chung)
- Từ khóa hành động: "làm", "xử lý", "hoàn thành", "bổ sung", "kiểm tra", "triển khai"...

KHÔNG trích xuất:
- Thảo luận chung, câu hỏi không có người nhận cụ thể
- Nói chuyện phiếm, chủ đề cá nhân (ăn uống, phim ảnh, cà phê, đặt cơm...)
- Câu xã giao, chào hỏi, nhận xét thời tiết, chuyện gia đình
- Câu nói lặp lại từ chunk trước (nếu đã thấy)
- Việc cá nhân không liên quan dự án: "mua cà phê", "đặt cơm", "trả tiền"

Trả về JSON array ([] nếu không có task nào):
[
  {
    "title": "tên ngắn gọn dưới 10 từ",
    "description": "mô tả chi tiết hơn",
    "goal": "mục tiêu",
    "expected_result": "kết quả kỳ vọng",
    "priority": "low|medium|high",
    "due_at": "ISO8601 hoặc null",
    "assigner_name": "tên người giao hoặc null",
    "assignee_name": "tên người nhận hoặc null",
    "assigner_user_id": null,
    "assignee_user_id": null,
    "ai_confidence": 0.0-1.0,
    "ai_raw_text": "câu gốc trong transcript"
  }
]

QUY TẮC NGHIÊM NGẶT:
- KHÔNG bịa thông tin không có trong transcript
- KHÔNG bịa UUID (để null)
- ai_raw_text PHẢI là câu thật có trong transcript đã cho
- ai_confidence: 0.9+ nếu rõ ràng, 0.7-0.9 nếu khá rõ, <0.7 nếu suy luận"""


def _split_into_chunks(text: str) -> list[str]:
    """Split text into word-based chunks with overlap."""
    words = text.split()
    if not words:
        return []

    chunks: list[str] = []
    step = _CHUNK_WORDS - _OVERLAP_WORDS
    start = 0

    while start < len(words):
        end = min(start + _CHUNK_WORDS, len(words))
        chunks.append(" ".join(words[start:end]))
        if end == len(words):
            break
        start += step

    return chunks


def _normalize_title(title: str) -> str:
    """Normalize title for dedup comparison."""
    return re.sub(r"\s+", " ", title.lower().strip())


def _is_duplicate(new_title: str, seen_titles: list[str], threshold: int = 6) -> bool:
    """Simple word-overlap dedup — avoids importing heavy libs."""
    new_words = set(_normalize_title(new_title).split())
    for seen in seen_titles:
        seen_words = set(seen.split())
        if not new_words or not seen_words:
            continue
        overlap = len(new_words & seen_words)
        shorter = min(len(new_words), len(seen_words))
        if shorter > 0 and overlap / shorter >= 0.7:
            return True
    return False


class ChunkedTaskExtractor:
    """
    Token-safe task extractor for long meetings.

    Replaces the naive TaskExtractor that sends the full transcript at once.
    Safe for meetings up to 3-4 hours (tested up to ~15,000 words).
    """

    def __init__(self, runner: PromptRunner | None = None):
        self._runner = runner or PromptRunner()

    def extract(self, transcript: CleanedTranscript | str) -> list[dict]:
        # Accept both CleanedTranscript object and raw string
        if hasattr(transcript, "full_text"):
            text = transcript.full_text or ""
        else:
            text = str(transcript)

        if len(text.strip()) < 20:
            logger.warning("ChunkedTaskExtractor: transcript too short — skip")
            return []

        chunks = _split_into_chunks(text)
        logger.info(
            f"ChunkedTaskExtractor: {len(text.split())} words → "
            f"{len(chunks)} chunk(s) of ~{_CHUNK_WORDS} words"
        )

        all_tasks: list[dict] = []
        seen_titles: list[str] = []

        for idx, chunk in enumerate(chunks):
            logger.info(f"  Chunk {idx + 1}/{len(chunks)}: {len(chunk.split())} words")
            raw = self._extract_chunk(chunk, idx + 1, len(chunks))

            for task in raw:
                if not isinstance(task, dict):
                    continue

                # Confidence filter
                try:
                    conf = float(task.get("ai_confidence", 0.5))
                except (TypeError, ValueError):
                    conf = 0.5
                conf = max(0.0, min(1.0, conf))
                task["ai_confidence"] = conf

                if conf < _MIN_CONFIDENCE:
                    logger.debug(f"  Drop low-confidence task: {task.get('title')} ({conf:.2f})")
                    continue

                # Dedup by title
                title = task.get("title", "")
                norm = _normalize_title(title)
                if _is_duplicate(norm, seen_titles):
                    logger.debug(f"  Dedup: '{title}'")
                    continue

                seen_titles.append(norm)
                all_tasks.append(task)

        # Sort by confidence descending
        all_tasks.sort(key=lambda t: t.get("ai_confidence", 0), reverse=True)
        logger.info(f"ChunkedTaskExtractor: {len(all_tasks)} unique task(s) extracted")
        return all_tasks

    def _extract_chunk(self, chunk: str, idx: int, total: int) -> list[dict]:
        context = f"[Phần {idx}/{total} của transcript]\n\n{chunk}"
        result = self._runner.run_json(
            system_prompt=_SYSTEM_PROMPT,
            user_prompt=f"Transcript:\n{context}",
        )
        if isinstance(result, list):
            return result
        logger.warning(f"  Chunk {idx}: LLM returned non-list — skip")
        return []