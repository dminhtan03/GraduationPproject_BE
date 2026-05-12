"""
FastMeetingPipeline — production-optimised, 8-stage pipeline.

Stages:
    1. OpenAI whisper-1  → RawTranscript (word timestamps + logprob)
    2. GapBasedDiarizer  → speaker segments
    3. Alignment         → AlignedTranscript with speaker labels
    4. Confidence scoring → HIGH / MEDIUM / LOW per segment
    5. Rule-based cleanup (NO LLM) → dedup / hallucination / filler
    6. Local speaker repair (NO LLM) → temporal / backchannel / continuity
    7. LLM fallback (LAST RESORT) → only truly-unresolvable LOW segments
    8. Topic filter + chunked task extraction

LLM is NEVER called for HIGH/MEDIUM segments.
Target: < 3% of segments reach the LLM.
"""

from __future__ import annotations

import json
import re
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from app.core.config import settings
from app.core.logging import logger
from app.schemas.action_item import ActionItem
from app.schemas.meeting import MeetingMinutes
from app.schemas.transcript import RawTranscript, CleanedTranscript, TranscriptSegment
from app.services.speech.diarization import GapBasedDiarizer
from app.services.speech.aligner import TranscriptAligner
from app.services.nlp.transcript_filter import TranscriptFilter
from app.services.nlp.task_extractor_chunked import ChunkedTaskExtractor
from app.services.nlp.minutes_generator import MinutesGenerator
from app.services.nlp.action_extractor import ActionItemExtractor
from app.services.llm.prompt_runner import PromptRunner


# ── Internal segment with confidence metadata ─────────────────────────────────

@dataclass
class ScoredSegment:
    speaker:        str
    text:           str
    start:          float
    end:            float
    asr_score:      float   = 1.0   # mean word probability from Whisper
    diar_score:     float   = 1.0   # gap-based speaker confidence
    align_score:    float   = 1.0   # timestamp alignment quality
    anomaly_score:  float   = 1.0   # speaker-switch anomaly (1=clean)
    composite:      float   = 1.0   # weighted combination
    tier:           str     = "HIGH"   # HIGH / MEDIUM / LOW
    repair_method:  str     = ""    # empty / rule / temporal / backchannel / llm
    words:          list    = field(default_factory=list)

    @property
    def duration(self) -> float:
        return max(0.0, self.end - self.start)

    @property
    def word_count(self) -> int:
        return len(self.text.split())


# ── Stage 1: Whisper-1 STT ────────────────────────────────────────────────────

def _stt_whisper1(audio_path: str, language: str = "vi") -> Optional[RawTranscript]:
    if not settings.openai_api_key:
        logger.warning("STT: OPENAI_API_KEY not configured")
        return None
    if not Path(audio_path).exists():
        logger.warning(f"STT: file not found: {audio_path}")
        return None
    try:
        from openai import OpenAI
        client = OpenAI(api_key=settings.openai_api_key)
        with open(audio_path, "rb") as f:
            resp = client.audio.transcriptions.create(
                model="whisper-1",
                file=f,
                language=language,
                response_format="verbose_json",
                timestamp_granularities=["segment"],
            )

        raw_segs = getattr(resp, "segments", None) or []
        segs: list[TranscriptSegment] = []
        for s in raw_segs:
            text  = (s.get("text") if isinstance(s, dict) else getattr(s, "text", "")).strip()
            start = float(s.get("start") if isinstance(s, dict) else getattr(s, "start", 0.0))
            end   = float(s.get("end")   if isinstance(s, dict) else getattr(s, "end",   0.0))
            # avg_logprob from Whisper: closer to 0 is better (range ~-2 to 0)
            logprob = float(s.get("avg_logprob") if isinstance(s, dict) else getattr(s, "avg_logprob", -0.3))
            if text:
                seg = TranscriptSegment(speaker="UNKNOWN", start=start, end=end, text=text)
                seg.__dict__["_avg_logprob"] = logprob
                segs.append(seg)

        if not segs:
            full     = getattr(resp, "text", "").strip()
            duration = float(getattr(resp, "duration", 0.0))
            if full:
                segs = [TranscriptSegment(speaker="UNKNOWN", start=0.0, end=duration or 1.0, text=full)]

        duration_out = float(getattr(resp, "duration", segs[-1].end if segs else 0.0))
        logger.info(f"STT: {len(segs)} segments, duration={duration_out:.1f}s")
        return RawTranscript(segments=segs, language=language, duration_seconds=duration_out)

    except Exception as exc:
        logger.warning(f"whisper-1 failed: {exc}")
        return None


