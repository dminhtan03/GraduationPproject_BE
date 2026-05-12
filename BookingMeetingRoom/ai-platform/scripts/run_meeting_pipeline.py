"""
CLI script to run the Meeting Pipeline on a local audio file.

Usage:
    cd D:\\business-assistant\\ai-platform
    python -m scripts.run_meeting_pipeline data/samples/sample.wav
    python -m scripts.run_meeting_pipeline data/samples/sample.wav --title "Sprint Review" --output out.json
"""

import sys
import json
import argparse
from pathlib import Path

# Ensure repo root is on sys.path when run as module
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.pipelines.meeting_pipeline import MeetingPipeline
from app.core.logging import logger


def main() -> None:
    parser = argparse.ArgumentParser(description="Run Meeting Pipeline on an audio file")
    parser.add_argument("audio_path", help="Path to audio file (wav, mp3, m4a, ...)")
    parser.add_argument("--title", default="Cuộc họp", help="Meeting title")
    parser.add_argument("--language", default="vi", help="Primary language (default: vi)")
    parser.add_argument("--output", default=None, help="Optional output JSON file path")
    args = parser.parse_args()

    audio_path = Path(args.audio_path)
    if not audio_path.exists():
        logger.warning(f"Audio file not found: {audio_path} — running pipeline in stub mode anyway")

    logger.info(f"Running MeetingPipeline on: {audio_path}")
    pipeline = MeetingPipeline()
    result = pipeline.run(
        audio_path=str(audio_path),
        meeting_title=args.title,
        language=args.language,
    )

    output_dict = result.model_dump(mode="json")
    output_json = json.dumps(output_dict, indent=2, ensure_ascii=False)

    if args.output:
        Path(args.output).write_text(output_json, encoding="utf-8")
        logger.info(f"Output saved to: {args.output}")
    else:
        print(output_json)


if __name__ == "__main__":
    main()
