# AI Platform — Technical Specification

**Version:** 0.2 | **Date:** 2026-05-04 | **Audience:** Backend Engineers

---

## 0. Quick Start (Backend)

Get the full extraction → review → approve flow running in 5 minutes.

### Prerequisites

- PostgreSQL running
- Backend `.env` has `AI_PLATFORM_URL` set (or ai-platform venv in same Python path)
- `OPENAI_API_KEY` set in ai-platform `.env` (optional for pipeline, required for extraction)

### Step 1 — Run migration

```bash
cd backend
alembic upgrade head
# New table: meeting_assignment_drafts
```

### Step 2 — Start backend

```bash
cd backend
uvicorn app.main:app --reload --port 8000
```

### Step 3 — Trigger AI extraction

```http
POST /api/meetings/{meeting_id}/ai-extract-assignments
Authorization: Bearer <token>
Content-Type: application/json

{
  "audio_path": "/data/recordings/sprint_review.wav"
}
```

Expected response: `201 Created` with `drafts[]` array.

### Step 4 — Verify DB

```sql
SELECT id, title, ai_confidence, review_status, review_issues
FROM meeting_assignment_drafts
WHERE meeting_id = '<your_meeting_id>'
ORDER BY created_at;
```

### Step 5 — List drafts (frontend call)

```http
GET /api/meetings/{meeting_id}/assignment-drafts
Authorization: Bearer <token>
```

### Step 6 — Fill missing fields + approve

```http
PATCH /api/assignment-drafts/{draft_id}
Content-Type: application/json

{
  "assigner_user_id": "uuid-of-manager",
  "assignee_user_id": "uuid-of-developer"
}
```

```http
POST /api/assignment-drafts/{draft_id}/approve
```

Response: `{ "created_task_id": "...", "created_assignment_id": "..." }`

---

## 1. System Overview

AI Platform is a standalone Python service that provides two categories of AI agents:

| Agent | Type | Responsibility |
|---|---|---|
| **MeetingAgent** | Intelligence agent | Processes meeting audio → extracts draft assignments for human review |
| **TaskAgent** | Action agent | Interprets natural language task commands → calls backend Task API |
| **OrchestratorAgent** | Router | Classifies intent, routes to the correct agent, manages multi-turn state |

**Critical constraint:** MeetingAgent **never creates real tasks or assignments**. It only produces draft candidates that require human approval via the backend review flow.

---

## 2. Architecture Overview

### Meeting Flow

```
Audio File
    │
    ▼
AudioPreprocessor          WAV 16kHz mono
    │
    ▼
WhisperSTT                 RawTranscript (segments + timestamps)
    │
    ▼
SpeakerDiarizer            list[DiarSegment] (speaker, start, end)
    │
    ▼
TranscriptAligner          CleanedTranscript (speaker per segment, confidence)
    │
    ▼
TranscriptCleaner (LLM)    CleanedTranscript (corrected Vietnamese text)
    │
    ▼
MinutesGenerator (LLM)     MeetingMinutes (summary, decisions, risks)
    │
    ▼
TaskExtractor (LLM)        list[ExtractedTaskItem]
    │
    ▼
ReviewBuilder              {review_items, review_summary}
    │
    ▼
MeetingAgent.process()  →  JSON response to backend
```

### Task Flow

```
User text input
    │
    ▼
OrchestratorAgent
    │── IntentAgent.classify() ──► "task" | "meeting" | "unknown"
    │
    ▼ (intent = "task")
TaskAgent.start_flow()
    │
    ▼
PromptRunner.run_json()    LLM classifies action + extracts payload
    │
    ▼
_normalize_llm_output()    action: str, payload: dict
    │
    ▼
_fallback_action_from_text()   keyword fallback if LLM unavailable
    │
    ▼
_execute_action()
    │
    ▼
BackendAPIClient → TaskAPI  POST/PATCH /tasks, /assignments
```

---

## 3. Component Breakdown

### 3.1 MeetingAgent (`app/agents/meeting_agent/`)

**Files:**

