from fastapi import APIRouter, HTTPException
from datetime import datetime

from app.schemas.job import JobResponse, JobStatus
from app.core.logging import logger

router = APIRouter(prefix="/jobs", tags=["jobs"])


@router.get("/{job_id}", response_model=JobResponse)
async def get_job(job_id: str) -> JobResponse:
    """
    Retrieve the status and result of a processing job.
    Currently returns a mock response. Will be wired to the job store.
    """
    logger.info(f"Fetching job status: job_id={job_id}")

    # TODO: Look up real job from in-memory store or Redis
    now = datetime.utcnow()
    return JobResponse(
        job_id=job_id,
        status=JobStatus.completed,
        created_at=now,
        updated_at=now,
        result={"message": "Mock job result — real implementation pending"},
        error=None,
        progress=1.0,
    )
