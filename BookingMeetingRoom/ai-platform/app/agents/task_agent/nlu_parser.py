"""
Hybrid NLU Parser — Deterministic-first, LLM-fallback.

2-pass strategy (simpler, more robust):
    Pass 1 — Remove deadline/priority from text  → avoid contaminating name/title
    Pass 2 — Split at "cho" → assignee right, title left
    LLM    — Called only when confidence < threshold

Why 2-pass beats 1-regex:
    Single regex with lookahead breaks under re.IGNORECASE ([A-Z] matches lowercase).
    2-pass separates concerns: deadline/priority extraction is independent of name parsing.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from typing import Optional


# ── Constants ─────────────────────────────────────────────────────────────────

CONFIDENCE_THRESHOLD = 0.75
ALLOWED_PRIORITIES   = {"low", "high", "urgent"}


# ── Data model ────────────────────────────────────────────────────────────────

@dataclass
class ParsedPayload:
    action:        str           = "unknown"
    task_title:    Optional[str] = None
    assignee_name: Optional[str] = None
    deadline:      Optional[str] = None   # ISO 8601
    priority:      Optional[str] = None   # low | high | urgent
    confidence:    float         = 0.0
    source:        str           = "none" # rule | llm | merged
    raw_text:      str           = ""

    def is_complete_assign(self) -> bool:
        return bool(self.assignee_name and self.task_title)

    def missing_fields(self) -> list[str]:
        out = []
        if not self.assignee_name: out.append("assignee_name")
        if not self.task_title:    out.append("task_title")
        return out


# ── Text Normalizer ───────────────────────────────────────────────────────────

class TextNormalizer:
    @staticmethod
    def normalize(text: str) -> str:
        text = unicodedata.normalize("NFC", text)
        text = re.sub(r"\s+", " ", text).strip()
        return text


# ── Deadline Parser ───────────────────────────────────────────────────────────

class DeadlineParser:
    _WEEKDAY_VI = {
        "thu hai": 0, "thu 2": 0, "t2": 0,
        "thu ba":  1, "thu 3": 1, "t3": 1,
        "thu tu":  2, "thu 4": 2, "t4": 2,
        "thu nam": 3, "thu 5": 3, "t5": 3,
        "thu sau": 4, "thu 6": 4, "t6": 4,
        "thu bay": 5, "thu 7": 5, "t7": 5,
        "chu nhat": 6, "cn": 6,
        "cuoi tuan": 5,
    }

    @classmethod
    def parse(cls, raw: str) -> Optional[str]:
        t = cls._remove_accents(raw.strip().lower())
        today = date.today()

        if t in ("mai", "ngay mai"):
            return (today + timedelta(days=1)).isoformat()
        if t in ("ngay kia", "ngay kia"):
            return (today + timedelta(days=2)).isoformat()
        if t in ("hom nay",):
            return today.isoformat()
        if "tuan sau" in t:
            days = (0 - today.weekday()) % 7 + 7
            return (today + timedelta(days=days)).isoformat()
        for pat, wd in cls._WEEKDAY_VI.items():
            if t.startswith(pat):
                d = (wd - today.weekday()) % 7 or 7
                return (today + timedelta(days=d)).isoformat()

        m = re.fullmatch(r"(\d{1,2})[/\-](\d{1,2})(?:[/\-](\d{4}))?", raw.strip())
        if m:
            try:
                return date(int(m.group(3) or today.year), int(m.group(2)), int(m.group(1))).isoformat()
            except ValueError:
                pass

        m = re.search(r"(\d{1,2})\s*thang\s*(\d{1,2})(?:\s+(\d{4}))?", t)
        if m:
            try:
                return date(int(m.group(3) or today.year), int(m.group(2)), int(m.group(1))).isoformat()
            except ValueError:
                pass
        return None

    @staticmethod
    def _remove_accents(text: str) -> str:
        return "".join(
            c for c in unicodedata.normalize("NFD", text)
            if unicodedata.category(c) != "Mn"
        )


# ── Priority Parser ───────────────────────────────────────────────────────────

class PriorityParser:
    _MAP = {
        "urgent": "urgent", "khan cap": "urgent", "khan": "urgent", "ngay lap tuc": "urgent",
        "cao": "high",    "high": "high",    "gap": "high",    "gap len": "high",
        "quan trong": "high", "rat quan trong": "urgent",
        "thap": "low",    "low": "low",    "khong gap": "low", "tu tu": "low",
    }

    @classmethod
    def parse(cls, raw: str) -> Optional[str]:
        t = cls._no_accents(raw.strip().lower())
        if t in cls._MAP:
            return cls._MAP[t]
        for k, v in cls._MAP.items():
            if k in t:
                return v
        return None

    @staticmethod
    def _no_accents(text: str) -> str:
        return "".join(
            c for c in unicodedata.normalize("NFD", text)
            if unicodedata.category(c) != "Mn"
        )


# ── Rule-Based Parser (2-pass) ────────────────────────────────────────────────

# Patterns nhận diện deadline prefix
_DEADLINE_PREFIX_RE = re.compile(
    r"(?:deadline|han\s*chot?|han|truoc\s+ngay|den\s+ngay|truoc)\s+"
    r"(?P<dl>\d{1,2}[/\-]\d{1,2}(?:[/\-]\d{4})?|\d{1,2}\s+thang\s+\d{1,2}(?:\s+\d{4})?"
    r"|ngay mai|mai|ngay kia|hom nay|cuoi tuan|tuan sau"
    r"|chu nhat|thu\s*[2-7]|t[2-7])",
    re.IGNORECASE,
)

# Deadline pattern với dấu tiếng Việt
# (?:^|(?<=\s)) = word boundary: chỉ match đầu câu hoặc sau khoảng trắng
# Tránh bắt "han" trong "phan", "than", etc.
_DEADLINE_VI_RE = re.compile(
    r"(?:^|(?<=\s))(?:deadline|h[aạ]n\s*ch[oóô]t?|h[aạ]n|tr[uướ][oơ]c\s+ng[aà]y"
    r"|[dđ][eế]n\s+ng[aà]y|tr[uướ][oơ]c)(?=\s)"
    r"\s+"
    r"(?P<dl>[^\s,;!?]+(?:\s+[^\s,;!?]+)?)",
    re.IGNORECASE,
)

# Priority pattern
_PRIORITY_RE = re.compile(
    r"(?:[uưú]u\s+ti[eêé]n|priority)\s+"
    r"(?P<p>cao|th[aấ]p|low|high|urgent|g[aấ]p|kh[aẩ]n)"
    r"|(?P<p2>urgent|g[aấ]p\s+l[eê]n|kh[aẩ]n\s+c[aấ]p)",
    re.IGNORECASE,
)

# Xưng hô tiếng Việt cần bỏ khỏi tên: "anh Đạt" → "Đạt", "a Minh" → "Minh"
_HONORIFIC_RE = re.compile(
    r"^(?:anh|ch[iị]|em|[oôô]ng|b[àa]|c[oô]|ch[uú]|b[aá]c|th[aầ]y|a)\s+",
    re.IGNORECASE,
)


def _strip_honorifics(name: str) -> str:
    """Bỏ xưng hô đầu tên: 'anh Đạt' → 'Đạt', 'a Minh' → 'Minh'."""
    return _HONORIFIC_RE.sub("", name.strip()).strip()


def _split_name_and_title(text: str) -> tuple[str, Optional[str]]:
    """
    Tách tên người và mô tả task từ chuỗi gộp.
    'Đạt viết báo cáo' → ('Đạt', 'viết báo cáo')
    'Nguyễn Văn Đạt viết báo cáo' → ('Nguyễn Văn Đạt', 'viết báo cáo')
    Heuristic: từ viết hoa liên tiếp ở đầu = tên; phần còn lại = title.
    """
    words = text.split()
    if not words:
        return text, None
    name_words: list[str] = []
    for i, w in enumerate(words):
        if w[0].isupper():
            name_words.append(w)
        else:
            title_part = " ".join(words[i:]).strip()
            return " ".join(name_words) if name_words else words[0], title_part or None
    # Tất cả đều viết hoa → toàn bộ là tên
    return " ".join(name_words), None


# Động từ đứng một mình (không có gì theo sau) → không phải title
_STANDALONE_VERB_RE = re.compile(
    r"^(?:t[oô]i\s+(?:mu[oô]n|c[aầ]n|s[eẽ])\s+)?"
    r"(?:h[aã]y\s+)?(?:gi[uú]p\s+(?:t[oô]i|m[iì]nh)\s+)?"
    r"(?:giao|assign|ph[aâ]n\s+c[oô]ng|t[aạ]o|th[eê]m|create)\s*$",
    re.IGNORECASE,
)

# Verb phrases cần bỏ khi extract title
_VERB_PREFIXES = [
    # order matters: longer patterns first
    re.compile(
        r"^(?:t[oô]i\s+(?:mu[oô]n|c[aầ]n|s[eẽ])\s+)?"
        r"(?:h[aã]y\s+)?(?:gi[uú]p\s+(?:t[oô]i|m[iì]nh)\s+)?"
        r"(?:giao|assign|ph[aâ]n\s+c[oô]ng)\s+"
        r"(?:gi[uú]p\s+(?:t[oô]i|m[iì]nh)\s+)?"
        r"(?:task|vi[eệ]c|nhi[eệ]m\s+v[uụ]|c[oô]ng\s+vi[eệ]c)\s*",
        re.IGNORECASE,
    ),
    re.compile(
        r"^(?:t[oô]i\s+(?:mu[oô]n|c[aầ]n|s[eẽ])\s+)?"
        r"(?:h[aã]y\s+)?(?:gi[uú]p\s+(?:t[oô]i|m[iì]nh)\s+)?"
        r"(?:giao|assign)\s+",
        re.IGNORECASE,
    ),
]


class RuleBasedParser:
    """
    2-pass deterministic parser cho tiếng Việt:

    Pass 1: Extract và xoá deadline/priority khỏi text
    Pass 2: Tách "cho [ASSIGNEE]" → assignee; phần còn lại → title
    """

    # Pattern đảo ngược: "giao cho [NGƯỜI] [noun] [TITLE]"
    _INVERTED_RE = re.compile(
        r"^(?:t[oô]i\s+(?:mu[oô]n|c[aầ]n)\s+)?(?:h[aã]y\s+)?"
        r"(?:giao|assign|ph[aâ]n\s+c[oô]ng)\s+cho\s+"
        r"(?P<assignee>\S+(?:\s+\S+){0,3}?)\s+"
        r"(?:task|vi[eệ]c|nhi[eệ]m\s+v[uụ]|c[oô]ng\s+vi[eệ]c)\s+"
        r"(?P<title>.+)$",
        re.IGNORECASE,
    )

    def parse_assign(self, raw_text: str) -> ParsedPayload:
        result = ParsedPayload(action="assign_task", raw_text=raw_text, source="rule")
        norm   = TextNormalizer.normalize(raw_text)

        # ── Thử pattern đảo ngược trước: "giao cho [NGƯỜI] [noun] [TITLE]" ──
        inv = self._INVERTED_RE.match(norm)
        if inv:
            result.assignee_name = _strip_honorifics(inv.group("assignee").strip())
            result.task_title    = inv.group("title").strip() or None
            deadline_raw, _ = self._extract_and_remove_deadline(norm)
            priority_raw, _ = self._extract_and_remove_priority(norm)
            if deadline_raw:
                result.deadline = DeadlineParser.parse(deadline_raw) or deadline_raw
            if priority_raw:
                result.priority = PriorityParser.parse(priority_raw)
            # Xoá deadline/priority khỏi title nếu bị sót
            if result.task_title:
                result.task_title = _clean_title(result.task_title) or None
            result.confidence = self._score(result)
            return result

        # ── Pass 1: Extract deadline + priority, xoá khỏi text ───────────────
        deadline_raw, norm_clean = self._extract_and_remove_deadline(norm)
        priority_raw, norm_clean = self._extract_and_remove_priority(norm_clean)

        # ── Pass 2: Tách tại "cho" ─────────────────────────────────────────
        # Tìm "cho" theo sau bởi nội dung (có thể là tên người)
        # Dùng LAST "cho" để tránh "task làm báo cáo cho khách cho Minh"
        cho_positions = [m.start() for m in re.finditer(r"\bcho\b", norm_clean, re.IGNORECASE)]
        if not cho_positions:
            result.confidence = 0.1
            return result

        # Chọn "cho" phù hợp nhất:
        # - Ưu tiên "cho" được theo sau bởi ký tự hoa (tên người)
        # - Fallback: "cho" cuối cùng
        best_cho_pos = None
        for pos in cho_positions:
            after = norm_clean[pos + 3:].lstrip()
            if after and after[0].isupper():
                best_cho_pos = pos
                break
        if best_cho_pos is None:
            best_cho_pos = cho_positions[-1]  # fallback: cho cuối

        before_cho = norm_clean[:best_cho_pos].strip()
        after_cho  = norm_clean[best_cho_pos + 3:].strip()  # +3 = len("cho")

        raw_assignee = after_cho.strip() if after_cho else None

        # ── Extract title từ before_cho ──────────────────────────────────────
        title = self._strip_verb_prefix(before_cho)
        result.task_title = title if title else None

        # ── Xử lý assignee: bỏ xưng hô, tách tên + mô tả khi title rỗng ────
        if raw_assignee:
            cleaned = _strip_honorifics(raw_assignee)
            if not result.task_title:
                # Trường hợp "Giao cho a Đạt viết báo cáo" → after_cho = "Đạt viết báo cáo"
                # Tách tên (từ viết hoa) và phần còn lại thành title
                name, extra = _split_name_and_title(cleaned)
                result.assignee_name = name or cleaned
                if extra:
                    result.task_title = extra
            else:
                result.assignee_name = cleaned
        else:
            result.assignee_name = None

        # ── Apply deadline/priority ──────────────────────────────────────────
        if deadline_raw:
            result.deadline = DeadlineParser.parse(deadline_raw) or deadline_raw
        if priority_raw:
            result.priority = PriorityParser.parse(priority_raw)

        result.confidence = self._score(result)
        return result

    def parse_create(self, raw_text: str) -> ParsedPayload:
        result = ParsedPayload(action="create_task", raw_text=raw_text, source="rule")
        norm   = TextNormalizer.normalize(raw_text)

        deadline_raw, norm_clean = self._extract_and_remove_deadline(norm)
        priority_raw, norm_clean = self._extract_and_remove_priority(norm_clean)

        title = self._strip_verb_prefix(norm_clean)
        result.task_title = title if title else None

        if deadline_raw:
            result.deadline = DeadlineParser.parse(deadline_raw) or deadline_raw
        if priority_raw:
            result.priority = PriorityParser.parse(priority_raw)

        result.confidence = 0.85 if result.task_title else 0.3
        return result

    # ── Internals ─────────────────────────────────────────────────────────────

    @staticmethod
    def _extract_and_remove_deadline(text: str):
        """Trả về (raw_deadline_string | None, text_đã_xoá_deadline)."""
        m = _DEADLINE_VI_RE.search(text)
        if not m:
            return None, text
        raw = m.group("dl").strip()
        cleaned = text[:m.start()].rstrip() + " " + text[m.end():].lstrip()
        return raw, cleaned.strip()

    @staticmethod
    def _extract_and_remove_priority(text: str):
        """Trả về (raw_priority_string | None, text_đã_xoá_priority)."""
        m = _PRIORITY_RE.search(text)
        if not m:
            return None, text
        raw = (m.group("p") or m.group("p2") or "").strip()
        cleaned = text[:m.start()].rstrip() + " " + text[m.end():].lstrip()
        return raw, cleaned.strip()

    @staticmethod
    def _strip_verb_prefix(text: str) -> Optional[str]:
        """Bỏ verb phrase ở đầu, trả về title còn lại."""
        # Nếu toàn bộ text chỉ là động từ hành động (ví dụ "Giao") → không phải title
        if _STANDALONE_VERB_RE.match(text):
            return None
        for pattern in _VERB_PREFIXES:
            m = pattern.match(text)
            if m:
                remaining = text[m.end():].strip()
                return remaining if remaining else None
        return text.strip() if text.strip() else None

    @staticmethod
    def _score(p: ParsedPayload) -> float:
        s = 0.0
        if p.assignee_name: s += 0.50
        if p.task_title:    s += 0.35
        if p.deadline:      s += 0.10
        if p.priority:      s += 0.05
        return min(s, 1.0)


# ── Validation Layer ──────────────────────────────────────────────────────────

class PayloadValidator:
    @staticmethod
    def validate(p: ParsedPayload) -> ParsedPayload:
        if p.task_title:
            p.task_title = _clean_title(p.task_title) or None
        if p.assignee_name:
            p.assignee_name = p.assignee_name.strip().rstrip(".,;!? ")
            if len(p.assignee_name) < 2:
                p.assignee_name = None
        if p.priority and p.priority not in ALLOWED_PRIORITIES:
            p.priority = PriorityParser.parse(p.priority)
        if p.deadline and not _is_iso(p.deadline):
            p.deadline = DeadlineParser.parse(p.deadline)
        return p


def _clean_title(t: str) -> str:
    # Bỏ trailing deadline/priority keywords
    t = re.sub(
        r"\s+(?:deadline|h[aạ]n|[uưú]u\s+ti[eêé]n|priority|urgent|g[aấ]p).*$",
        "", t, flags=re.IGNORECASE,
    ).strip()
    return t.rstrip(".,;!? -")


def _is_iso(s: str) -> bool:
    try:
        datetime.fromisoformat(s)
        return True
    except ValueError:
        return False


# ── Merge Layer ───────────────────────────────────────────────────────────────

class MergeLayer:
    @staticmethod
    def merge(rule: ParsedPayload, llm: Optional[ParsedPayload]) -> ParsedPayload:
        if not llm:
            return rule
        out = ParsedPayload(action=rule.action, raw_text=rule.raw_text, source="merged")
        out.task_title    = rule.task_title    or llm.task_title
        out.assignee_name = rule.assignee_name or llm.assignee_name
        out.deadline      = rule.deadline      or llm.deadline
        out.priority      = rule.priority      or llm.priority
        out.confidence    = RuleBasedParser._score(out)
        return out


# ── Hybrid Parser ─────────────────────────────────────────────────────────────

class HybridNLUParser:
    """
    Entry point: rule-based (fast) → LLM (fallback) → merge → validate.

    Usage:
        parser = HybridNLUParser(llm_runner=prompt_runner)
        p = parser.parse_assign("giao task backend cho Nguyễn Văn A deadline mai")
        # p.task_title = "backend"
        # p.assignee_name = "Nguyễn Văn A"
        # p.deadline = "2026-05-08"
    """

    def __init__(self, llm_runner=None):
        self._rule   = RuleBasedParser()
        self._runner = llm_runner

    def parse_assign(self, text: str) -> ParsedPayload:
        rule = self._rule.parse_assign(text)
        if rule.confidence >= CONFIDENCE_THRESHOLD:
            return PayloadValidator.validate(rule)
        llm    = self._llm_assign(text)
        merged = MergeLayer.merge(rule, llm)
        return PayloadValidator.validate(merged)

    def parse_create(self, text: str) -> ParsedPayload:
        rule = self._rule.parse_create(text)
        if rule.confidence >= CONFIDENCE_THRESHOLD:
            return PayloadValidator.validate(rule)
        llm    = self._llm_create(text)
        merged = MergeLayer.merge(rule, llm)
        return PayloadValidator.validate(merged)

    def _llm_assign(self, text: str) -> Optional[ParsedPayload]:
        if not self._runner:
            return None
        prompt = (
            f"Trich xuat tu lenh giao task tieng Viet.\n"
            f"Tra ve JSON: {{\"task_title\":\"...\",\"assignee_name\":\"...\","
            f"\"deadline\":\"ISO8601|null\",\"priority\":\"low|high|urgent|null\"}}\n"
            f"Hom nay: {date.today().isoformat()}\nInput: {text}"
        )
        r = self._runner.run_json("Ban la NLU parser.", prompt)
        if not isinstance(r, dict):
            return None
        return ParsedPayload(
            action="assign_task", raw_text=text, source="llm", confidence=0.6,
            task_title=r.get("task_title"), assignee_name=r.get("assignee_name"),
            deadline=r.get("deadline"), priority=r.get("priority"),
        )

    def _llm_create(self, text: str) -> Optional[ParsedPayload]:
        if not self._runner:
            return None
        prompt = (
            f"Trich xuat tu lenh tao task tieng Viet.\n"
            f"Tra ve JSON: {{\"task_title\":\"...\","
            f"\"deadline\":\"ISO8601|null\",\"priority\":\"low|high|urgent|null\"}}\n"
            f"Hom nay: {date.today().isoformat()}\nInput: {text}"
        )
        r = self._runner.run_json("Ban la NLU parser.", prompt)
        if not isinstance(r, dict):
            return None
        return ParsedPayload(
            action="create_task", raw_text=text, source="llm", confidence=0.6,
            task_title=r.get("task_title"),
            deadline=r.get("deadline"), priority=r.get("priority"),
        )


# ── Test Suite ────────────────────────────────────────────────────────────────

def run_tests() -> None:
    """python -m app.agents.task_agent.nlu_parser"""
    parser = HybridNLUParser(llm_runner=None)

    assign_cases = [
        # (input, exp_title, exp_assignee, exp_has_deadline)
        ("giao task phat trien phan mem cho Nguyen Van A",
         "phat trien phan mem", "Nguyen Van A", False),

        ("giao viec fix bug login cho Tran Van B deadline mai",
         "fix bug login", "Tran Van B", True),

        ("assign task thiet ke UI mobile app cho Minh",
         "thiet ke UI mobile app", "Minh", False),

        ("giao giup minh task lam dashboard cho Hung",
         "lam dashboard", "Hung", False),

        ("toi muon giao task review code cho Mac Tuan Son deadline thu 6",
         "review code", "Mac Tuan Son", True),

        # CRITICAL: assignee khong duoc capture "deadline mai"
        ("giao task backend cho Nguyen Van A deadline mai",
         "backend", "Nguyen Van A", True),

        ("giao task viet tai lieu API cho Pham Khac Hai Bang uu tien cao",
         "viet tai lieu API", "Pham Khac Hai Bang", False),

        # Khong co title
        ("giao task cho Tuan", None, "Tuan", False),

        # Xung ho: "a", "anh" phai bi bo
        ("Giao cho a Dat viec viet bao cao", "viet bao cao", "Dat", False),
        ("Giao cho anh Dat viec viet bao cao", "viet bao cao", "Dat", False),
        ("Giao cho a Dat viet bao cao", "viet bao cao", "Dat", False),

        # "Giao" truoc "cho" khong phai title
        ("Giao cho Thang viet bao cao phat trien phan mem", "viet bao cao phat trien phan mem", "Thang", False),
        ("Giao cho Mac Tuan Son kiem tra ket qua du an", "kiem tra ket qua du an", "Mac Tuan Son", False),

        # Pattern dao nguoc: "giao cho [NGUOI] [noun] [TITLE]"
        ("Giao cho Son nhiem vu cap nhat tien do cho du an",
         "cap nhat tien do cho du an", "Son", False),

        ("giao cho Mac Tuan Son nhiem vu phat trien backend deadline mai",
         "phat trien backend", "Mac Tuan Son", True),

        ("toi muon giao cho Hung viec fix bug login",
         "fix bug login", "Hung", False),
    ]

    passed = failed = 0
    for text, exp_t, exp_a, exp_dl in assign_cases:
        r = parser.parse_assign(text)
        ok_t  = (r.task_title    == exp_t) if exp_t is not None else r.task_title is None
        ok_a  = (r.assignee_name == exp_a) if exp_a is not None else r.assignee_name is None
        ok_dl = (r.deadline is not None)   if exp_dl else True

        if ok_t and ok_a and ok_dl:
            passed += 1
            print(f"  [OK]  {text!r}")
        else:
            failed += 1
            print(f"  [FAIL] {text!r}")
            if not ok_t:
                print(f"         title:    got={r.task_title!r}  exp={exp_t!r}")
            if not ok_a:
                print(f"         assignee: got={r.assignee_name!r}  exp={exp_a!r}")
            if not ok_dl:
                print(f"         deadline: got={r.deadline!r}  expected non-None")
            print(f"         confidence={r.confidence:.2f} source={r.source}")

    print(f"\n  Tests: {passed} passed, {failed} failed / {len(assign_cases)}")


if __name__ == "__main__":
    run_tests()
