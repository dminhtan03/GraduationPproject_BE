"""Rule-based transcript filter — no LLM, runs before any expensive stage.

Classifies each segment as WORK or NOISE using keyword matching and
heuristics. Removes NOISE segments so the LLM only sees relevant content.
"""
import re
from app.schemas.transcript import CleanedTranscript, TranscriptSegment
from app.core.logging import logger

# ── Work-relevant keywords (Vietnamese + English tech) ────────────────────────

_WORK_KEYWORDS: frozenset[str] = frozenset({
    # Task / delivery
    "hoàn thành", "làm xong", "xử lý", "tiến độ", "deadline", "hạn",
    "giao", "nhận", "phụ trách", "chịu trách nhiệm", "bổ sung", "triển khai",
    "kiểm tra", "test", "fix", "sửa", "phát triển", "xây dựng", "cài đặt",
    "demo", "release", "deploy", "lên production", "merge",
    # Technical terms
    "api", "backend", "frontend", "database", "server", "vps", "gpu",
    "websocket", "nginx", "proxy", "storage", "upload", "download",
    "whisper", "llm", "model", "transcript", "audio",
    "ai agent", "ai platform", "ai model", "openai", "chatgpt", "gpt",
    "jwt", "token", "auth", "oauth", "ssl", "https",
    "docker", "kubernetes", "queue", "worker", "cache", "redis",
    "sql", "migration", "schema", "endpoint", "route",
    # Meeting / project
    "cuộc họp", "sprint", "ticket", "task", "issue", "bug", "feature",
    "khách hàng", "yêu cầu", "requirement", "milestone", "objective",
    "báo cáo", "report", "review", "approve", "reject",
    "deadline tuần", "hoàn thành trước", "xong trước", "dự kiến xong",
    "khoảng", "dự kiến", "ước tính", "estimate",
    # Action verbs in context — must appear with a subject/object
    "sẽ làm", "sẽ xử lý", "sẽ hoàn thành", "sẽ kiểm tra", "sẽ triển khai",
    "em làm", "anh làm", "em xử lý", "anh xử lý", "em kiểm tra",
})

# ── Noise patterns (small talk, off-topic) ─────────────────────────────────────

_NOISE_PATTERNS: list[re.Pattern] = [re.compile(p, re.IGNORECASE) for p in [
    # Food / drink ordering
    r"ăn gì|trưa nay|cơm\b|bún|phở|cà phê|uống gì|cafe|căng tin|mua đen đá|mua đèn đá",
    r"đặt cơm|đặt bún|đặt phở|ai đặt theo|đặt theo không",
    r"trả tiền|thanh toán bữa|order.*ăn|cho tao với",
    r"\btao order\b|order\s+(luôn|theo|nữa|cho|thêm)\b",
    # Entertainment
    r"xem phim|phim hàn|tập \d+|netflix|phim mày giới thiệu",
    r"tình tiết\b|twitch\b|streamer\b|youtube.*(?:xem|coi)",
    r"chưa kịp xem|kịp xem rồi|coi chưa\b|chưa coi",
    # Personal state
    r"buồn ngủ|mệt quá|đói quá|khát nước",
    # Personal schedule / off-topic
    r"đến trễ|đến sớm|ngủ sớm|ngủ muộn|đặt chuông|tối qua ngủ",
    r"\bmày\b.*\bđến\b|\bthường mày\b",
    r"tí nữa\s+họp|họp xong\s*(đi|rồi|nhé)|sắp vào rồi",
    # Social reactions without work content
    r"hay lắm,?\s+tình tiết|tình tiết.*(?:bất ngờ|kinh|hay)",
    r"\bý tưởng hay\b|\bhay đấy\b|\btuyệt đấy\b|\bnghe hay đó\b",
    r"sorry\s*$",              # aborted/incomplete sentence ending with apology
    # Reactions / filler
    r"haha|hihi|hehe|😂|😅",
    r"thôi thôi|thôi vậy|tập trung lại đi",
    r"cuối tuần đi chơi|du lịch|đi nhậu|đi bia",
    r"hay không\??\s*$|vui không|dễ thương",
    # Short acknowledgments / fillers (single or double word)
    r"^(ừ+|ok|oke|vâng|rồi|thôi|ừa|à+|ờ+|có|được)\s*\.?$",
    r"^(đúng rồi|xong rồi|vậy à|nghe rồi|hiểu rồi|ok đó|oke đó|ừ đúng|à đúng|ok thôi)\s*\.?$",
]]

# ── Config ────────────────────────────────────────────────────────────────────

_MIN_DURATION_SEC   = 1.5    # skip very short utterances
_MIN_WORDS          = 4      # skip utterances with too few words
_NOISE_THRESHOLD    = 0.65   # fraction of noise signals to classify as NOISE


class TranscriptFilter:
    """
    Rule-based pre-filter: removes off-topic/small-talk segments.

    Insert this stage AFTER alignment and BEFORE TranscriptCleaner so that
    LLM stages never see irrelevant content.

    For a 3-4 hour meeting this typically removes 20-40% of segments,
    saving proportional LLM tokens.
    """

    def filter(self, transcript: CleanedTranscript) -> CleanedTranscript:
        if not transcript.segments:
            return transcript

        kept: list[TranscriptSegment] = []
        dropped: list[TranscriptSegment] = []

        for seg in transcript.segments:
            if self._is_work_relevant(seg):
                kept.append(seg)
            else:
                dropped.append(seg)

        drop_pct = 100 * len(dropped) / max(len(transcript.segments), 1)
        logger.info(
            f"TranscriptFilter: kept {len(kept)}/{len(transcript.segments)} segments "
            f"({drop_pct:.1f}% filtered out)"
        )
        if dropped:
            logger.debug("Dropped segments: " + "; ".join(
                f"[{s.speaker}] {s.text[:60]}" for s in dropped[:5]
            ))

        full_text = "\n".join(f"{s.speaker}: {s.text}" for s in kept)

        return CleanedTranscript(
            segments=kept,
            language=transcript.language,
            duration_seconds=transcript.duration_seconds,
            speaker_count=transcript.speaker_count,
            full_text=full_text,
        )

    # ── Private ───────────────────────────────────────────────────────────────

    def _is_work_relevant(self, seg: TranscriptSegment) -> bool:
        text = (seg.text or "").strip()
        if not text:
            return False

        text_lower = text.lower()

        # Fast keep: any work keyword → keep immediately, skip all noise checks
        if any(kw in text_lower for kw in _WORK_KEYWORDS):
            return True

        # Noise check runs on ALL lengths — catches short fillers like "thôi vậy",
        # "Ok tao order luôn cho", single-word acks, etc.
        if any(p.search(text) for p in _NOISE_PATTERNS):
            return False

        words = text.split()

        # Too short to classify further → keep (avoid losing critical short confirmations)
        if len(words) < _MIN_WORDS:
            return True

        # Very short duration + few words → likely transcription artifact / filler
        duration = (seg.end or 0) - (seg.start or 0)
        if duration < _MIN_DURATION_SEC and len(words) < 6:
            return False

        return True