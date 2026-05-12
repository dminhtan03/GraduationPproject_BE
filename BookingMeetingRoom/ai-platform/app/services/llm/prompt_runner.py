import json
from app.services.llm.openai_client import OpenAIClient
from app.core.logging import logger


class PromptRunner:
    """Runs LLM prompts safely, returning None on any failure instead of raising."""

    def __init__(self):
        self._client = OpenAIClient()

    def run_text(
        self,
        system_prompt: str,
        user_prompt: str,
        temperature: float = 0.2,
    ) -> str | None:
        if not self._client.is_available():
            return None
        logger.debug("Running LLM text prompt")
        try:
            return self._client.chat(system_prompt, user_prompt, temperature)
        except Exception as exc:
            logger.warning(f"LLM text prompt failed: {exc}")
            return None

    def run_json(
        self,
        system_prompt: str,
        user_prompt: str,
        temperature: float = 0.2,
    ) -> dict | None:
        if not self._client.is_available():
            return None
        logger.debug("Running LLM JSON prompt")
        try:
            response_text = self._client.chat(system_prompt, user_prompt, temperature)
        except Exception as exc:
            logger.warning(f"LLM JSON prompt failed: {exc}")
            return None
        try:
            clean = response_text.strip()
            start = clean.find("{")
            end = clean.rfind("}")
            if start != -1 and end != -1 and end > start:
                clean = clean[start:end + 1]
            if clean.startswith("```"):
                clean = clean.split("\n", 1)[-1].rsplit("```", 1)[0].strip()
            return json.loads(clean)
        except (json.JSONDecodeError, ValueError):
            logger.warning("JSON parse failed — LLM did not return valid JSON")
            return None
