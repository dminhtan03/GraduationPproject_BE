# AI Platform — Meeting Agent

AI-powered meeting audio processing pipeline. Converts raw audio into structured meeting minutes and action items.

## Pipeline

```
Audio Input → Preprocess → STT (Whisper) → Diarization (Pyannote)
    → Align Transcript → LLM Clean → Generate Minutes → Extract Actions
    → JSON Output
```

## Quick Start

```bash
# Install core deps (no ML models yet)
pip install -r requirements.txt

# Copy and configure environment
cp .env.example .env

# Run skeleton pipeline (stub mode, no real models needed)
python -m scripts.run_meeting_pipeline data/samples/sample.wav

# Start API server
uvicorn app.main:app --reload --port 8001

# API docs: http://localhost:8001/docs
```

## Install ML Dependencies (when ready)

```bash
# 1. Install PyTorch first (choose your platform at pytorch.org)
pip install torch torchaudio --index-url https://download.pytorch.org/whl/cpu

# 2. Install STT
pip install faster-whisper

# 3. Install diarization (requires HuggingFace account + model access)
pip install pyannote.audio

# 4. Install audio processing
pip install pydub soundfile librosa
# Also install ffmpeg binary: https://ffmpeg.org/download.html
```

## Project Structure

```
app/
├── api/routes/     HTTP endpoints
├── agents/         High-level agent orchestrators
├── pipelines/      End-to-end pipeline logic
├── services/
│   ├── audio/      Audio preprocessing
│   ├── speech/     STT + Diarization + Alignment
│   ├── nlp/        LLM-powered text processing
│   └── llm/        OpenAI client wrapper
├── schemas/        Pydantic I/O models
├── workers/        Async job queue
└── core/           Config, logging, exceptions
```

## Implementation Status

| Stage | Status |
|---|---|
| Project structure | Done |
| FastAPI skeleton | Done |
| Pydantic schemas | Done |
| Audio preprocess | Stub |
| STT (Whisper) | Stub |
| Diarization | Stub |
| Alignment | Implemented (logic ready) |
| Transcript clean | Stub (LLM ready) |
| Minutes generation | Stub (LLM ready) |
| Action extraction | Stub (LLM ready) |

## Next Steps

1. `pip install pydub soundfile` → implement `AudioPreprocessor.preprocess()`
2. `pip install faster-whisper` → implement `WhisperSTT.transcribe()`
3. `pip install pyannote.audio` → implement `SpeakerDiarizer.diarize()`
4. Set `OPENAI_API_KEY` → implement LLM-based NLP services
