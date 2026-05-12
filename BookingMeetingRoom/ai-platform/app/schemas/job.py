from pydantic import BaseModel, Field
from typing import Optional, Any
from datetime import datetime
from enum import Enum


class JobStatus(str, Enum):
    pending = "pending"
    running = "running"
    completed = "completed"
    failed = "failed"


class JobResponse(BaseModel):
    job_id: str
    status: JobStatus
    created_at: datetime
    updated_at: datetime
    result: Optional[Any] = None
    error: Optional[str] = None
    progress: Optional[float] = Field(None, ge=0.0, le=1.0, description="Progress 0.0 to 1.0")