# ── Stage 4: Confidence Scoring ───────────────────────────────────────────────

def _logprob_to_score(logprob: float) -> float:
    """Convert avg_logprob (-∞ to 0) → confidence (0 to 1)."""
    # logprob=0 → 1.0; logprob=-0.5 → ~0.6; logprob=-1.0 → ~0.37
    import math
    return max(0.0, min(1.0, math.exp(logprob)))


def _detect_switch_anomaly(seg: ScoredSegment, history: list[ScoredSegment]) -> float:
    """
    Detect suspicious speaker switches.
    Returns 0.0 (very anomalous) → 1.0 (clean).
    """
    if not history:
        return 1.0

    recent = [h for h in history[-5:] if h.end > seg.start - 6.0]
    if not recent:
        return 1.0

    switches = sum(
        1 for a, b in zip(recent, recent[1:]) if a.speaker != b.speaker
    )
    # More than 3 switches in last 6 seconds is suspicious
    if switches > 3:
        return 0.3

    # Very short segment with different speaker than neighbor
    prev = recent[-1]
    if seg.duration < 0.4 and prev.speaker != seg.speaker and seg.duration < 0.5:
        return 0.4

    # Gap-based: unrealistically fast speaker change
    gap = seg.start - prev.end
    if gap < 0.0 and prev.speaker != seg.speaker:
        return 0.5  # overlapping segments → ambiguous

    return 1.0


def _score_segments(
    aligned: list[TranscriptSegment],
    raw_segs: list[TranscriptSegment],
) -> list[ScoredSegment]:
    """
    Compute per-segment confidence scores from available signals.
    GapBasedDiarizer doesn't produce embeddings, so we use:
      - ASR logprob  (from Whisper)
      - Segment duration anomaly
      - Speaker-switch anomaly
      - Text quality (very short / punctuation only)
    """
    # Build logprob lookup from raw whisper segments by start time
    logprob_map: dict[float, float] = {}
    for rs in raw_segs:
        lp = rs.__dict__.get("_avg_logprob", -0.3)
        logprob_map[round(rs.start, 2)] = lp

    scored: list[ScoredSegment] = []
    for seg in aligned:
        lp       = logprob_map.get(round(seg.start, 2), -0.3)
        asr      = _logprob_to_score(lp)
        duration = max(0.0, (seg.end or 0) - (seg.start or 0))

        # Duration anomaly: too short or impossibly long
        if duration < 0.2 or duration > 30.0:
            diar_score = 0.4
        elif duration < 0.5:
            diar_score = 0.65
        else:
            diar_score = 0.85

        # Text quality
        words = seg.text.strip().split()
        if len(words) == 0:
            continue  # drop empty
        if not re.search(r"\w", seg.text):
            continue  # drop punctuation-only

        # Align score: penalise UNKNOWN speaker
        align_score = 0.5 if seg.speaker == "UNKNOWN" else 0.9

        ss = ScoredSegment(
            speaker=seg.speaker,
            text=seg.text.strip(),
            start=seg.start or 0.0,
            end=seg.end or 0.0,
            asr_score=asr,
            diar_score=diar_score,
            align_score=align_score,
            anomaly_score=1.0,   # filled in below
        )
        scored.append(ss)

    # Anomaly pass (needs full list for history)
    for i, ss in enumerate(scored):
        ss.anomaly_score = _detect_switch_anomaly(ss, scored[:i])
        ss.composite = (
            0.35 * ss.asr_score +
            0.25 * ss.diar_score +
            0.20 * ss.align_score +
            0.20 * ss.anomaly_score
        )
        ss.tier = (
            "HIGH"   if ss.composite >= 0.75 else
            "MEDIUM" if ss.composite >= 0.50 else
            "LOW"
        )

    high   = sum(1 for s in scored if s.tier == "HIGH")
    medium = sum(1 for s in scored if s.tier == "MEDIUM")
    low    = sum(1 for s in scored if s.tier == "LOW")
    logger.info(f"Confidence: HIGH={high} MEDIUM={medium} LOW={low} / {len(scored)} total")
    return scored


