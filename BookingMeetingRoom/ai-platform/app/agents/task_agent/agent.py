import re
from datetime import date
from typing import Any, Optional
from app.agents.task_agent.nlu_parser import HybridNLUParser
from app.agents.task_agent.schemas import TaskAgentInput, TaskAgentResult
from app.agents.task_agent.prompts import TASK_ACTION_SYSTEM, TASK_ACTION_USER
from app.agents.shared.conversation_state import (
    ConversationState, get_state, set_state, clear_state,
)
from app.services.backend_api.client import BackendAPIClient
from app.services.backend_api.task_api import TaskAPI
from app.services.llm.prompt_runner import PromptRunner
from app.core.logging import logger

_CREATE_PREFIXES = [
    r"tạo cho (?:tôi|mình)\s+(?:một\s+)?(?:task|việc|nhiệm vụ|công việc)\s*[:\-]?\s*",
    r"tạo (?:task|việc|nhiệm vụ|công việc)\s*[:\-]?\s*",
    r"thêm (?:task|việc|nhiệm vụ)\s*[:\-]?\s*",
    r"create task\s*[:\-]?\s*",
]
_CREATE_KEYWORDS        = ["tạo task", "tạo việc", "thêm task", "thêm việc", "create task",
                            "tạo nhiệm vụ", "tạo công việc", "tạo cho tôi"]
_LIST_PERSONAL_KEYWORDS = ["task cá nhân", "việc cá nhân", "nhiệm vụ cá nhân",
                            "task của tôi", "việc của tôi", "công việc của tôi",
                            "danh sách task", "danh sách việc", "xem task", "xem việc"]
_LIST_ASSIGNED_KEYWORDS = ["task được giao", "việc được giao", "task giao đến",
                            "task giao cho tôi", "việc giao cho tôi",
                            "assigned to me", "xem task được giao", "xem việc được giao"]
_ASSIGN_KEYWORDS = [
    # cơ bản
    "giao task",
    "giao việc",
    "giao nhiệm vụ",
    "giao công việc",
    "assign",
    "assign task",
    "phân công",
    "phân công việc",
    "phân công task",
    "phân công nhiệm vụ",

    # có “cho”
    "giao cho",
    "giao việc cho",
    "giao task cho",
    "giao nhiệm vụ cho",
    "giao công việc cho",
    "assign cho",
    "assign to",
    "phân công cho",
    "phân công việc cho",
    "phân công task cho",

    # conversational
    "giao giúp",
    "giao dùm",
    "giao hộ",
    "giao giúp mình",
    "giao giúp tôi",
    "giao dùm tôi",
    "giao hộ tôi",
    "phân công giúp",
    "phân công dùm",
    "phân công hộ",

    # intent tự nhiên
    "muốn giao task",
    "muốn giao việc",
    "muốn giao nhiệm vụ",
    "tôi muốn giao task",
    "tôi muốn giao việc",
    "mình muốn giao task",
    "mình muốn giao việc",
    "cần giao task",
    "cần giao việc",
    "nhờ giao task",
    "nhờ giao việc",

    # task / ticket / issue
    "giao ticket",
    "assign ticket",
    "giao issue",
    "assign issue",
    "giao bug",
    "assign bug",
    "giao bugfix",
    "giao feature",
    "giao công tác",

    # english variations
    "delegate",
    "delegate task",
    "delegate work",
    "allocate task",
    "allocate work",
    "send task to",
    "give task to",
    "hand over task",
    "handover task",

    # informal typing
    "giao cv",
    "phân cv",
    "assign cv",
    "giao job",
    "assign job",

    # quản lý/team lead wording
    "phân task",
    "chia task",
    "chia việc",
    "chia công việc",
    "giao đầu việc",
    "phân đầu việc",
    "phân việc",

    # agile/dev wording
    "assign sprint task",
    "assign backlog item",
    "giao user story",
    "giao task sprint",
    "phân task sprint",

    # indirect phrasing
    "đưa task cho",
    "đưa việc cho",
    "đẩy task cho",
    "chuyển task cho",
    "chuyển việc cho",
    "bàn giao task cho",
    "bàn giao việc cho",

    # mixed vietnamese-english
    "giao assignment",
    "assign công việc",
    "assign nhiệm vụ",
    "assign việc",
    "assign cho tôi",
    "assign cho nhân viên",

    # manager-like requests
    "tạo task cho",
    "tạo việc cho",
    "tạo nhiệm vụ cho",
    "lập task cho",
    "lập việc cho",

    # support/chat style
    "nhờ bạn giao task",
    "giúp tôi giao task",
    "hãy giao task",
    "hãy phân công",
    "please assign",
    "pls assign",
    "assign giúp",
]


