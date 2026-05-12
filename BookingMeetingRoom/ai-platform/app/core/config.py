from pydantic_settings import BaseSettings, SettingsConfigDict
from functools import lru_cache


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # App
    app_name: str = "AI Platform"
    app_version: str = "0.1.0"
    debug: bool = False

    # Server
    host: str = "0.0.0.0"
    port: int = 8001

    # OpenAI
    openai_api_key: str = ""
    openai_model: str = "gpt-4o-mini"

    # STT
    stt_provider: str = "local"
    stt_model: str = "base"
    stt_language: str = "vi"
    stt_device: str = "cpu"

    # Storage
    storage_base_dir: str = "data"
    raw_audio_dir: str = "data/raw"
    processed_audio_dir: str = "data/processed"
    outputs_dir: str = "data/outputs"

    # Diarization
    hf_token: str = ""  # HuggingFace token for pyannote.audio
    diarization_model: str = "pyannote/speaker-diarization-3.1"

    # Job queue
    max_concurrent_jobs: int = 2
    job_timeout_seconds: int = 600


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