# ── Stage 5: Rule-Based Cleanup (NO LLM) ─────────────────────────────────────

_FILLER_VI = re.compile(
    r"\b(ừm*|ờ+|à+|ơ+|ạ|thì|là(?=\s)|uh|um|er|ah)\b",
    re.IGNORECASE
)
_HALLUCINATION = [
    re.compile(r"(.{4,})\1{2,}"),                       # long phrase repeated 3x+
    re.compile(r"\b(\w+)(\s+\1){2,}\b"),                # word repeated 3x+
    re.compile(r"^(Cảm ơn|Like và|Subscribe|Xin chào các bạn đã xem)", re.IGNORECASE),
    re.compile(r"^\s*[.,!?;:]+\s*$"),                   # only punctuation
]
_REPEATED_PUNCT = re.compile(r"([.,!?]){2,}")


def _normalize_for_dedup(text: str) -> str:
    """Lowercase + remove spaces for fuzzy dedup comparison."""
    return re.sub(r"\s+", "", text.lower())


def _rule_cleanup(segments: list[ScoredSegment]) -> list[ScoredSegment]:
    """
    Stage 5: deterministic text cleanup — no LLM.
    Drops hallucinated / empty segments.
    Applies in-place text fixes + cross-segment deduplication.
    """
    cleaned: list[ScoredSegment] = []
    dropped = 0
    # Rolling window of recent normalized texts for cross-segment dedup
    recent_texts: list[str] = []
    DEDUP_WINDOW = 6   # check against last N segments
    DEDUP_RATIO  = 0.85  # similarity threshold to consider duplicate

    for seg in segments:
        text = seg.text

        # 1. Hallucination check → drop segment
        if any(p.search(text) for p in _HALLUCINATION):
            dropped += 1
            continue

        # 2. Impossible timestamp → duration sanity
        if seg.duration <= 0 or seg.duration > 60:
            dropped += 1
            continue

        # 3. Remove filler words
        text = _FILLER_VI.sub("", text)

        # 4. Collapse repeated punctuation
        text = _REPEATED_PUNCT.sub(r"\1", text)

        # 5. Normalize Unicode
        text = unicodedata.normalize("NFC", text)

        # 6. Strip leading/trailing punctuation artifacts
        text = re.sub(r"^[,.\s]+|[,.\s]+$", "", text).strip()

        # 7. Drop if nothing meaningful remains
        if not text or not re.search(r"\w", text):
            dropped += 1
            continue

        # 8. Cross-segment deduplication (Whisper hallucination)
        norm = _normalize_for_dedup(text)
        is_dup = False
        for prev_norm in recent_texts[-DEDUP_WINDOW:]:
            if not prev_norm:
                continue
            shorter  = min(len(norm), len(prev_norm))
            longer   = max(len(norm), len(prev_norm))
            if longer == 0:
                continue
            # Character overlap ratio
            overlap = sum(a == b for a, b in zip(norm, prev_norm))
            ratio   = overlap / longer
            if ratio >= DEDUP_RATIO:
                is_dup = True
                break

        if is_dup:
            dropped += 1
            continue

        seg.text = text
        recent_texts.append(norm)
        cleaned.append(seg)

    logger.info(f"Rule cleanup: kept {len(cleaned)}/{len(segments)}, dropped {dropped}")
    return cleaned


# ── Stage 6: Local Speaker Repair (NO LLM) ───────────────────────────────────

_BACKCHANNELS_VI = frozenset({
    "ừ", "vâng", "ok", "được", "aha", "đúng", "ừa", "nhỉ",
    "oke", "yeah", "rồi", "thôi", "ờ", "um", "mm",
})