def _extract_assignee_from_text(text: str) -> Optional[str]:
    """Trích xuất tên người nhận từ câu giao task."""
    patterns = [
        r"giao (?:task|việc|nhiệm vụ|công việc)\s+(?:\S+\s+)*cho\s+(.+?)(?:\s+(?:với|deadline|hạn|về|để|task|việc)|$)",
        r"giao (?:task|việc|nhiệm vụ|công việc) cho\s+(.+?)(?:\s+(?:với|deadline|hạn|về|để)|$)",
        r"(?:tôi muốn|muốn|cần)\s+giao (?:task|việc) cho\s+(.+?)(?:\s+(?:với|deadline|hạn)|$)",
        r"assign (?:task |)(?:to|cho)\s+(.+?)(?:\s+|$)",
    ]
    for p in patterns:
        m = re.search(p, text.strip(), re.IGNORECASE)
        if m:
            name = m.group(1).strip().rstrip('.,!?')
            if 2 <= len(name) <= 80:
                return name
    return None


def _strip_self_ref(text: str) -> str:
    """Bỏ 'cho tôi/mình/em' nếu là toàn bộ nội dung còn lại."""
    cleaned = re.sub(r'^cho\s+(?:tôi|mình|em)\s*', '', text, flags=re.IGNORECASE).strip(' ,.-')
    return cleaned

def _extract_title_from_text(text: str) -> Optional[str]:
    lower = text.lower()
    for pattern in _CREATE_PREFIXES:
        m = re.search(pattern, lower)
        if m:
            rest = text[m.end():].strip()
            rest = re.split(r'(?i)\b(hạn|deadline|due|đến ngày|trước ngày|ưu tiên|priority)', rest)[0]
            rest = _strip_self_ref(rest.strip(' ,.-'))
            if len(rest) >= 2:
                return rest
    return None


def _safe_priority(p: Optional[str]) -> str:
    if p in ("high", "urgent", "low"):
        return p
    return "low"


_NO_RESPONSES = {
    "không", "ko", "k", "no", "nope", "bỏ qua", "skip",
    "thôi", "không cần", "cứ vậy thôi", "tạo đi", "được rồi",
    "oke", "ok", "fine", "pass", "đủ rồi", "vậy thôi",
}

def _is_no_response(text: str) -> bool:
    return text.strip().lower() in _NO_RESPONSES


