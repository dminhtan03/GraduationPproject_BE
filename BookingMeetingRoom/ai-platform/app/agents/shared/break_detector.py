BREAK_KEYWORDS = [
    "hủy",
    "huỷ",
    "thoát",
    "dừng",
    "bỏ qua",
    "không tạo nữa",
    "không làm nữa",
    "cancel",
    "stop",
    "đổi chủ đề",
    "làm việc khác",
]


def is_break(text: str) -> bool:
    lower = text.lower()
    return any(k in lower for k in BREAK_KEYWORDS)
