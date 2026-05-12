from openai import OpenAI
from app.core.config import settings
from app.core.logging import logger


class OpenAIClient:
    """Thin wrapper around the OpenAI client with safe initialization and error handling."""

    def __init__(self):
        self.model = settings.openai_model
        if not settings.openai_api_key:
            logger.warning("OPENAI_API_KEY not set — OpenAIClient unavailable")
            self._client = None
        else:
            self._client = OpenAI(api_key=settings.openai_api_key)
            logger.info(f"OpenAIClient ready (model={self.model})")

    def is_available(self) -> bool:
        return self._client is not None

    def chat(
        self,
        system_prompt: str,
        user_prompt: str,
        temperature: float = 0.2,
    ) -> str:
        """
        Send a chat completion request and return the response text.

        Raises RuntimeError if client is unavailable or the API call fails.
        """
        if self._client is None:
            raise RuntimeError("OpenAI client is not available. Missing OPENAI_API_KEY.")

        logger.debug(f"LLM chat: model={self.model}, temperature={temperature}")
        try:
            response = self._client.chat.completions.create(
                model=self.model,
                temperature=temperature,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
            )
            content = response.choices[0].message.content or ""
            logger.debug(f"LLM response: {len(content)} chars")
            return content
        except Exception as exc:
            raise RuntimeError(f"OpenAI API call failed: {exc}") from exc
