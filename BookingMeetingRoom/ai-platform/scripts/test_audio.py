"""
Placeholder script to validate audio file before processing.

Usage:
    python -m scripts.test_audio data/samples/sample.wav
"""

import sys
import argparse
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.utils.file_utils import is_supported_audio, file_size_mb
from app.core.logging import logger


def main() -> None:
    parser = argparse.ArgumentParser(description="Check audio file validity")
    parser.add_argument("audio_path", help="Path to audio file")
    args = parser.parse_args()

    path = Path(args.audio_path)

    print(f"Checking: {path}")
    print(f"  Exists       : {path.exists()}")
    print(f"  Extension    : {path.suffix}")
    print(f"  Supported    : {is_supported_audio(str(path))}")

    if path.exists():
        print(f"  Size (MB)    : {file_size_mb(str(path)):.2f}")
    else:
        print("  [WARNING] File does not exist")

    # TODO: When pydub/soundfile is installed, print:
    # - duration
    # - sample rate
    # - channels
    # - bit depth


if __name__ == "__main__":
    main()
