import uuid
from datetime import datetime

from app.core.logging import logger
from app.core.exceptions import AIPlatformError
from app.schemas.meeting import MeetingOutput
from app.services.audio.preprocess import AudioPreprocessor
from app.services.speech.stt import WhisperSTT
from app.services.speech.diarization import SpeakerDiarizer
from app.services.speech.aligner import TranscriptAligner
from app.services.nlp.transcript_filter import TranscriptFilter
from app.services.nlp.transcript_cleaner import TranscriptCleaner
from app.services.nlp.task_extractor_chunked import ChunkedTaskExtractor
from app.services.nlp.minutes_generator import MinutesGenerator
from app.services.nlp.action_extractor import ActionItemExtractor


class MeetingPipeline:
    """
    End-to-end meeting audio processing pipeline.

    Stages:
        1. Audio preprocessing  → normalized WAV
        2. STT (Whisper)        → RawTranscript with timestamps
        3. Diarization          → speaker segments
        4. Alignment            → CleanedTranscript with speaker labels
        5. Filter (rule-based)  → removes small-talk / off-topic (NO LLM)
        6. Transcript cleaning  → LLM batch-clean (8 segments per call)
        7. Minutes generation   → MeetingMinutes
        8. Action extraction    → list[ActionItem] (chunked, token-safe)
    """

    def __init__(self):
        self._preprocessor    = AudioPreprocessor()
        self._stt             = WhisperSTT()
        self._diarizer        = SpeakerDiarizer()
        self._aligner         = TranscriptAligner()
        self._filter          = TranscriptFilter()          # NEW — stage 5
        self._cleaner         = TranscriptCleaner()
        self._task_extractor  = ChunkedTaskExtractor()      # NEW — replaces naive extractor
        self._minutes_gen     = MinutesGenerator()
        self._action_extractor = ActionItemExtractor()

    def run(
        self,
        audio_path: str,
        meeting_title: str = "Cuộc họp",
        language: str = "vi",
    ) -> MeetingOutput:
        job_id = str(uuid.uuid4())
        logger.info(f"Pipeline started: job_id={job_id}, audio={audio_path}")

        try:
            # Stage 1: Preprocess
            logger.info("[1/7] Preprocessing audio")
            processed_path = self._preprocessor.preprocess(audio_path)

            # Stage 2: STT
            logger.info("[2/7] Running STT")
            raw_transcript = self._stt.transcribe(processed_path)

            # Stage 3: Diarization
            logger.info("[3/7] Running diarization")
            diar_segments = self._diarizer.diarize(processed_path)

            # Stage 4: Align transcript + speakers
            logger.info("[4/8] Aligning transcript with speakers")
            aligned_transcript = self._aligner.align(raw_transcript, diar_segments)

            # Stage 5: Filter off-topic segments (rule-based, no LLM)
            logger.info("[5/8] Filtering off-topic segments")
            filtered_transcript = self._filter.filter(aligned_transcript)

            # Stage 6: Clean transcript (batch mode — 8 segments per LLM call)
            logger.info("[6/8] Cleaning transcript (batch)")
            cleaned_transcript = self._cleaner.clean(filtered_transcript)

            # Stage 7: Generate minutes
            logger.info("[7/8] Generating meeting minutes")
            minutes = self._minutes_gen.generate(cleaned_transcript, meeting_title)

            # Stage 8: Extract action items (chunked, token-safe)
            logger.info("[8/8] Extracting action items (chunked)")
            action_items = self._action_extractor.extract(cleaned_transcript)

            logger.info(f"Pipeline completed: job_id={job_id}")
            return MeetingOutput(
                job_id=job_id,
                audio_path=audio_path,
                status="completed",
                processed_at=datetime.utcnow(),
                transcript=cleaned_transcript,
                minutes=minutes,
                action_items=action_items,
                duration_seconds=cleaned_transcript.duration_seconds,
                speaker_count=cleaned_transcript.speaker_count,
                language=language,
            )

        except AIPlatformError:
            raise
        except Exception as exc:
            logger.error(f"Pipeline failed: job_id={job_id}, error={exc}", exc_info=True)
            return MeetingOutput(
                job_id=job_id,
                audio_path=audio_path,
                status="failed",
                processed_at=datetime.utcnow(),
                language=language,
                error=str(exc),
            )