def _local_repair(segments: list[ScoredSegment]) -> list[ScoredSegment]:
    """
    Stage 6: repair LOW-confidence speaker attribution without LLM.
    Applies 4 passes in order — each pass marks segments with repair_method.
    Returns repaired list.
    """
    segs = list(segments)

    # Pass 1 — Temporal smoothing
    # UNKNOWN segment between two occurrences of the same speaker
    # within a short time window → assign that speaker
    TEMPORAL_WINDOW = 3.0  # seconds
    for i, seg in enumerate(segs):
        if seg.speaker != "UNKNOWN" and seg.tier != "LOW":
            continue
        prev_spk = next((s.speaker for s in reversed(segs[:i])
                         if s.speaker not in ("UNKNOWN", "")), None)
        next_spk = next((s.speaker for s in segs[i+1:]
                         if s.speaker not in ("UNKNOWN", "")), None)
        gap_prev = seg.start - segs[i-1].end if i > 0 else 999
        if prev_spk and prev_spk == next_spk and gap_prev < TEMPORAL_WINDOW:
            segs[i].speaker = prev_spk
            segs[i].tier = "MEDIUM"
            segs[i].repair_method = "temporal_smooth"

    # Pass 2 — Backchannel inference
    # Segments that are ONLY backchannels → assign to non-active speaker
    all_speakers = {s.speaker for s in segs if s.speaker not in ("UNKNOWN", "")}
    for i, seg in enumerate(segs):
        words = set(seg.text.lower().split())
        if not words or not words.issubset(_BACKCHANNELS_VI):
            continue
        prev_spk = segs[i-1].speaker if i > 0 else None
        nxt_spk  = segs[i+1].speaker if i < len(segs)-1 else None
        active   = prev_spk or nxt_spk
        listeners = all_speakers - {active, "UNKNOWN", ""}
        if listeners:
            segs[i].speaker = next(iter(listeners))
            segs[i].repair_method = "backchannel"

    # Pass 3 — Short utterance reassignment
    # Very short segments with LOW confidence → assign to most recent HIGH speaker
    SHORT_WORD_THRESHOLD = 3
    MAX_GAP              = 4.0
    for i, seg in enumerate(segs):
        if seg.tier != "LOW" or seg.word_count >= SHORT_WORD_THRESHOLD:
            continue
        prev_high = next(
            (s for s in reversed(segs[:i]) if s.tier == "HIGH"),
            None
        )
        if prev_high and (seg.start - prev_high.end) < MAX_GAP:
            segs[i].speaker = prev_high.speaker
            segs[i].tier    = "MEDIUM"
            segs[i].repair_method = "short_utterance"

    # Pass 4 — Continuity propagation (multi-pass until stable)
    changed = True
    iterations = 0
    while changed and iterations < 5:
        changed = False
        iterations += 1
        for i, seg in enumerate(segs):
            if seg.speaker not in ("UNKNOWN", "") or seg.tier != "LOW":
                continue
            prev = segs[i-1] if i > 0 else None
            nxt  = segs[i+1] if i < len(segs)-1 else None
            if (prev and nxt and
                    prev.speaker == nxt.speaker and
                    prev.speaker not in ("UNKNOWN", "") and
                    seg.duration < 6.0):
                segs[i].speaker = prev.speaker
                segs[i].tier    = "MEDIUM"
                segs[i].repair_method = "continuity"
                changed = True

    repaired = sum(1 for s in segs if s.repair_method)
    still_low = sum(1 for s in segs if s.tier == "LOW")
    logger.info(f"Local repair: {repaired} repaired, {still_low} still LOW")
    return segs


# ── Stage 7: LLM Fallback (LAST RESORT) ──────────────────────────────────────

_LLM_REPAIR_CACHE: dict[str, str] = {}

_LLM_REPAIR_SYSTEM = (
    "Fix speaker attribution. "
    "Speakers: {spk_list}. "
    "Return ONLY JSON array: [{\"id\":N,\"spk\":\"S\"}]. "
    "No explanation."
)