| File | Role |
|---|---|
| `agent.py` | Entry point — orchestrates pipeline + extractor + review |
| `task_extractor.py` | Calls LLM to extract task items from transcript |
| `review_builder.py` | Wraps extracted items with status/issues for review |
| `prompts.py` | LLM prompt constants for extraction |
| `schemas.py` | Pydantic schemas: `ExtractedTaskItem`, `MeetingAgentResult` |

**Entry point:**

```python
agent = MeetingAgent()
result = agent.process(audio_path="data/meeting.wav")
```

**Internal execution order:**

1. `MeetingPipeline.run(audio_path)` → `MeetingOutput`
2. Extract `transcript.full_text` from pipeline output (may be `None` if pipeline failed)
3. `TaskExtractor.extract(transcript)` → `list[dict]`
4. `ReviewBuilder.build(items)` → `{items, summary}`
5. Return structured response

**Output shape** — see Section 4.

---

### 3.2 MeetingPipeline (`app/pipelines/meeting_pipeline.py`)

7-stage pipeline. Stages run sequentially; any exception is caught and returns `status: "failed"`.

| Stage | Class | Output |
|---|---|---|
| 1. Preprocess | `AudioPreprocessor` | WAV 16kHz mono file path |
| 2. STT | `WhisperSTT` | `RawTranscript` |
| 3. Diarization | `SpeakerDiarizer` | `list[DiarSegment]` |
| 4. Align | `TranscriptAligner` | `CleanedTranscript` with speaker labels |
| 5. Clean | `TranscriptCleaner` | `CleanedTranscript` (LLM-corrected text) |
| 6. Minutes | `MinutesGenerator` | `MeetingMinutes` |
| 7. Action items | `ActionItemExtractor` | `list[ActionItem]` (internal, not exposed) |

**Stub behavior:** If ML packages (`faster-whisper`, `pyannote.audio`) are not installed, or `OPENAI_API_KEY` is not set, each stage falls back to stub output — the pipeline completes without crashing.

**Pipeline output schema:**

```json
{
  "job_id": "uuid",
  "audio_path": "path/to/file.wav",
  "status": "completed | failed",
  "processed_at": "2026-05-04T06:00:00",
  "transcript": {
    "segments": [
      {
        "segment_id": "uuid or null",
        "speaker": "SPEAKER_00",
        "start": 0.0,
        "end": 5.2,
        "text": "Chúng ta sẽ giao việc này cho Minh.",
        "confidence": 0.91,
        "stt_confidence": 0.92,
        "speaker_confidence": 0.89,
        "needs_review": false
      }
    ],
    "language": "vi",
    "duration_seconds": 120.0,
    "speaker_count": 3,
    "full_text": "SPEAKER_00: Chúng ta sẽ...\nSPEAKER_01: ..."
  },
  "minutes": {
    "title": "Cuộc họp sprint review",
    "summary": "Nhóm thảo luận tiến độ...",
    "key_decisions": ["Quyết định đẩy deadline lên 20/5"],
    "discussion_points": ["Tiến độ phát triển"],
    "risks": [],
    "open_questions": []
  },
  "action_items": [],
  "duration_seconds": 120.0,
  "speaker_count": 3,
  "language": "vi",
  "error": null
}
```

When `status: "failed"`, `transcript` is `null` and `error` contains the error message.

---

### 3.3 TaskExtractor (`app/agents/meeting_agent/task_extractor.py`)

**Input:** `transcript: str` — the `full_text` field from `CleanedTranscript`

**Behavior:**
- If `len(transcript.strip()) < 20` → returns `[]` immediately (too short to extract)
- Calls `PromptRunner.run_json()` with `EXTRACT_TASK_SYSTEM` prompt
- If LLM returns non-list → returns `[]`
- Clamps `ai_confidence` to `[0.0, 1.0]`

**Output:** `list[dict]` where each dict matches `ExtractedTaskItem` schema (Section 4.1)

**LLM rules enforced by prompt:**
- Must not fabricate UUIDs for `assigner_user_id` / `assignee_user_id`
- `ai_raw_text` must be an actual sentence from the transcript
- If no actionable tasks → return `[]`

---

