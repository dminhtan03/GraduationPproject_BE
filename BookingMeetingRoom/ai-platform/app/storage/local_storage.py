import shutil
import json
from pathlib import Path
from datetime import datetime
from app.core.config import settings
from app.core.logging import logger
from app.core.exceptions import StorageError


class LocalStorage:
    """
    File system storage for audio files and pipeline outputs.
    """

    def __init__(self):
        self.raw_dir = Path(settings.raw_audio_dir)
        self.processed_dir = Path(settings.processed_audio_dir)
        self.outputs_dir = Path(settings.outputs_dir)

        for d in (self.raw_dir, self.processed_dir, self.outputs_dir):
            d.mkdir(parents=True, exist_ok=True)

    def save_upload(self, source_path: str, filename: str) -> str:
        """Copy an uploaded file to raw audio storage."""
        dest = self.raw_dir / filename
        try:
            shutil.copy2(source_path, dest)
            logger.info(f"Saved upload: {dest}")
            return str(dest)
        except OSError as exc:
            raise StorageError(f"Failed to save upload: {exc}") from exc

    def save_output(self, job_id: str, data: dict) -> str:
        """Persist pipeline JSON output."""
        out_file = self.outputs_dir / f"{job_id}.json"
        try:
            out_file.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
            logger.info(f"Saved output: {out_file}")
            return str(out_file)
        except OSError as exc:
            raise StorageError(f"Failed to save output: {exc}") from exc

    def load_output(self, job_id: str) -> dict:
        """Load a previously saved pipeline output."""
        out_file = self.outputs_dir / f"{job_id}.json"
        if not out_file.exists():
            raise StorageError(f"Output not found for job: {job_id}")
        return json.loads(out_file.read_text(encoding="utf-8"))
