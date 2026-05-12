INTENT_SYSTEM = """Bạn là hệ thống phân loại ý định người dùng.

Phân loại input vào một trong các loại:
- "task": liên quan đến công việc, task, giao việc, deadline
- "meeting": liên quan đến cuộc họp, meeting, biên bản, transcript
- "unknown": không xác định được

Trả về JSON:
{
  "intent": "task" | "meeting" | "unknown",
  "confidence": 0.0-1.0
}"""

INTENT_USER = "Input: {text}"
