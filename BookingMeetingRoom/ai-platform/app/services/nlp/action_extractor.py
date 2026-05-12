import json
from app.schemas.transcript import CleanedTranscript
from app.schemas.action_item import ActionItem, Priority
from app.services.llm.prompt_runner import PromptRunner
from app.core.logging import logger

_REJECT_PHRASES = {"chúng ta", "nên", "có thể", "thảo luận"}
_HIGH_CONFIDENCE_WORDS = {"phải", "deadline", "bắt buộc"}

_PROMPT = """Bạn là hệ thống trích xuất công việc từ transcript cuộc họp.

Nhiệm vụ:
- Xác định các hành động cụ thể cần thực hiện
- Không trích xuất các câu chỉ mang tính thảo luận

QUY TẮC:
- Chỉ extract khi có hành động rõ ràng
- Không suy diễn
- Không thêm task không tồn tại
- Nếu không chắc → bỏ qua

Chỉ extract khi có các dấu hiệu:
- 'tôi sẽ'
- 'anh làm'
- 'giao cho'
- 'cần làm'
- 'phải'
- 'deadline'
- 'trước ngày'
- 'by'
- 'before'

Output JSON:
{{
  "items": [
    {{
      "title": "...",
      "description": "...",
      "assignee": "SPEAKER_XX hoặc null",
      "due_date": "... hoặc null",
      "source_segment_ids": []
    }}
  ]
}}

Input:
{segments}"""


class ActionExtractor:
    """
    Extracts ActionItems from a CleanedTranscript via LLM.
    Uses segment-level input (speaker + text + segment_id) for precise source attribution.
    Applies strict validation to avoid creating garbage tasks.
    """

    def __init__(self):
        self._runner = PromptRunner()

    def extract(self, transcript: CleanedTranscript) -> list[ActionItem]:
        if not transcript.segments:
            return []

        # Build segment input for LLM (no full_text — use per-segment data)
        segment_data = [
            {
                "speaker": seg.speaker,
                "text": seg.text,
                "segment_id": seg.segment_id,
            }
            for seg in transcript.segments
        ]
        prompt = _PROMPT.format(segments=json.dumps(segment_data, ensure_ascii=False, indent=2))

        data = self._runner.run_json(
            system_prompt="You are an action item extractor. Respond only with valid JSON.",
            user_prompt=prompt,
            temperature=0.1,
        )
        if data is None:
            logger.warning("ActionExtractor: LLM returned no data — returning []")
            return []

        raw_items = data.get("items", []) if isinstance(data, dict) else []

        if not raw_items:
            return []

        # Build speaker lookup: segment_id → speaker (for source attribution)
        seg_speaker: dict[str, str] = {
            seg.segment_id: seg.speaker
            for seg in transcript.segments
            if seg.segment_id is not None
        }

        results: list[ActionItem] = []
        seen_titles: set[str] = set()
        for raw in raw_items:
            item = self._validate_and_build(raw, seg_speaker, transcript)
            if item is None:
                continue
            normalized_title = item.title.lower().strip()
            if normalized_title in seen_titles:
                logger.warning(f"ActionExtractor: duplicate title skipped: {repr(item.title)}")
                continue
            seen_titles.add(normalized_title)
            results.append(item)

        logger.info(f"ActionExtractor: {len(results)} action items extracted from {len(raw_items)} candidates")
        return results

    def _validate_and_build(
        self,
        raw: dict,
        seg_speaker: dict[str, str],
        transcript: CleanedTranscript,
    ) -> ActionItem | None:
        title = " ".join(str(raw.get("title", "") or "").split())

        # Reject: empty or too short
        if not title or len(title) < 5:
            logger.warning(f"ActionExtractor: rejected item — title too short: {repr(title)}")
            return None

        # Reject: discussion phrases in title
        title_lower = title.lower()
        for phrase in _REJECT_PHRASES:
            if phrase in title_lower:
                logger.warning(f"ActionExtractor: rejected item — discussion phrase '{phrase}' in title")
                return None

        description = " ".join(str(raw.get("description", "") or "").split()) or None

        # Reject: description hallucination (> 3x title length)
        if description and len(description) > 3 * len(title):
            logger.warning(f"ActionExtractor: description too long ({len(description)} vs {len(title)}) — clearing")
            description = None

        assignee = raw.get("assignee") or None
        if isinstance(assignee, str):
            assignee = assignee.strip() or None

        due_date = raw.get("due_date") or None
        if isinstance(due_date, str):
            due_date = due_date.strip() or None

        source_segment_ids: list[str] = [
            s for s in (raw.get("source_segment_ids") or [])
            if isinstance(s, str) and s.strip()
        ]

        # Resolve source_speaker from first matched segment_id
        source_speaker: str | None = None
        for sid in source_segment_ids:
            if sid in seg_speaker:
                source_speaker = seg_speaker[sid]
                break

        # Confidence: check source segments only (per-item, not global)
        source_text = " ".join(
            seg.text
            for seg in transcript.segments
            if seg.segment_id in source_segment_ids
        )
        if not source_text:
            source_text = title
        if any(word in source_text.lower() for word in _HIGH_CONFIDENCE_WORDS):
            confidence = 0.85
        else:
            confidence = 0.7

        return ActionItem(
            title=title,
            description=description,
            assignee=assignee,
            due_date=due_date,
            priority=Priority.medium,
            confidence=confidence,
            source_speaker=source_speaker,
            source_segment_ids=source_segment_ids,
            needs_review=True,
        )


# Backward-compatible alias — pipeline imports ActionItemExtractor
ActionItemExtractor = ActionExtractor