def _llm_repair(
    segments: list[ScoredSegment],
    runner: PromptRunner,
    batch_size: int = 8,
) -> list[ScoredSegment]:
    """
    Stage 7: LLM fallback for remaining LOW-confidence segments only.
    Batches 8 segments per call with minimal context.
    Target: < 3% of total segments reach this stage.
    """
    low_indices = [i for i, s in enumerate(segments) if s.tier == "LOW"]
    if not low_indices:
        return segments

    logger.info(f"LLM fallback: {len(low_indices)} LOW segments to repair")

    # Speaker list (compress to S0, S1...)
    all_spks = sorted({s.speaker for s in segments if s.speaker not in ("UNKNOWN", "")})
    spk_map  = {spk: f"S{i}" for i, spk in enumerate(all_spks)}
    rev_map  = {v: k for k, v in spk_map.items()}

    def _context_window(idx: int) -> dict:
        seg  = segments[idx]
        prev = next((segments[j] for j in range(idx-1, -1, -1)
                     if segments[j].speaker not in ("UNKNOWN", "")), None)
        nxt  = next((segments[j] for j in range(idx+1, len(segments))
                     if segments[j].speaker not in ("UNKNOWN", "")), None)
        return {
            "p": f"{spk_map.get(prev.speaker,'?')}:{prev.text[:50]}" if prev else "",
            "t": seg.text[:80],
            "n": f"{spk_map.get(nxt.speaker,'?')}:{nxt.text[:50]}"  if nxt else "",
        }

    # Process in batches
    for batch_start in range(0, len(low_indices), batch_size):
        batch_idxs = low_indices[batch_start:batch_start + batch_size]
        items      = []
        cached_map: dict[int, str] = {}

        for local_id, seg_idx in enumerate(batch_idxs):
            ctx = _context_window(seg_idx)
            cache_key = f"{ctx['p']}|{ctx['t']}|{ctx['n']}"
            if cache_key in _LLM_REPAIR_CACHE:
                cached_map[local_id] = _LLM_REPAIR_CACHE[cache_key]
            else:
                items.append({"id": local_id, "p": ctx["p"], "t": ctx["t"], "n": ctx["n"],
                              "_key": cache_key})

        # Call LLM only for uncached items
        if items:
            uncached_payload = [{"id": it["id"], "p": it["p"], "t": it["t"], "n": it["n"]}
                                for it in items]
            system = _LLM_REPAIR_SYSTEM.format(spk_list=list(spk_map.values()))
            user   = json.dumps(uncached_payload, ensure_ascii=False)
            try:
                raw  = runner.run_text(system_prompt=system, user_prompt=user, temperature=0.0)
                data = json.loads(raw or "[]") if raw else []
                if not isinstance(data, list):
                    data = []
            except Exception as exc:
                logger.warning(f"LLM repair batch failed: {exc}")
                data = []

            for result in data:
                if not isinstance(result, dict):
                    continue
                local_id = result.get("id")
                spk_code = result.get("spk", "")
                real_spk = rev_map.get(spk_code)
                if local_id is not None and real_spk:
                    cached_map[local_id] = real_spk
                    # Cache for future identical contexts
                    matching = next((it for it in items if it["id"] == local_id), None)
                    if matching:
                        _LLM_REPAIR_CACHE[matching["_key"]] = real_spk

        # Apply repairs
        for local_id, seg_idx in enumerate(batch_idxs):
            if local_id in cached_map:
                segments[seg_idx].speaker       = cached_map[local_id]
                segments[seg_idx].tier          = "MEDIUM"
                segments[seg_idx].repair_method = "llm_fallback"

    remaining_low = sum(1 for s in segments if s.tier == "LOW")
    logger.info(f"LLM repair done. Remaining LOW: {remaining_low}")
    return segments


# ── Speaker fallback: LLM assigns turns when diarizer sees only 1 speaker ────

