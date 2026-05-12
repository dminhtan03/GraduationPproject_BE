from datetime import date
from typing import Any, Optional
from app.agents.intent_agent.agent import IntentAgent
from app.agents.task_agent.agent import TaskAgent
from app.agents.meeting_agent.agent import MeetingAgent
from app.agents.shared.conversation_state import get_state, clear_state
from app.agents.shared.break_detector import is_break
from app.services.backend_api.client import BackendAPIClient
from app.services.backend_api.task_api import TaskAPI
from app.services.llm.prompt_runner import PromptRunner
from app.core.logging import logger

_SYSTEM_PROMPT = (
    "Bạn là trợ lý AI của hệ thống quản lý công việc LeanMAC. "
    "Bạn CHỈ hỗ trợ các chủ đề liên quan đến công việc, tổ chức, task, cuộc họp, "
    "tiến độ, nhân sự, và hoạt động nội bộ doanh nghiệp. "
    "Nếu người dùng hỏi bất cứ điều gì KHÔNG liên quan đến công việc hoặc tổ chức "
    "(toán học thuần túy, kiến thức phổ thông, giải trí, v.v.), "
    "hãy từ chối lịch sự và nhắc lại phạm vi hỗ trợ. "
    "Trả lời ngắn gọn bằng tiếng Việt."
)

_OFF_TOPIC_REPLY = (
    "Tôi chỉ hỗ trợ các vấn đề liên quan đến công việc và tổ chức. "
    "Bạn có thể hỏi tôi về task, cuộc họp, tiến độ công việc, hoặc nhân sự."
)


class OrchestratorAgent:
    def __init__(self):
        self._intent  = IntentAgent()
        self._task    = TaskAgent()
        self._meeting = MeetingAgent()
        self._runner  = PromptRunner()

    def run(
        self,
        text: str,
        audio_path: Optional[str] = None,
        user_id: Optional[str] = None,
        organization_id: Optional[str] = None,
        session_id: Optional[str] = None,
        token: Optional[str] = None,
    ) -> dict[str, Any]:
        session_id = session_id or "default"

        # ── Kiểm tra pending conversation ────────────────────────────────────
        state = get_state(session_id)
        if state and state.status == "pending":
            if is_break(text):
                clear_state(session_id)
                return {"reply": "Đã hủy thao tác.", "intent": "cancelled"}

            if state.active_agent == "task":
                result = self._task.continue_flow(text, session_id, token=token, user_id=user_id)
                return {"reply": result.get("message", "Đã xử lý."), "intent": "task", "data": result.get("data")}

            clear_state(session_id)
            return {"reply": "Đã hủy do không hỗ trợ.", "intent": "error"}

        # ── Phân loại intent ─────────────────────────────────────────────────
        intent = self._intent.classify(text)
        logger.info(f"OrchestratorAgent: intent={intent}")

        if intent == "meeting":
            if not audio_path:
                return {
                    "reply": "Để xử lý cuộc họp, bạn cần upload file audio qua trang Meetings.",
                    "intent": "meeting",
                }
            try:
                result = self._meeting.process(audio_path)
                return {"reply": "Đã xử lý xong cuộc họp.", "intent": "meeting", "data": result}
            except Exception as e:
                return {"reply": f"Lỗi xử lý cuộc họp: {e}", "intent": "error"}

        if intent == "task":
            result = self._task.start_flow(text, session_id, token=token, user_id=user_id)
            return {"reply": result.get("message", "Đã xử lý."), "intent": "task", "data": result.get("data")}

        if intent == "summary":
            return self._handle_summary(token)

        if intent == "off_topic":
            return {"reply": _OFF_TOPIC_REPLY, "intent": "off_topic"}

        # ── Fallback: câu hỏi công việc không khớp keyword → LLM với strict prompt ──
        response = self._runner.run_text(system_prompt=_SYSTEM_PROMPT, user_prompt=text)
        return {
            "reply": response or _OFF_TOPIC_REPLY,
            "intent": "general",
        }

    def _handle_summary(self, token: Optional[str]) -> dict[str, Any]:
        try:
            task_api = TaskAPI(BackendAPIClient(access_token=token))
            data = task_api.get_today_summary()
        except Exception as e:
            logger.error(f"OrchestratorAgent summary error: {e}")
            return {"reply": "Không thể lấy tóm tắt ngày hôm nay. Vui lòng thử lại.", "intent": "summary"}

        if "error" in data:
            return {"reply": f"Lỗi: {data['error']}", "intent": "summary"}

        today_str = date.today().strftime("%d/%m/%Y")
        lines = [f"📅 **Tóm tắt ngày {today_str}**\n"]

        my_tasks = data.get("my_tasks", [])
        assigned = data.get("assigned_to_me", [])
        pending  = data.get("pending_acceptance", [])
        meetings = data.get("meetings_today", [])
        meet_pnd = data.get("meetings_pending", [])

        if my_tasks:
            lines.append(f"✅ **Việc cá nhân chưa xong ({len(my_tasks)}):**")
            for t in my_tasks:
                due = f" — hạn {t['due_at'][:10]}" if t.get("due_at") else " — chưa có hạn"
                lines.append(f"  • {t['title']} [{t.get('status','?')}]{due}")
        else:
            lines.append("✅ Không có việc cá nhân nào chưa xong.")

        lines.append("")

        if assigned:
            lines.append(f"📋 **Việc được giao cho tôi ({len(assigned)}):**")
            for t in assigned:
                due = f" — hạn {t['due_at'][:10]}" if t.get("due_at") else " — chưa có hạn"
                lines.append(f"  • {t['title']} [{t.get('status','?')}]{due}")
        else:
            lines.append("📋 Không có việc nào được giao chưa xong.")

        lines.append("")

        if pending:
            lines.append(f"⏳ **Chờ tôi chấp nhận ({len(pending)}):**")
            for t in pending:
                assigner = f" (từ {t['assigner_name']})" if t.get("assigner_name") else ""
                lines.append(f"  • {t['title']}{assigner}")
        else:
            lines.append("⏳ Không có việc nào chờ chấp nhận.")

        lines.append("")

        if meetings:
            lines.append(f"🗓 **Meeting hôm nay ({len(meetings)}):**")
            for m in meetings:
                start = m.get("scheduled_start", "")[:16].replace("T", " ") if m.get("scheduled_start") else "?"
                status = " ⚠️ chưa xác nhận" if m.get("response_status") == "invited" else ""
                lines.append(f"  • {m['title']} lúc {start}{status}")
        else:
            lines.append("🗓 Không có meeting hôm nay.")

        if meet_pnd:
            lines.append(f"\n⚠️ **{len(meet_pnd)} meeting chưa xác nhận** — vào trang Meetings để phản hồi.")

        return {"reply": "\n".join(lines), "intent": "summary"}
