from pathlib import Path
from app.core.config import settings
from app.core.logging import logger
from app.schemas.transcript import RawTranscript, TranscriptSegment


class BaseSTT:
    """Abstract base for all STT providers."""

    def transcribe(self, audio_path: str) -> RawTranscript:
        raise NotImplementedError


class WhisperSTT(BaseSTT):
    """
    STT via faster-whisper (local inference).
    Falls back to a stub response if the package is not installed.
    """

    def __init__(self):
        self._model = None
        self.model_size = settings.stt_model
        self.language = settings.stt_language
        self.device = settings.stt_device
        logger.info(f"WhisperSTT ready (model={self.model_size}, device={self.device})")
        self._load_model()

    def _load_model(self) -> None:
        try:
            from faster_whisper import WhisperModel
            self._model = WhisperModel(self.model_size, device=self.device, compute_type="int8")
            logger.info("faster-whisper model loaded")
        except ImportError:
            logger.warning("faster-whisper not installed — WhisperSTT will return stub output")
        except Exception as exc:
            raise RuntimeError(f"Failed to load faster-whisper model: {exc}") from exc

    def transcribe(self, audio_path: str) -> RawTranscript:
        if not Path(audio_path).exists():
            logger.warning(f"Audio file not found: {audio_path} — returning stub transcript")
            return self._stub_transcript()

        if self._model is None:
            logger.warning("faster-whisper model unavailable — returning stub transcript")
            return self._stub_transcript()

        logger.info(f"WhisperSTT transcribing: {audio_path}")
        try:
            segments_iter, info = self._model.transcribe(
                audio_path,
                language=self.language,
                word_timestamps=True,
            )
            segments: list[TranscriptSegment] = []
            for seg in segments_iter:
                segments.append(TranscriptSegment(
                    speaker="UNKNOWN",
                    start=round(seg.start, 3),
                    end=round(seg.end, 3),
                    text=seg.text.strip(),
                    stt_confidence=round(seg.avg_logprob, 4) if hasattr(seg, "avg_logprob") else None,
                ))
            logger.info(f"WhisperSTT produced {len(segments)} segments")
            return RawTranscript(
                segments=segments,
                language=info.language,
                duration_seconds=round(info.duration, 3),
            )
        except Exception as exc:
            raise RuntimeError(f"faster-whisper transcription failed: {exc}") from exc

    def _stub_transcript(self) -> RawTranscript:
        return RawTranscript(
            segments=[TranscriptSegment(speaker="UNKNOWN", start=0.0, end=5.0, text="[STT stub output]")],
            language=self.language,
            duration_seconds=5.0,
        )


class OpenAISTT(BaseSTT):
    """
    STT via OpenAI Whisper API (gpt-4o-transcribe).
    Falls back to a stub response if the API key is not configured.
    """

    MODEL = "gpt-4o-transcribe"

    def __init__(self):
        self._client = None
        self.language = settings.stt_language
        logger.info(f"OpenAISTT ready (model={self.MODEL})")
        self._init_client()

    def _init_client(self) -> None:
        if not settings.openai_api_key:
            logger.warning("OPENAI_API_KEY not set — OpenAISTT will return stub output")
            return
        try:
            from openai import OpenAI
            self._client = OpenAI(api_key=settings.openai_api_key)
        except ImportError:
            logger.warning("openai package not installed — OpenAISTT will return stub output")
        except Exception as exc:
            raise RuntimeError(f"Failed to init OpenAI client: {exc}") from exc

    def transcribe(self, audio_path: str) -> RawTranscript:
        if not Path(audio_path).exists():
            logger.warning(f"Audio file not found: {audio_path} — returning stub transcript")
            return self._stub_transcript()

        if self._client is None:
            logger.warning("OpenAI client unavailable — returning stub transcript")
            return self._stub_transcript()

        logger.info(f"OpenAISTT transcribing: {audio_path} via {self.MODEL}")
        try:
            with open(audio_path, "rb") as f:
                response = self._client.audio.transcriptions.create(
                    model=self.MODEL,
                    file=f,
                    language=self.language,
                    response_format="verbose_json",
                    timestamp_granularities=["segment"],
                )
            segments: list[TranscriptSegment] = []
            for seg in response.segments or []:
                segments.append(TranscriptSegment(
                    speaker="UNKNOWN",
                    start=round(float(seg.get("start", 0.0)), 3),
                    end=round(float(seg.get("end", 0.0)), 3),
                    text=seg.get("text", "").strip(),
                ))
            logger.info(f"OpenAISTT produced {len(segments)} segments")
            duration = float(getattr(response, "duration", 0.0))
            return RawTranscript(
                segments=segments,
                language=getattr(response, "language", self.language),
                duration_seconds=round(duration, 3),
            )
        except Exception as exc:
            raise RuntimeError(f"OpenAI STT API call failed: {exc}") from exc

    def _stub_transcript(self) -> RawTranscript:
        return RawTranscript(
            segments=[TranscriptSegment(speaker="UNKNOWN", start=0.0, end=5.0, text="[OpenAI STT stub output]")],
            language=self.language,
            duration_seconds=5.0,
        )


def get_stt_provider(provider: str = "local") -> BaseSTT:
    """
    Factory: return the correct STT backend by name.
      "local"  → WhisperSTT (faster-whisper, runs on CPU/GPU)
      "openai" → OpenAISTT  (gpt-4o-transcribe via API)
    """
    logger.info(f"STT provider requested: {provider}")
    if provider == "local":
        return WhisperSTT()
    if provider == "openai":
        return OpenAISTT()
    raise ValueError(f"Unknown STT provider: '{provider}'. Choose 'local' or 'openai'.")
