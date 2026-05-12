from app.agents.meeting_agent.prompts import EXTRACT_TASK_SYSTEM
from app.core.logging import logger


class TaskExtractor:
    """Extract action items from a meeting transcript via LLM."""

    def __init__(self, runner):
        self._runner = runner

    def extract(self, transcript: str) -> list[dict]:
        if not transcript or len(transcript.strip()) < 20:
            logger.warning("TaskExtractor: transcript too short — skip extraction")
            return []

        logger.info(f"TaskExtractor: extracting tasks from transcript (len={len(transcript)})")
        result = self._runner.run_json(
            system_prompt=EXTRACT_TASK_SYSTEM,
            user_prompt=f"Transcript:\n{transcript}",
        )

        if not isinstance(result, list):
            logger.warning(f"TaskExtractor: invalid output type={type(result).__name__}")
            return []

        for item in result:
            if isinstance(item, dict):
                try:
                    conf = float(item.get("ai_confidence", 0.5))
                except (TypeError, ValueError):
                    conf = 0.5
                item["ai_confidence"] = max(0.0, min(1.0, conf))

        logger.info(f"TaskExtractor: extracted {len(result)} task(s)")
        return result