class TaskAgent:
    def __init__(self):
        self._runner = PromptRunner()
        self._nlu    = HybridNLUParser(llm_runner=self._runner)

    def _make_client(self, token: Optional[str] = None) -> BackendAPIClient:
        return BackendAPIClient(access_token=token)

    def _make_task_api(self, token: Optional[str] = None) -> TaskAPI:
        return TaskAPI(self._make_client(token))

    # ── Entry points ──────────────────────────────────────────────────────────

    def start_flow(
        self,
        text: str,
        session_id: str,
        token: Optional[str] = None,
        user_id: Optional[str] = None,
    ) -> dict[str, Any]:
        logger.info(f"TaskAgent: start_flow session={session_id}")

        llm_output = self._parse_with_llm(text)
        action, payload = self._normalize_llm_output(llm_output)
        action = self._fallback_action_from_text(text, action)
        logger.info(f"TaskAgent: action={action} payload={payload}")

        if action == "unknown":
            return {"success": False, "message": "Tôi chưa hiểu yêu cầu. Bạn muốn tạo task, xem task, hay giao task cho ai?"}

        if action in ("list_tasks", "list_my_tasks"):
            return self._handle_list_my_tasks(token)

        if action == "list_assigned_tasks":
            return self._handle_list_assigned_tasks(token)

        if action == "assign_task":
            # Dùng HybridNLUParser để extract đầy đủ ngay lần đầu
            parsed = self._nlu.parse_assign(text)
            if parsed.task_title:
                payload["task_title"] = parsed.task_title
            if parsed.assignee_name:
                payload["assignee_name"] = parsed.assignee_name
            if parsed.deadline and not payload.get("due_at"):
                payload["due_at"] = parsed.deadline
            if parsed.priority and not payload.get("priority"):
                payload["priority"] = parsed.priority
            return self._handle_assign_start(payload, text, session_id, user_id, token)

        if action == "create_task":
            # Clean title từ LLM nếu chỉ là self-reference ("cho tôi", "mình", ...)
            if payload.get("title"):
                cleaned = _strip_self_ref(payload["title"].strip())
                payload["title"] = cleaned if len(cleaned) >= 2 else None
            if not payload.get("title"):
                extracted = _extract_title_from_text(text)
                if extracted:
                    payload["title"] = extracted

            if not payload.get("title"):
                state = ConversationState(
                    session_id=session_id, active_agent="task",
                    pending_action="create_task", payload=payload,
                    missing_fields=["title"], status="pending",
                )
                set_state(session_id, state)
                return {"success": False, "message": "Bạn muốn đặt tiêu đề cho task này là gì?"}

            result = self._execute_create(payload, token)
            return self._format_create_result(result, payload)

        result = self._execute_action(action, payload, text, token=token)
        return {"success": True, "message": "Đã xử lý xong.", "data": result}

    def continue_flow(
        self,
        text: str,
        session_id: str,
        token: Optional[str] = None,
        user_id: Optional[str] = None,
    ) -> dict[str, Any]:
        logger.info(f"TaskAgent: continue_flow session={session_id}")
        state = get_state(session_id)
        if not state:
            return {"success": False, "message": "Không có trạng thái pending.", "data": None}

        # ── Chờ tiêu đề task khi tạo ────────────────────────────────────────
        if state.pending_action == "create_task" and "title" in state.missing_fields:
            state.payload["title"] = text.strip()
            state.missing_fields.remove("title")
            if not state.missing_fields:
                result = self._execute_create(state.payload, token)
                clear_state(session_id)
                return self._format_create_result(result, state.payload)
            set_state(session_id, state)
            return {"success": False, "message": "Cần thêm thông tin."}

        # ── Chờ tiêu đề task khi giao ────────────────────────────────────────
        if state.pending_action == "assign_get_title" and "title" in state.missing_fields:
            task_title = text.strip()
            state.payload["task_title"] = task_title
            state.missing_fields.remove("title")
            clear_state(session_id)
            # Tìm user và giao
            return self._resolve_and_assign(
                task_title,
                state.payload.get("assignee_name", ""),
                session_id, user_id, token,
            )

        # ── Chọn người nhận khi có nhiều kết quả ────────────────────────────
        if state.pending_action == "select_assignee" and "choice" in state.missing_fields:
            candidates: list[dict] = state.payload.get("candidates", [])
            task_title: str        = state.payload.get("task_title", "")
            uid: Optional[str]     = state.payload.get("user_id") or user_id

            try:
                idx = int(text.strip()) - 1
            except ValueError:
                return {"success": False, "message": f"Vui lòng nhập số từ 1 đến {len(candidates)}."}

            if not (0 <= idx < len(candidates)):
                return {"success": False, "message": f"Số không hợp lệ. Nhập số từ 1 đến {len(candidates)}."}

            selected = candidates[idx]
            clear_state(session_id)
            return self._ask_for_optional_details(task_title, selected, session_id, uid, token)

        # ── Hỏi thêm thông tin optional trước khi tạo ───────────────────────
        if state.pending_action == "assign_confirm_details" and "details" in state.missing_fields:
            assignee  = state.payload.get("assignee", {})
            task_title = state.payload.get("task_title", "")
            uid        = state.payload.get("user_id") or user_id

            # Nếu user từ chối → tạo ngay không thêm thông tin
            if _is_no_response(text):
                clear_state(session_id)
                return self._finalize_assign(task_title, assignee, uid, token, extra={})

            # Dùng LLM parse optional fields từ câu trả lời
            extra = self._parse_optional_details(text, token)
            clear_state(session_id)
            return self._finalize_assign(task_title, assignee, uid, token, extra=extra)

        clear_state(session_id)
        return {"success": False, "message": "Flow đã hết hạn. Vui lòng thử lại.", "data": None}

    # ── Assign task flow ──────────────────────────────────────────────────────

    def _handle_assign_start(
        self,
        payload: dict,
        text: str,
        session_id: str,
        user_id: Optional[str],
        token: Optional[str],
    ) -> dict[str, Any]:
        assignee_name: str = (payload.get("assignee_name") or "").strip()
        task_title: str    = (payload.get("task_title") or "").strip()

        # LLM không extract được tên → thử regex từ text gốc
        if not assignee_name:
            assignee_name = _extract_assignee_from_text(text) or ""

        if not assignee_name:
            return {"success": False, "message": "Bạn muốn giao task cho ai? Vui lòng nhập tên đầy đủ của người nhận."}

        if not task_title:
            state = ConversationState(
                session_id=session_id, active_agent="task",
                pending_action="assign_get_title",
                payload={"assignee_name": assignee_name, "user_id": user_id},
                missing_fields=["title"], status="pending",
            )
            set_state(session_id, state)
            return {"success": False, "message": f"Bạn muốn giao task **gì** cho **{assignee_name}**?"}

        return self._resolve_and_assign(task_title, assignee_name, session_id, user_id, token)

    def _resolve_and_assign(
        self,
        task_title: str,
        assignee_name: str,
        session_id: str,
        user_id: Optional[str],
        token: Optional[str],
    ) -> dict[str, Any]:
        client = self._make_client(token)
        matches = client.find_users_by_name(assignee_name)

        if not matches:
            return {
                "success": False,
                "message": f"Không tìm thấy người dùng nào tên **{assignee_name}** trong hệ thống.",
            }

        if len(matches) == 1:
            return self._ask_for_optional_details(task_title, matches[0], session_id, user_id, token)

        # Nhiều kết quả → hỏi lại
        lines = [f"Tìm thấy **{len(matches)} người** tên '{assignee_name}'. Bạn muốn giao cho ai?\n"]
        for i, u in enumerate(matches, 1):
            email = u.get("email", "")
            roles = ", ".join(u.get("roles", [])) or "—"
            lines.append(f"  {i}. **{u.get('full_name','?')}** — {email} ({roles})")
        lines.append("\nNhập số thứ tự (1, 2, ...)")

        state = ConversationState(
            session_id=session_id, active_agent="task",
            pending_action="select_assignee",
            payload={"task_title": task_title, "candidates": matches, "user_id": user_id},
            missing_fields=["choice"], status="pending",
        )
        set_state(session_id, state)
        return {"success": False, "message": "\n".join(lines)}

    def _ask_for_optional_details(
        self,
        task_title: str,
        assignee: dict,
        session_id: str,
        user_id: Optional[str],
        token: Optional[str],
    ) -> dict[str, Any]:
        """Lưu state và hỏi 1 lần về thông tin optional."""
        name = assignee.get("full_name", "?")
        state = ConversationState(
            session_id=session_id, active_agent="task",
            pending_action="assign_confirm_details",
            payload={"task_title": task_title, "assignee": assignee, "user_id": user_id},
            missing_fields=["details"], status="pending",
        )
        set_state(session_id, state)
        return {
            "success": False,
            "message": (
                f"📋 Giao task **{task_title}** cho **{name}**.\n\n"
                "Bạn có muốn thêm thông tin không? Ví dụ:\n"
                "  • **Thời hạn** — deadline 15/5, hạn thứ 6\n"
                "  • **Mô tả** — mô tả [nội dung]\n"
                "  • **Mục tiêu** — mục tiêu [nội dung]\n"
                "  • **Cách thực hiện** — cách [hướng dẫn]\n"
                "  • **Người review** — reviewer [tên]\n"
                "  • **Người hỗ trợ** — hỗ trợ [tên]\n"
                "  • **Độ ưu tiên** — ưu tiên cao / urgent\n\n"
                "Nhập thông tin hoặc **không** để tạo ngay."
            ),
        }

    def _parse_optional_details(self, text: str, token: Optional[str]) -> dict:
        """
        Rule-based parser: strip diacritics → match ASCII keywords → slice original.

        Key insight: strip_tones(NFC_text) has SAME LENGTH as NFC text because each
        precomposed NFC char decomposes to base + combining marks in NFD, and after
        removing combining marks we get exactly 1 base char per NFC char.
        → Positions in stripped text map 1:1 to original → original Vietnamese names preserved.
        """
        import re as _re
        import unicodedata as _uc
        from app.agents.task_agent.nlu_parser import DeadlineParser, PriorityParser

        result: dict = {}
        text = _uc.normalize("NFC", text)

        def strip_tones(s: str) -> str:
            return "".join(
                c for c in _uc.normalize("NFD", s)
                if _uc.category(c) != "Mn"
            ).lower()

        t = strip_tones(text)   # tone-free lowercase, len == len(text)

        STOP = (
            r"(?=\s*[,;]\s*"
            r"|\s*\.\s+"
            r"|\s+(?:mo\s*ta|muc\s*tieu|cach"
            r"|nguoi\s*review|reviewer|review"
            r"|nguoi?\s*ho\s*tro|ho\s*tro|supporter"
            r"|deadline|han\s*chot?|han|thoi\s*han|truoc"
            r"|uu\s*tien|priority)"
            r"|\s*$)"
        )

        def find(kw: str) -> Optional[str]:
            """Match ASCII kw in stripped text; extract original chars by position."""
            m = _re.search(kw + r"\s+(?P<v>.+?)" + STOP, t, _re.IGNORECASE)
            if not m:
                return None
            val = text[m.start("v"):m.end("v")].strip().rstrip(".,; ")
            return val if len(val) >= 2 else None

        # ── Deadline ──────────────────────────────────────────────────────────
        dl = find(r"(?:deadline|han\s*chot?|han|thoi\s*han|truoc\s*ngay|truoc)")
        if dl:
            hm = _re.match(r"(\d{1,2})\s*h\s*(.+)", dl.strip(), _re.IGNORECASE)
            if hm:
                hour = int(hm.group(1))
                d = DeadlineParser.parse(hm.group(2).strip())
                if d:
                    result["due_at"] = f"{d}T{hour:02d}:00:00"
            else:
                d = DeadlineParser.parse(dl)
                if d:
                    result["due_at"] = d

        # ── Priority ──────────────────────────────────────────────────────────
        pri = find(r"(?:uu\s*tien|priority)")
        if pri:
            result["priority"] = PriorityParser.parse(strip_tones(pri))
        elif _re.search(r"\b(?:urgent|khan\s*cap|gap\s*len)\b", t):
            result["priority"] = "urgent"
        elif _re.search(r"\bgap\b", t):
            result["priority"] = "high"

        # ── Description ───────────────────────────────────────────────────────
        desc = find(r"mo\s*ta")
        if desc:
            result["description"] = desc

        # ── Goal ──────────────────────────────────────────────────────────────
        goal = find(r"muc\s*tieu")
        if goal:
            result["goal"] = goal

        # ── How to do ─────────────────────────────────────────────────────────
        htd = find(r"cach\s+(?:thuc\s*hien\s+)?")
        if htd:
            result["how_to_do"] = htd

        # ── Reviewer ──────────────────────────────────────────────────────────
        rev = find(r"(?:nguoi\s*review|reviewer|review)")
        if rev:
            result["reviewer_name"] = rev.strip()

        # ── Supporters ────────────────────────────────────────────────────────
        sup = find(r"(?:nguoi?\s*ho\s*tro|ho\s*tro|supporter)")
        if sup:
            parts = _re.split(r"[,;/]|\bvà\b|\bva\b|\band\b", sup,
                              flags=_re.IGNORECASE | _re.UNICODE)
            names = [p.strip().rstrip(".,; ") for p in parts if len(p.strip()) >= 2]
            if names:
                result["supporter_names"] = names

        logger.info(f"_parse_optional_details: {result}")
        return result

    def _finalize_assign(
        self,
        task_title: str,
        assignee: dict,
        assigner_user_id: Optional[str],
        token: Optional[str],
        extra: dict,
    ) -> dict[str, Any]:
        """Tạo task + giao + reviewer + supporters với đầy đủ thông tin."""
        task_api  = self._make_task_api(token)
        client    = self._make_client(token)

        # ── Resolve reviewer ────────────────────────────────────────────────
        reviewer_id = None
        reviewer_name = extra.get("reviewer_name")
        if reviewer_name:
            matches = client.find_users_by_name(reviewer_name)
            if matches:
                reviewer_id = matches[0].get("id")

        # ── Tạo task ────────────────────────────────────────────────────────
        task_payload: dict = {
            "title":               task_title,
            "task_type":           "assignable",
            "priority":            _safe_priority(extra.get("priority")),
            "assigned_by_user_id": assigner_user_id,
            "visibility_scope":    "shared",
        }
        if extra.get("due_at"):
            task_payload["due_at"] = extra["due_at"]
        if extra.get("description"):
            task_payload["description"] = extra["description"]
        if extra.get("goal"):
            task_payload["goal"] = extra["goal"]
        if reviewer_id:
            task_payload["reviewer_user_id"] = reviewer_id

        created = task_api.create_task(task_payload)
        if not created or "error" in created:
            return {"success": False, "message": f"Không tạo được task: {(created or {}).get('error','lỗi không xác định')}"}

        task_id = str(created.get("id", ""))
        if not task_id:
            return {"success": False, "message": "Tạo task thất bại — không nhận được task ID."}

        # ── Giao task (kèm Assignment Brief đầy đủ) ────────────────────────
        assign_payload: dict = {"assignee_user_id": assignee["id"]}
        # description → assignment_note  (Note: why this person / what to do)
        if extra.get("description"):
            assign_payload["assignment_note"] = extra["description"]
        # how_to_do → assignment_how_to_do  (Cách làm)
        if extra.get("how_to_do"):
            assign_payload["assignment_how_to_do"] = extra["how_to_do"]
        # goal → assignment_expected_result  (Kết quả mong muốn)
        if extra.get("goal"):
            assign_payload["assignment_expected_result"] = extra["goal"]

        assignment = task_api.assign_task(task_id, assign_payload)
        if assignment and "error" in assignment:
            return {"success": False, "message": f"Tạo task OK nhưng giao thất bại: {assignment['error']}"}

        # ── Thêm supporter ──────────────────────────────────────────────────
        supporter_names: list = extra.get("supporter_names") or []
        added_supporters = []
        for sname in supporter_names:
            sm = client.find_users_by_name(sname)
            if sm:
                r = task_api.add_supporter(task_id, sm[0]["id"])
                if not (r and "error" in r):
                    added_supporters.append(sm[0].get("full_name", sname))

        # ── Tổng hợp kết quả ────────────────────────────────────────────────
        name = assignee.get("full_name", "?")
        lines = [f"✅ Đã tạo và giao task **{task_title}** cho **{name}**."]
        if extra.get("due_at"):
            lines.append(f"  📅 Hạn: {extra['due_at'][:10]}")
        if extra.get("description"):
            lines.append(f"  📝 Mô tả: {extra['description']}")
        if extra.get("goal"):
            lines.append(f"  🎯 Mục tiêu: {extra['goal']}")
        if extra.get("how_to_do"):
            lines.append(f"  🔧 Cách thực hiện: {extra['how_to_do']}")
        if reviewer_id:
            lines.append(f"  👁 Reviewer: {reviewer_name}")
        if added_supporters:
            lines.append(f"  🤝 Hỗ trợ: {', '.join(added_supporters)}")
        if extra.get("priority") and extra["priority"] != "low":
            lines.append(f"  🔴 Ưu tiên: {extra['priority']}")

        return {"success": True, "message": "\n".join(lines), "data": created}

    # ── List tasks ────────────────────────────────────────────────────────────

    def _handle_list_my_tasks(self, token: Optional[str]) -> dict[str, Any]:
        try:
            result = self._make_task_api(token).list_my_personal_tasks()
            tasks = result if isinstance(result, list) else []
            if not tasks:
                return {"success": True, "message": "Bạn không có task cá nhân nào chưa xong.", "data": None}
            lines = [f"Bạn có **{len(tasks)} task cá nhân**:\n"]
            for i, t in enumerate(tasks[:8], 1):
                due = f" · Hạn: {t['due_at'][:10]}" if t.get("due_at") else ""
                lines.append(f"  {i}. {t.get('title','—')} [{t.get('status','?')}]{due}")
            if len(tasks) > 8:
                lines.append(f"  ... và {len(tasks) - 8} task khác.")
            return {"success": True, "message": "\n".join(lines), "data": None}
        except Exception as e:
            logger.error(f"list_my_tasks error: {e}")
            return {"success": False, "message": "Không thể lấy danh sách. Vào trang 'Việc của tôi' để xem.", "data": None}

    def _handle_list_assigned_tasks(self, token: Optional[str]) -> dict[str, Any]:
        try:
            result = self._make_task_api(token).list_assigned_to_me()
            tasks = result if isinstance(result, list) else []
            if not tasks:
                return {"success": True, "message": "Bạn không có task nào được giao.", "data": None}
            lines = [f"Bạn có **{len(tasks)} task** đang được giao:\n"]
            for i, t in enumerate(tasks[:8], 1):
                due = f" · Hạn: {t['due_at'][:10]}" if t.get("due_at") else ""
                lines.append(f"  {i}. {t.get('title','—')} [{t.get('status','?')}]{due}")
            if len(tasks) > 8:
                lines.append(f"  ... và {len(tasks) - 8} task khác.")
            return {"success": True, "message": "\n".join(lines), "data": None}
        except Exception as e:
            logger.error(f"list_assigned error: {e}")
            return {"success": False, "message": "Không thể lấy danh sách. Vào trang Task để xem.", "data": None}

    # ── Create helpers ────────────────────────────────────────────────────────

    def _execute_create(self, payload: dict, token: Optional[str]) -> Optional[dict]:
        task_api = self._make_task_api(token)
        title = payload.get("title", "")
        clean = {k: v for k, v in payload.items() if v is not None}
        clean["title"]     = title
        clean["task_type"] = clean.get("task_type", "personal")
        clean["priority"]  = _safe_priority(clean.get("priority"))
        return task_api.create_task(clean)

    def _format_create_result(self, result: Optional[dict], payload: dict) -> dict[str, Any]:
        if not result or "error" in (result or {}):
            err = (result or {}).get("error", "lỗi không xác định")
            return {"success": False, "message": f"Không tạo được task: {err}", "data": None}
        title   = payload.get("title", "")
        due_str = f" · Hạn: {payload['due_at'][:10]}" if payload.get("due_at") else ""
        pri     = _safe_priority(payload.get("priority"))
        return {
            "success": True,
            "message": f"✅ Đã tạo task: **{title}**{due_str} · Ưu tiên: {pri}",
            "data": result,
        }

    # ── Fallback & parse ──────────────────────────────────────────────────────

    def _fallback_action_from_text(self, text: str, action: str) -> str:
        if action != "unknown":
            return action
        lower = text.lower()
        if any(k in lower for k in _CREATE_KEYWORDS):
            return "create_task"
        if any(k in lower for k in _LIST_ASSIGNED_KEYWORDS):
            return "list_assigned_tasks"
        if any(k in lower for k in _LIST_PERSONAL_KEYWORDS):
            return "list_my_tasks"
        if any(k in lower for k in _ASSIGN_KEYWORDS):
            return "assign_task"
        return action

    def _parse_with_llm(self, text: str) -> dict:
        current_date = date.today().strftime("%Y-%m-%d")
        system = TASK_ACTION_SYSTEM.format(current_date=current_date)
        result = self._runner.run_json(
            system_prompt=system,
            user_prompt=TASK_ACTION_USER.format(text=text),
        )
        return result if isinstance(result, dict) else {}

    def _normalize_llm_output(self, data: dict) -> tuple[str, dict]:
        if not isinstance(data, dict):
            return "unknown", {}
        action  = data.get("action") or "unknown"
        payload = data.get("payload") or {}
        if not isinstance(payload, dict):
            payload = {}
        return action, payload

    def _execute_action(self, action: str, payload: dict, text: str, token: Optional[str] = None) -> dict | None:
        task_api = self._make_task_api(token)
        if action == "create_task":
            return self._execute_create(payload, token)
        if action == "update_task":
            return task_api.update_task(payload.get("task_id", ""), payload)
        if action == "delete_task":
            return task_api.delete_task(payload.get("task_id", ""))
        if action == "list_tasks":
            return task_api.list_my_personal_tasks()
        logger.warning(f"TaskAgent: unhandled action={action}")
        return None
