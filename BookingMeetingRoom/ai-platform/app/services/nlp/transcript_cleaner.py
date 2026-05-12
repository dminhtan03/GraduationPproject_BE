from app.schemas.transcript import CleanedTranscript, TranscriptSegment
from app.services.llm.prompt_runner import PromptRunner
from app.core.logging import logger

_BATCH_SIZE = 8   # segments per LLM call (tune based on avg segment length)

_PROMPT = """Bạn là hệ thống làm sạch transcript tiếng Việt.

Nhiệm vụ với MỖI segment:
- Sửa lỗi chính tả, nhận dạng sai (STT errors)
- Thêm dấu câu nếu cần
- Chuẩn hóa câu

QUY TẮC NGHIÊM NGẶT:
- Không thêm thông tin mới
- Không suy diễn, không tóm tắt
- Không đổi meaning
- Giữ nguyên thuật ngữ kỹ thuật (API, JWT, WebSocket...)
- Nếu không chắc → giữ nguyên nguyên văn

Input là JSON array các segment:
{segments_json}

Output: JSON array cùng số phần tử, chỉ có "id" và "cleaned_text":
[{{"id": 0, "cleaned_text": "..."}}, ...]"""


class TranscriptCleaner:
    """
    Cleans Vietnamese meeting transcript via LLM.

    OPTIMIZED: processes segments in batches of {batch_size} instead of one
    call per segment. For a 3-4 hour meeting this reduces LLM calls by ~{batch_size}x.
    Falls back to original text on any guard failure.
    """.format(batch_size=_BATCH_SIZE)

    def __init__(self):
        self._runner = PromptRunner()

    def clean(self, transcript: CleanedTranscript) -> CleanedTranscript:
        if not transcript.segments:
            return transcript

        # Split into batches
        segs = transcript.segments
        batches = [segs[i:i + _BATCH_SIZE] for i in range(0, len(segs), _BATCH_SIZE)]

        cleaned_segments: list[TranscriptSegment] = []
        fallback_count = 0

        for batch_idx, batch in enumerate(batches):
            results = self._clean_batch(batch)
            for local_idx, seg in enumerate(batch):
                cleaned_text = results.get(local_idx)
                if cleaned_text is None:
                    cleaned_text = seg.text
                    fallback_count += 1
                cleaned_segments.append(TranscriptSegment(
                    segment_id=seg.segment_id,
                    speaker=seg.speaker,
                    start=seg.start,
                    end=seg.end,
                    text=cleaned_text,
                    confidence=seg.confidence,
                    stt_confidence=seg.stt_confidence,
                    speaker_confidence=seg.speaker_confidence,
                    needs_review=seg.needs_review,
                ))

            logger.debug(f"TranscriptCleaner: batch {batch_idx + 1}/{len(batches)} done")

        cleaned_count = len(cleaned_segments) - fallback_count
        logger.info(
            f"TranscriptCleaner: {cleaned_count} segments cleaned in "
            f"{len(batches)} batch(es), {fallback_count} fallback to original"
        )

        full_text = "\n".join(f"{s.speaker}: {s.text}" for s in cleaned_segments)

        return CleanedTranscript(
            segments=cleaned_segments,
            language=transcript.language,
            duration_seconds=transcript.duration_seconds,
            speaker_count=transcript.speaker_count,
            full_text=full_text,
        )

    def _clean_batch(self, batch: list[TranscriptSegment]) -> dict[int, str | None]:
        """Send a batch of segments to LLM, return {local_idx: cleaned_text}."""
        import json
        segments_json = json.dumps(
            [{"id": i, "text": seg.text} for i, seg in enumerate(batch)],
            ensure_ascii=False,
        )
        prompt = _PROMPT.format(segments_json=segments_json)
        data = self._runner.run_json(
            system_prompt="You are a Vietnamese transcript cleaning system. Respond only with valid JSON array.",
            user_prompt=prompt,
            temperature=0.1,
        )
        results: dict[int, str | None] = {}
        if not isinstance(data, list):
            return results  # all fallback

        for item in data:
            if not isinstance(item, dict):
                continue
            idx = item.get("id")
            text = item.get("cleaned_text", "")
            if not isinstance(idx, int) or idx < 0 or idx >= len(batch):
                continue
            original = batch[idx].text
            cleaned = self._validate(text, original)
            results[idx] = cleaned

        return results

    def _validate(self, cleaned_text: str, original: str) -> str | None:
        """Apply guards. Return None to trigger fallback to original."""
        if not cleaned_text or not cleaned_text.strip():
            return None
        if len(cleaned_text) > 2.5 * len(original):
            return None
        if "SPEAKER_" in cleaned_text:
            return None
        original_tokens = self._extract_guard_tokens(original)
        if original_tokens and not original_tokens.issubset(
            self._extract_guard_tokens(cleaned_text)
        ):
            return None
        return " ".join(cleaned_text.split())

    def _extract_guard_tokens(self, text: str) -> set[str]:
        """
        Extract tokens that must be preserved through cleaning:
        - Words containing digits (e.g. "10/5", "v2", "task_123")
        - Uppercase words >= 2 chars (e.g. "API", "STT", "LLM")
        - Words containing special chars: - _ / . (e.g. "end-to-end", "gpt-4o")
        """
        tokens: set[str] = set()
        for word in text.split():
            if any(ch.isdigit() for ch in word):
                tokens.add(word)
            elif word.isupper() and len(word) >= 2:
                tokens.add(word)
            elif any(ch in word for ch in ("-", "_", "/", ".")):
                tokens.add(word)
        return tokens