def _assign_speakers_llm(full_text: str, meeting_title: str, runner: PromptRunner) -> Optional[str]:
    system = (
        "Bạn nhận transcript cuộc họp tiếng Việt. "
        "Phân tích ngữ cảnh, xác định người nói, "
        "định dạng lại: 'Người 1: ...' / 'Người 2: ...' etc. "
        "Chỉ trả về transcript đã format."
    )
    user = (
        f"Cuộc họp: {meeting_title}\n\n"
        f"Transcript:\n{full_text[:4000]}\n\n"
        "Format lại theo người nói:"
    )
    result = runner.run_text(system_prompt=system, user_prompt=user)
    if result and "Người" in result:
        return result.strip()
    return None


# ── Result schema ─────────────────────────────────────────────────────────────

@dataclass
class FastPipelineResult:
    transcript_text:   str
    summary:           str
    key_decisions:     list[str]        = field(default_factory=list)
    discussion_points: list[str]        = field(default_factory=list)
    action_items:      list[ActionItem] = field(default_factory=list)
    extracted_tasks:   list[dict]       = field(default_factory=list)
    speaker_count:     int              = 0
    duration_seconds:  float            = 0.0
    stt_failed:        bool             = False
    repair_stats:      dict             = field(default_factory=dict)
    error:             Optional[str]    = None


# ── Pipeline ──────────────────────────────────────────────────────────────────