### 3.4 ReviewBuilder (`app/agents/meeting_agent/review_builder.py`)

**Input:** `items: list[dict]` — raw output from `TaskExtractor`

**Classification logic:**

```python
if ai_confidence >= 0.85:
    status = "high_confidence"
else:
    status = "needs_review"
    issues.append("low_confidence")

if not assigner_user_id:
    issues.append("missing_assigner_user_id")

if not assignee_user_id:
    issues.append("missing_assignee_user_id")
```

**Output:**

```json
{
  "items": [
    {
      "item": { ...ExtractedTaskItem... },
      "status": "high_confidence | needs_review",
      "issues": ["low_confidence", "missing_assigner_user_id", "missing_assignee_user_id"]
    }
  ],
  "summary": {
    "total": 3,
    "high_confidence": 1,
    "need_review": 2,
    "missing_assigner": 3,
    "missing_assignee": 2
  }
}
```

---

### 3.5 TaskAgent (`app/agents/task_agent/`)

**Entry point:**

```python
agent.start_flow(text="tạo task họp daily", session_id="user_session_abc")
agent.continue_flow(text="Báo cáo Q2", session_id="user_session_abc")
```

**Supported actions:**

| Action | Triggers | Backend call |
|---|---|---|
| `create_task` | "tạo task", "create task", "thêm việc" | `POST /tasks` |
| `update_task` | "sửa", "update" | `PATCH /tasks/{id}` |
| `delete_task` | "xóa", "delete" | `DELETE /tasks/{id}` |
| `cancel_task` | "hủy task" | `PATCH /tasks/{id}/cancel` |
| `get_task` | "xem", "chi tiết" | `GET /tasks/{id}` |
| `assign_task` | "giao", "assign" | `POST /tasks/{id}/assignments` |
| `cancel_assignment` | "hủy giao" | `PATCH /tasks/{id}/assignments/{uid}/cancel` |

**Multi-turn slot filling:** If `create_task` is detected but `title` is missing, the agent saves state and asks "Bạn muốn đặt tiêu đề task là gì?". Next user turn fills the title and executes immediately.

**Break detection:** If user sends "hủy", "thoát", "stop", etc. while a flow is pending, the state is cleared and the flow is aborted cleanly.

---

## 4. AI Output Contract

### Full MeetingAgent.process() Response

```json
{
  "pipeline": {
    "job_id": "550e8400-e29b-41d4-a716-446655440000",
    "audio_path": "data/meeting.wav",
    "status": "completed",
    "processed_at": "2026-05-04T06:00:00",
    "transcript": { "...see 3.2..." },
    "minutes": { "...see 3.2..." },
    "action_items": [],
    "duration_seconds": 185.3,
    "speaker_count": 3,
    "language": "vi",
    "error": null
  },
  "extracted_items": [
    {
      "title": "Phát triển module thanh toán",
      "description": "Tích hợp VNPay vào checkout",
      "goal": "Người dùng thanh toán online được trước 20/5",
      "expected_result": "Module ổn định, test đủ case",
      "priority": "high",
      "due_at": "2026-05-20T17:00:00Z",
      "assigner_user_id": null,
      "assignee_user_id": null,
      "ai_confidence": 0.92,
      "ai_raw_text": "Anh Sơn giao cho Minh Đức làm phần thanh toán, deadline cuối tháng này nhé"
    }
  ],
  "review_required": true,
  "review_summary": {
    "total": 1,
    "high_confidence": 1,
    "need_review": 0,
    "missing_assigner": 1,
    "missing_assignee": 1
  },
  "review_items": [
    {
      "item": { "...same as extracted_items[0]..." },
      "status": "high_confidence",
      "issues": ["missing_assigner_user_id", "missing_assignee_user_id"]
    }
  ]
}
```

---

### 4.1 ExtractedTaskItem Schema

