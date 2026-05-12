class AIPlatformError(Exception):
    """Base exception for all AI Platform errors."""

    def __init__(self, message: str, code: str = "INTERNAL_ERROR"):
        self.message = message
        self.code = code
        super().__init__(message)


class AudioProcessingError(AIPlatformError):
    def __init__(self, message: str):
        super().__init__(message, code="AUDIO_PROCESSING_ERROR")


class STTError(AIPlatformError):
    def __init__(self, message: str):
        super().__init__(message, code="STT_ERROR")


class DiarizationError(AIPlatformError):
    def __init__(self, message: str):
        super().__init__(message, code="DIARIZATION_ERROR")


class LLMError(AIPlatformError):
    def __init__(self, message: str):
        super().__init__(message, code="LLM_ERROR")


class JobNotFoundError(AIPlatformError):
    def __init__(self, job_id: str):
        super().__init__(f"Job '{job_id}' not found", code="JOB_NOT_FOUND")


class StorageError(AIPlatformError):
    def __init__(self, message: str):
        super().__init__(message, code="STORAGE_ERROR")