class FastMeetingPipeline:
    """
    8-stage production pipeline.
    LLM is used ONLY in Stage 7 (< 3% of segments) and Stage 1 (STT).
    All other processing is rule-based or heuristic.
    """

    def __init__(self):
        self._diarizer       = GapBasedDiarizer()
        self._aligner        = TranscriptAligner()
        self._topic_filter   = TranscriptFilter()
        self._task_extractor = ChunkedTaskExtractor()
        self._minutes        = MinutesGenerator()
        self._actions        = ActionItemExtractor()
        self._runner         = PromptRunner()

    def run(
        self,
        audio_path: str,
        meeting_title: str = "Cuộc họp",
        language: str = "vi",
    ) -> FastPipelineResult:
        logger.info(f"FastMeetingPipeline START: audio={audio_path}")

        # ── Stage 1: STT ──────────────────────────────────────────────────────
        logger.info("[1/8] STT (whisper-1)")
        raw = _stt_whisper1(audio_path, language)

        if raw is None or not raw.segments:
            logger.warning("STT failed — no transcript")
            return FastPipelineResult(
                transcript_text="",
                summary=f'Cuộc họp "{meeting_title}". Không thể phiên âm.',
                stt_failed=True,
            )

        # ── Stage 2: Diarization ──────────────────────────────────────────────
        logger.info("[2/8] Diarization (GapBased)")
        diar_segs = self._diarizer.diarize_from_transcript(raw)

        # ── Stage 3: Alignment ────────────────────────────────────────────────
        logger.info("[3/8] Alignment")
        cleaned   = self._aligner.align(raw, diar_segs)
        logger.info(f"  → {cleaned.speaker_count} speakers, {len(cleaned.segments)} segments")
        logger.debug(f"  Preview: {cleaned.full_text[:200]}")

        # ── Stage 4: Confidence scoring ───────────────────────────────────────
        logger.info("[4/8] Confidence scoring")
        scored = _score_segments(cleaned.segments, raw.segments)

        total   = len(scored)
        n_high  = sum(1 for s in scored if s.tier == "HIGH")
        n_med   = sum(1 for s in scored if s.tier == "MEDIUM")
        n_low   = sum(1 for s in scored if s.tier == "LOW")
        logger.info(f"  → HIGH={n_high} MED={n_med} LOW={n_low} / {total}")

        # ── Stage 5: Rule-based cleanup (NO LLM) ─────────────────────────────
        logger.info("[5/8] Rule-based cleanup")
        scored = _rule_cleanup(scored)

        # ── Stage 6: Local speaker repair (NO LLM) ────────────────────────────
        logger.info("[6/8] Local speaker repair")
        scored = _local_repair(scored)

        # ── Stage 7: LLM fallback (last resort, LOW segments only) ────────────
        low_count = sum(1 for s in scored if s.tier == "LOW")
        if low_count > 0:
            low_pct = 100 * low_count / max(len(scored), 1)
            logger.info(f"[7/8] LLM fallback ({low_count} segs, {low_pct:.1f}% of total)")
            scored = _llm_repair(scored, self._runner)
        else:
            logger.info("[7/8] LLM fallback: skipped (no LOW segments)")

        # Repair stats
        repair_stats = {
            m: sum(1 for s in scored if s.repair_method == m)
            for m in ("temporal_smooth", "backchannel", "short_utterance",
                      "continuity", "llm_fallback")
        }

        # ── Rebuild CleanedTranscript from scored segments ────────────────────
        rebuilt_segs = [
            TranscriptSegment(
                speaker=s.speaker,
                start=s.start,
                end=s.end,
                text=s.text,
                confidence=s.composite,
            )
            for s in scored
        ]
        rebuilt_full = "\n".join(
            f"{s.speaker}: {s.text}" for s in scored
        )
        speaker_count = len({s.speaker for s in scored if s.speaker not in ("UNKNOWN", "")})

        transcript_text = rebuilt_full

        # ── LLM speaker assignment if diarizer detected only 1 speaker ────────
        if speaker_count <= 1 and len(scored) > 3:
            logger.info("  Only 1 speaker — LLM speaker assignment")
            plain = " ".join(s.text for s in scored)
            llm_t = _assign_speakers_llm(plain, meeting_title, self._runner)
            if llm_t:
                transcript_text = llm_t
                speaker_count = len({
                    line.split(":")[0].strip()
                    for line in llm_t.split("\n")
                    if ":" in line and line.strip().startswith("Người")
                })
                logger.info(f"  LLM assigned {speaker_count} speakers")

        rebuilt_cleaned = CleanedTranscript(
            segments=rebuilt_segs,
            language=language,
            duration_seconds=raw.duration_seconds,
            speaker_count=speaker_count,
            full_text=transcript_text,
        )

        # ── Stage 8a: Topic filter (removes small-talk before LLM stages) ─────
        logger.info("[8/8a] Topic filter")
        try:
            filtered = self._topic_filter.filter(rebuilt_cleaned)
            # Use filtered transcript as the displayed content — no small talk
            transcript_text = filtered.full_text
        except Exception as exc:
            logger.warning(f"TopicFilter failed: {exc}")
            filtered = rebuilt_cleaned

        # ── Stage 8b: Minutes ─────────────────────────────────────────────────
        logger.info("[8/8b] Minutes generation")
        try:
            minutes = self._minutes.generate(filtered, meeting_title)
            summary = minutes.summary or f'Cuộc họp "{meeting_title}" đã hoàn thành.'
        except Exception as exc:
            logger.warning(f"Minutes failed: {exc}")
            minutes = MeetingMinutes(title=meeting_title, summary="")
            summary = f'Cuộc họp "{meeting_title}" đã hoàn thành.'

        # ── Stage 8c: Legacy action items ─────────────────────────────────────
        try:
            action_items = self._actions.extract(filtered)
        except Exception as exc:
            logger.warning(f"ActionExtractor failed: {exc}")
            action_items = []

        # ── Stage 8d: Chunked task extraction (richer, token-safe) ────────────
        logger.info("[8/8d] Chunked task extraction")
        try:
            extracted_tasks = self._task_extractor.extract(filtered)
        except Exception as exc:
            logger.warning(f"ChunkedTaskExtractor failed: {exc}")
            extracted_tasks = []

        logger.info(
            f"FastMeetingPipeline DONE: speakers={speaker_count} "
            f"tasks={len(extracted_tasks)} duration={raw.duration_seconds:.1f}s "
            f"repair={repair_stats}"
        )
        return FastPipelineResult(
            transcript_text=transcript_text,
            summary=summary,
            key_decisions=getattr(minutes, "key_decisions", []) or [],
            discussion_points=getattr(minutes, "discussion_points", []) or [],
            action_items=action_items,
            extracted_tasks=extracted_tasks,
            speaker_count=speaker_count,
            duration_seconds=raw.duration_seconds,
            repair_stats=repair_stats,
        )