| Field | Type | Nullable | Description |
|---|---|---|---|
| `title` | `string` | No | Short task title, max ~200 chars |
| `description` | `string` | Yes | Detail or context |
| `goal` | `string` | Yes | Why this task matters |
| `expected_result` | `string` | Yes | What done looks like |
| `priority` | `"low"\|"medium"\|"high"` | Yes | AI-estimated priority |
| `due_at` | `string (ISO 8601)` | Yes | Deadline, e.g. `"2026-05-20T17:00:00Z"` |
| `assigner_user_id` | `uuid string` | **Always null** | AI never generates UUIDs — must be resolved by backend/UI |
| `assignee_user_id` | `uuid string` | **Always null** | Same — must be resolved |
| `ai_confidence` | `float [0.0, 1.0]` | No | Extraction confidence. ≥ 0.85 = high |
| `ai_raw_text` | `string` | No | Verbatim sentence from transcript this item was derived from |

**Important:** `assigner_user_id` and `assignee_user_id` are always `null`. The AI does not know internal user UUIDs. Backend/frontend must let users map speaker names to real users during the review step.

---

### 4.2 Review Item Schema

```json
{
  "item": { "...ExtractedTaskItem..." },
  "status": "high_confidence | needs_review",
  "issues": [
    "low_confidence",
    "missing_assigner_user_id",
    "missing_assignee_user_id"
  ]
}
```

| Issue code | Condition |
|---|---|
| `low_confidence` | `ai_confidence < 0.85` |
| `missing_assigner_user_id` | `assigner_user_id == null` |
| `missing_assignee_user_id` | `assignee_user_id == null` |

An item with `status: "high_confidence"` may still have issues (e.g. missing user IDs). Status and issues are independent.

---

### 4.3 Sample Response — Realistic (3 items)

```json
{
  "pipeline": {
    "job_id": "a3f2e1b0-cc21-4d5e-9f87-112233445566",
    "audio_path": "data/raw/sprint_review_2026_05_04.wav",
    "status": "completed",
    "processed_at": "2026-05-04T09:15:32Z",
    "duration_seconds": 2340.0,
    "speaker_count": 4,
    "language": "vi",
    "error": null
  },
  "extracted_items": [
    {
      "title": "Tích hợp cổng thanh toán VNPay",
      "description": "Xây dựng module thanh toán online cho luồng checkout, hỗ trợ thẻ nội địa và QR",
      "goal": "Khách hàng có thể thanh toán online mà không cần chuyển khoản thủ công",
      "expected_result": "Module hoạt động ổn định, pass toàn bộ test case thanh toán, không có lỗi timeout",
      "priority": "high",
      "due_at": "2026-05-20T17:00:00Z",
      "assigner_user_id": null,
      "assignee_user_id": null,
      "ai_confidence": 0.93,
      "ai_raw_text": "Anh Sơn giao cho Minh Đức làm phần thanh toán VNPay, deadline 20 tháng 5, phải xong trước khi demo cho khách"
    },
    {
      "title": "Viết tài liệu API cho module người dùng",
      "description": "Tài liệu Swagger/OpenAPI đầy đủ cho các endpoint authentication và profile",
      "goal": "Frontend team có thể tự tích hợp mà không cần hỏi backend",
      "expected_result": "Tài liệu có ví dụ request/response cho mỗi endpoint",
      "priority": "medium",
      "due_at": null,
      "assigner_user_id": null,
      "assignee_user_id": null,
      "ai_confidence": 0.78,
      "ai_raw_text": "Mình cũng cần ai đó viết docs API cho phần user, để anh em frontend không phải hỏi mãi"
    },
    {
      "title": "Kiểm tra lại hiệu năng trang dashboard",
      "description": "Đo thời gian load và tối ưu các query chậm trên trang dashboard chính",
      "goal": null,
      "expected_result": null,
      "priority": "low",
      "due_at": null,
      "assigner_user_id": null,
      "assignee_user_id": null,
      "ai_confidence": 0.61,
      "ai_raw_text": "Có vẻ dashboard đang chậm, hình như query nhiều quá, nên xem lại"
    }
  ],
  "review_required": true,
  "review_summary": {
    "total": 3,
    "high_confidence": 1,
    "need_review": 2,
    "missing_assigner": 3,
    "missing_assignee": 3
  },
  "review_items": [
    {
      "item": {
        "title": "Tích hợp cổng thanh toán VNPay",
        "description": "Xây dựng module thanh toán online cho luồng checkout, hỗ trợ thẻ nội địa và QR",
        "goal": "Khách hàng có thể thanh toán online mà không cần chuyển khoản thủ công",
        "expected_result": "Module hoạt động ổn định, pass toàn bộ test case thanh toán, không có lỗi timeout",
        "priority": "high",
        "due_at": "2026-05-20T17:00:00Z",
        "assigner_user_id": null,
        "assignee_user_id": null,
        "ai_confidence": 0.93,
        "ai_raw_text": "Anh Sơn giao cho Minh Đức làm phần thanh toán VNPay, deadline 20 tháng 5, phải xong trước khi demo cho khách"
      },
      "status": "high_confidence",
      "issues": ["missing_assigner_user_id", "missing_assignee_user_id"]
    },
    {
      "item": {
        "title": "Viết tài liệu API cho module người dùng",
        "description": "Tài liệu Swagger/OpenAPI đầy đủ cho các endpoint authentication và profile",
        "goal": "Frontend team có thể tự tích hợp mà không cần hỏi backend",
        "expected_result": "Tài liệu có ví dụ request/response cho mỗi endpoint",
        "priority": "medium",
        "due_at": null,
        "assigner_user_id": null,
        "assignee_user_id": null,
        "ai_confidence": 0.78,
        "ai_raw_text": "Mình cũng cần ai đó viết docs API cho phần user, để anh em frontend không phải hỏi mãi"
      },
      "status": "needs_review",
      "issues": ["low_confidence", "missing_assigner_user_id", "missing_assignee_user_id"]
    },
    {
      "item": {
        "title": "Kiểm tra lại hiệu năng trang dashboard",
        "description": "Đo thời gian load và tối ưu các query chậm trên trang dashboard chính",
        "goal": null,
        "expected_result": null,
        "priority": "low",
        "due_at": null,
        "assigner_user_id": null,
        "assignee_user_id": null,
        "ai_confidence": 0.61,
        "ai_raw_text": "Có vẻ dashboard đang chậm, hình như query nhiều quá, nên xem lại"
      },
      "status": "needs_review",
      "issues": ["low_confidence", "missing_assigner_user_id", "missing_assignee_user_id"]
    }
  ]
}
```

