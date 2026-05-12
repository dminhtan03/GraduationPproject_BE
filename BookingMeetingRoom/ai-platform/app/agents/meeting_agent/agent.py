from typing import Any, Optional
from app.services.backend_api.client import BackendAPIClient
from app.services.backend_api.meeting_api import MeetingAPI
from app.services.llm.prompt_runner import PromptRunner
from app.pipelines.meeting_pipeline import MeetingPipeline
from app.core.logging import logger
from app.agents.meeting_agent.prompts import MEETING_ACTION_SYSTEM, MEETING_ACTION_USER
from app.agents.meeting_agent import meeting_actions, recording_actions
from app.agents.meeting_agent.task_extractor import TaskExtractor
from app.agents.meeting_agent.review_builder import ReviewBuilder

_CREATE_KEYWORDS = ["tạo meeting", "tạo cuộc họp", "thêm meeting", "create meeting"]
_LIST_KEYWORDS = ["danh sách", "xem meeting", "list meeting"]
_UPLOAD_KEYWORDS = ["upload", "tải lên", "gửi recording"]
_PROCESS_KEYWORDS = ["xử lý", "phân tích", "process", "transcript"]


class MeetingAgent:
    def __init__(self):
        self._runner = PromptRunner()
        self._pipeline = MeetingPipeline()
        self._extractor = TaskExtractor(self._runner)
        self._review_builder = ReviewBuilder()

    def _make_meeting_api(self, token: Optional[str] = None) -> MeetingAPI:
        return MeetingAPI(BackendAPIClient(access_token=token))

    def handle(self, text: str, token: Optional[str] = None) -> dict[str, Any]:
        logger.info(f"MeetingAgent: handle text={repr(text)}")

        llm_output = self._parse(text)
        action, payload = self._normalize(llm_output)
        action = self._fallback_action_from_text(text, action)
        logger.info(f"MeetingAgent: action={action}")

        api = self._make_meeting_api(token)

        if action == "create_meeting":
            result = meeting_actions.create_meeting(api, payload)
        elif action == "list_meetings":
            result = meeting_actions.list_meetings(api)
        elif action == "upload_recording":
            result = recording_actions.upload_recording(
                api,
                payload.get("meeting_id", ""),
                payload.get("file_path", ""),
            )
        elif action == "process_recording":
            result = recording_actions.process_recording(
                self._pipeline,
                payload.get("file_path", ""),
            )
            if hasattr(result, "model_dump"):
                result = result.model_dump(mode="json")
        else:
            return {"success": False, "message": "Không hiểu yêu cầu meeting.", "data": None}

        return {"success": True, "message": action, "data": result}

    def process(self, audio_path: str) -> dict[str, Any]:
        logger.info(f"MeetingAgent: processing audio={audio_path}")
        try:
            pipeline_result = self._pipeline.run(audio_path)
            transcript_obj = getattr(pipeline_result, "transcript", None)
            if isinstance(transcript_obj, str):
                transcript = transcript_obj
            elif hasattr(transcript_obj, "full_text"):
                transcript = transcript_obj.full_text or ""
            else:
                transcript = ""

            tasks = self._extractor.extract(transcript)
            review = self._review_builder.build(tasks)

            pipeline_data = pipeline_result.model_dump(mode="json") if hasattr(pipeline_result, "model_dump") else pipeline_result

            return {
                "pipeline": pipeline_data,
                "extracted_items": tasks,
                "review_required": True,
                "review_summary": review["summary"],
                "review_items": review["items"],
            }
        except Exception as exc:
            logger.error(f"MeetingAgent failed: {exc}")
            raise RuntimeError(f"MeetingAgent failed: {exc}") from exc

    def _parse(self, text: str) -> dict:
        out = self._runner.run_json(
            system_prompt=MEETING_ACTION_SYSTEM,
            user_prompt=MEETING_ACTION_USER.format(text=text),
        )
        return out if isinstance(out, dict) else {}

    def _normalize(self, data: dict) -> tuple[str, dict]:
        if not isinstance(data, dict):
            return "unknown", {}
        action = data.get("action") or "unknown"
        payload = data.get("payload") or {}
        if not isinstance(payload, dict):
            payload = {}
        return action, payload

    def _fallback_action_from_text(self, text: str, action: str) -> str:
        if action != "unknown":
            return action
        lower = text.lower()
        if any(k in lower for k in _CREATE_KEYWORDS):
            return "create_meeting"
        if any(k in lower for k in _LIST_KEYWORDS):
            return "list_meetings"
        if any(k in lower for k in _UPLOAD_KEYWORDS):
            return "upload_recording"
        if any(k in lower for k in _PROCESS_KEYWORDS):
            return "process_recording"
        return action
