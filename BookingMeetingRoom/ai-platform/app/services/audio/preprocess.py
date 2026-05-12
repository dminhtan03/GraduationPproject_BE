import subprocess
from pathlib import Path
from app.core.logging import logger


class AudioPreprocessor:
    """
    Converts and normalizes audio files to WAV 16kHz mono for STT and diarization.
    Uses ffmpeg via subprocess — no heavy Python audio library required.
    """

    TARGET_SAMPLE_RATE = 16000
    TARGET_CHANNELS = 1
    STRICT_MODE = True

    def __init__(self, output_dir: str = "data/processed"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)

    def preprocess(self, input_path: str) -> str:
        """
        Convert audio file to WAV 16kHz mono and return the output path.

        Raises:
            FileNotFoundError: if input file does not exist.
            RuntimeError: if ffmpeg conversion fails.
        """
        src = Path(input_path)

        if not src.exists():
            logger.warning(f"Audio file not found: {src}")
            # In STRICT_MODE: fail fast for production
            # In non-strict mode: allow pipeline to continue (useful for testing)
            if self.STRICT_MODE:
                raise FileNotFoundError(f"Audio file not found: {src}")
            else:
                logger.warning("Running in non-strict mode, skipping preprocessing and returning original path")
                return str(src)

        dest = self.output_dir / f"{src.stem}_processed.wav"

        logger.info(f"Preprocessing audio: {src}")
        logger.info(f"Output path: {dest}")

        cmd = [
            "ffmpeg", "-y",
            "-i", str(src),
            "-ac", str(self.TARGET_CHANNELS),
            "-ar", str(self.TARGET_SAMPLE_RATE),
            str(dest),
        ]

        try:
            result = subprocess.run(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=300,
            )
        except FileNotFoundError:
            raise RuntimeError(
                "ffmpeg not found. Install it from https://ffmpeg.org/download.html "
                "and ensure it is on your system PATH."
            )

        if result.returncode != 0:
            stderr = result.stderr.decode(errors="replace")
            raise RuntimeError(f"ffmpeg failed (exit {result.returncode}):\n{stderr}")

        logger.info(f"Preprocessing complete: {dest}")
        return str(dest)

    def get_duration(self, audio_path: str) -> float:
        """
        Return audio duration in seconds using ffprobe.
        Returns 0.0 if ffprobe is unavailable or fails.
        """
        try:
            result = subprocess.run(
                [
                    "ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    audio_path,
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=30,
            )
            if result.returncode == 0:
                return float(result.stdout.decode().strip())
        except Exception:
            pass
        logger.warning(f"Could not determine duration for: {audio_path}")
        return 0.0