**Reading the sample:**

| Item | Confidence | Status | Issues | What to do |
|---|---|---|---|---|
| VNPay payment | 0.93 | `high_confidence` | Missing both user IDs | Fill assigner + assignee, then approve |
| API docs | 0.78 | `needs_review` | Low confidence + missing user IDs | Verify title/content, fill users, approve or reject |
| Dashboard perf | 0.61 | `needs_review` | Low confidence + missing users + no due date | Vague statement — likely reject or heavily edit |

---

## 4.4 Environment & Configuration

### Required — Backend `.env`

```env
# URL of the running AI Platform service
# Leave empty to use direct Python import (dev mode, same venv)
AI_PLATFORM_URL=http://localhost:8001
```

### Required — AI Platform `.env`

```env
# LLM for transcript cleaning, minutes, and task extraction
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini
```

### Optional — AI Platform `.env` (audio pipeline)

```env
# STT model size: tiny | base | small | medium | large-v3
# Larger = more accurate, slower, more RAM
STT_MODEL=base
STT_LANGUAGE=vi
STT_DEVICE=cpu          # cpu | cuda

# HuggingFace token for pyannote diarization model
HF_TOKEN=hf_...
DIARIZATION_MODEL=pyannote/speaker-diarization-3.1
```

### Behavior matrix

| Condition | Effect | extracted_items |
|---|---|---|
| `OPENAI_API_KEY` not set | All LLM stages skipped | `[]` — no extraction |
| `faster-whisper` not installed | STT returns stub text | Extraction runs on stub text |
| `HF_TOKEN` not set | Diarization returns stub speakers | Transcript has `SPEAKER_00`, `SPEAKER_01` only |
| Audio file not found | Pipeline fails at Stage 1 | `[]` — no extraction |
| All configured correctly | Full pipeline runs | Real extraction results |

