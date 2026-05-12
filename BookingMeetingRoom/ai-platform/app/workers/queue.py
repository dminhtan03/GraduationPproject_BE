import asyncio
import uuid
from datetime import datetime
from typing import Any, Callable, Coroutine
from app.schemas.job import JobStatus, JobResponse
from app.core.logging import logger


class InMemoryJobQueue:
    """
    Simple in-memory job queue for development.
    Replace with Celery + Redis for production.
    """

    def __init__(self, max_concurrent: int = 2):
        self._jobs: dict[str, JobResponse] = {}
        self._semaphore = asyncio.Semaphore(max_concurrent)

    def get(self, job_id: str) -> JobResponse | None:
        return self._jobs.get(job_id)

    async def submit(
        self,
        task_fn: Callable[..., Coroutine],
        **kwargs: Any,
    ) -> str:
        job_id = str(uuid.uuid4())
        now = datetime.utcnow()
        self._jobs[job_id] = JobResponse(
            job_id=job_id,
            status=JobStatus.pending,
            created_at=now,
            updated_at=now,
        )
        asyncio.create_task(self._run(job_id, task_fn, **kwargs))
        return job_id

    async def _run(self, job_id: str, task_fn: Callable, **kwargs: Any) -> None:
        async with self._semaphore:
            self._update(job_id, status=JobStatus.running, progress=0.0)
            try:
                result = await task_fn(**kwargs)
                self._update(job_id, status=JobStatus.completed, result=result, progress=1.0)
                logger.info(f"Job {job_id} completed")
            except Exception as exc:
                self._update(job_id, status=JobStatus.failed, error=str(exc))
                logger.error(f"Job {job_id} failed: {exc}")

    def _update(self, job_id: str, **fields: Any) -> None:
        job = self._jobs[job_id]
        updated = job.model_copy(update={**fields, "updated_at": datetime.utcnow()})
        self._jobs[job_id] = updated


job_queue = InMemoryJobQueue()
