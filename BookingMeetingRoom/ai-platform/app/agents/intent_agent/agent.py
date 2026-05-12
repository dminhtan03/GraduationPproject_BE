import re as _re

from app.agents.intent_agent.schemas import IntentResult, IntentType
from app.core.logging import logger

_SUMMARY_KEYWORDS = {
    "hôm nay", "today", "ngày hôm nay", "lịch hôm nay",
    "hôm nay có gì", "hôm nay tôi có", "hôm nay làm gì",
    "việc hôm nay", "task hôm nay", "meeting hôm nay",
    "họp hôm nay", "tóm tắt", "tổng quan ngày",
    "tôi có những gì", "những việc hôm nay", "các việc hôm nay",
}
_TASK_KEYWORDS = {
    "task", "việc", "công việc", "giao", "làm", "deadline",
    "nhiệm vụ", "tạo", "thêm", "xóa", "cập nhật",
    "create", "add", "delete", "update", "tạo task",
}
_MEETING_KEYWORDS = {
    "họp", "meeting", "cuộc họp", "biên bản", "transcript", "audio",
    "ghi âm",
}

# Từ khoá rõ ràng ngoài lề công việc
_OFF_TOPIC_KEYWORDS = {
    "thủ đô", "dân số", "diện tích", "núi nào cao nhất",
    "ai là tổng thống", "bao nhiêu hành tinh",
    "tiểu sử", "sinh năm bao nhiêu",
    "bài hát", "ca sĩ", "phim hay", "diễn viên",
    "thời tiết hôm nay", "nhiệt độ ngoài trời",
    "nấu ăn", "công thức nấu", "nguyên liệu nấu",
    "trò chơi", "minecraft", "liên minh huyền thoại",
    "dịch sang tiếng", "translate to",
    "kể chuyện cổ tích", "bài thơ", "viết thơ",
    "joke", "kể joke",
}

# Regex: phép tính toán học thuần túy
_OFF_TOPIC_REGEX = [
    _re.compile(r"^\s*[\d\s\+\-\*\/x×÷\(\)\.]+[=\?]\s*$"),   # 1+1=? / (2+3)*4?
    _re.compile(r"^\s*\d+\s*[\+\-\*\/x×÷]\s*\d+\s*$"),        # 1+1 / 2*3
    _re.compile(r"bằng bao nhiêu\s*\??$"),
    _re.compile(r"^\s*bao nhiêu là\s+[\d\+\-\*\/]"),
]


def _is_off_topic(text: str) -> bool:
    """Phát hiện câu hỏi rõ ràng ngoài lề công việc."""
    lower = text.lower().strip()
    if any(kw in lower for kw in _OFF_TOPIC_KEYWORDS):
        return True
    if any(pat.search(lower) for pat in _OFF_TOPIC_REGEX):
        return True
    return False


class IntentAgent:
    """Classifies user input into an intent category."""

    def classify(self, text: str) -> IntentType:
        logger.info(f"IntentAgent: classifying (len={len(text)})")
        lower = text.lower()

        # Summary/daily intent takes priority over task/meeting
        if any(kw in lower for kw in _SUMMARY_KEYWORDS):
            logger.info("IntentAgent: intent=summary")
            return "summary"

        if any(kw in lower for kw in _MEETING_KEYWORDS):
            logger.info("IntentAgent: intent=meeting")
            return "meeting"

        if any(kw in lower for kw in _TASK_KEYWORDS):
            logger.info("IntentAgent: intent=task")
            return "task"

        if _is_off_topic(text):
            logger.info("IntentAgent: intent=off_topic")
            return "off_topic"

        logger.info("IntentAgent: intent=unknown")
        return "unknown"

    def classify_full(self, text: str) -> IntentResult:
        intent = self.classify(text)
        return IntentResult(intent=intent, text=text, confidence=1.0)