**The system never crashes regardless of missing config.** Missing packages or keys cause individual stages to fall back to stubs or skip.

---

## 5. Integration Guide for Backend

### Step 1 — Trigger AI Extraction

```
POST /meetings/{meeting_id}/ai-extract-assignments
Body: { "audio_path": "/data/meeting.wav" }
```

Backend calls:

```python
from app.agents.meeting_agent.agent import MeetingAgent
result = MeetingAgent().process(audio_path)
```

Read `result["review_items"]` and persist each item as a `meeting_assignment_draft` row.

### Step 2 — Save Drafts

For each entry in `review_items`:

```
INSERT INTO meeting_assignment_drafts (
    meeting_id, pipeline_job_id, organization_id,
    title, description, goal, expected_result, priority, due_at,
    assigner_user_id,   -- null
    assignee_user_id,   -- null
    ai_confidence, ai_raw_text,
    review_status,      -- "pending_review"
    review_issues,      -- JSONB array of issue codes
    source_payload      -- full item dict for audit
)
```

### Step 3 — Frontend Displays Review

```
GET /meetings/{meeting_id}/assignment-drafts
```

Frontend shows each draft with `review_status`, `ai_confidence`, and `issues` badges.

### Step 4 — User Reviews and Fills Missing Fields

```
PATCH /assignment-drafts/{draft_id}
Body: {
    "assigner_user_id": "uuid",
    "assignee_user_id": "uuid",
    "due_at": "2026-05-20T17:00:00Z"
}
```

User can also reject:
```
PATCH /assignment-drafts/{draft_id}/reject
```

### Step 5 — Approve → Create Real Assignment

```
POST /assignment-drafts/{draft_id}/approve
```

Backend validates `assigner_user_id` and `assignee_user_id` are set, then:

1. `TaskService.create_task()` — `task_type="assignable"`, `source_type="system_assigned"`
2. `AssignmentService.create_assignment()` — `assignment_note = ai_raw_text`
3. Update draft: `review_status = "converted_to_assignment"`, save `created_task_id`, `created_assignment_id`

Response:

```json
{
  "success": true,
  "draft_id": "uuid",
  "created_task_id": "uuid",
  "created_assignment_id": "uuid",
  "review_status": "converted_to_assignment"
}
```

---

## 6. API Endpoints Reference

| Method | Path | Description |
|---|---|---|
| `POST` | `/meetings/{meeting_id}/ai-extract-assignments` | Run AI extraction, save drafts |
| `GET` | `/meetings/{meeting_id}/assignment-drafts` | List all drafts for a meeting |
| `PATCH` | `/assignment-drafts/{draft_id}` | Edit draft (fill missing fields) |
| `PATCH` | `/assignment-drafts/{draft_id}/reject` | Reject draft |
| `POST` | `/assignment-drafts/{draft_id}/approve` | Approve → create task + assignment |

---

## 7. Business Rules

| Rule | Detail |
|---|---|
| **No auto-assignment** | AI output is always draft. No assignment is created without explicit `POST /approve` |
| **assigner_user_id always null** | AI cannot resolve internal UUIDs. UI must let user select |
| **assignee_user_id always null** | Same |
| **ai_confidence is advisory** | High confidence does not skip review. It only affects the `status` label |
| **ai_raw_text is required for audit** | Always persist it. It links the draft back to the exact meeting moment |
| **Rejected drafts are final** | Once `review_status = "rejected"`, the draft cannot be reopened |
| **Approve requires both user IDs** | Backend must enforce 422 if either is null at approve time |

---

## 8. Error Handling

| Scenario | AI Platform behavior | Backend response |
|---|---|---|
| Audio file not found | `pipeline.status = "failed"`, `error = "Audio file not found: ..."` | No drafts created; return pipeline error to client |
| ML models not installed | STT/diarization return stub output; pipeline still completes | Drafts created with stub transcript text |
| `OPENAI_API_KEY` not set | LLM stages skipped; `extracted_items = []` | No drafts created; inform user AI is unavailable |
| LLM returns invalid JSON | `extracted_items = []` | No drafts created |
| Transcript shorter than 20 chars | `extracted_items = []` | No drafts created |
| LLM returns non-list | `extracted_items = []` | No drafts created |

---

## 9. Sequence Diagram

```
User               Backend              AI Platform            DB
 │                    │                      │                  │
 │ Upload audio       │                      │                  │
 │──────────────────► │                      │                  │
 │                    │ MeetingAgent         │                  │
 │                    │ .process(path)       │                  │
 │                    │─────────────────────►│                  │
 │                    │                      │ AudioPreprocess   │
 │                    │                      │ → STT             │
 │                    │                      │ → Diarization     │
 │                    │                      │ → Align           │
 │                    │                      │ → Clean (LLM)     │
 │                    │                      │ → TaskExtract(LLM)│
 │                    │                      │ → ReviewBuilder   │
 │                    │◄─────────────────────│                  │
 │                    │ {pipeline,           │                  │
 │                    │  extracted_items,    │                  │
 │                    │  review_items}       │                  │
 │                    │                      │                  │
 │                    │ INSERT drafts        │                  │
 │                    │─────────────────────────────────────── ►│
 │                    │                      │                  │
 │◄───────────────────│                      │                  │
 │ {meeting_id,       │                      │                  │
 │  review_summary,   │                      │                  │
 │  drafts[]}         │                      │                  │
 │                    │                      │                  │
 │ Review draft       │                      │                  │
 │──────────────────► │                      │                  │
 │ PATCH draft        │ UPDATE draft         │                  │
 │ (fill user IDs)    │─────────────────────────────────────── ►│
 │                    │                      │                  │
 │ Approve draft      │                      │                  │
 │──────────────────► │                      │                  │
 │                    │ TaskService          │                  │
 │                    │ .create_task()       │                  │
 │                    │─────────────────────────────────────── ►│
 │                    │ AssignmentService    │                  │
 │                    │ .create_assignment() │                  │
 │                    │─────────────────────────────────────── ►│
 │                    │ UPDATE draft         │                  │
 │                    │ review_status =      │                  │
 │                    │ "converted_..."      │                  │
 │                    │─────────────────────────────────────── ►│
 │◄───────────────────│                      │                  │
 │ {created_task_id,  │                      │                  │
 │  created_asgn_id}  │                      │                  │
```

---

## 10. Acceptance Criteria

Backend implementation is complete when:

- [ ] `POST /meetings/{id}/ai-extract-assignments` calls AI platform and saves drafts to DB
- [ ] `GET /meetings/{id}/assignment-drafts` returns all drafts with correct fields
- [ ] `PATCH /assignment-drafts/{id}` updates draft fields; rejects if status is `rejected` or `converted_to_assignment`
- [ ] `PATCH /assignment-drafts/{id}/reject` sets `review_status = "rejected"`
- [ ] `POST /assignment-drafts/{id}/approve` validates both user IDs, creates task + assignment, updates draft
- [ ] No assignment is ever created without an explicit approve call
- [ ] `ai_raw_text` is persisted on all drafts
- [ ] Draft cannot be approved if `assigner_user_id` or `assignee_user_id` is null (HTTP 422)

---

## 11. Open Questions — Backend to Confirm

| # | Question | Impact |
|---|---|---|
| 1 | Does assignment creation require a `task_id`? If yes, the approve flow must create a task first. | Approve endpoint design |
| 2 | User name → UUID mapping: does backend resolve this (via name search), or does frontend send the UUID directly? | Whether AI Platform needs a name resolver |
| 3 | `due_at` timezone handling: AI returns ISO 8601 with `Z` suffix (UTC). Does backend store as UTC or convert to org timezone? | Draft schema + migration column type |
| 4 | Should `assigner_user_id` default to `current_user_id` (the person who triggered extraction) if not set by AI? | Approve flow pre-fill logic |
| 5 | Multi-speaker meetings: AI labels speakers as `SPEAKER_00`, `SPEAKER_01`. Does the UI need a step to map speaker labels to real users before review? | UX flow and additional API needed |
