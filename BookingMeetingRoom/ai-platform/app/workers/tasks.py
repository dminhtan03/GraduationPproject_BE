from app.pipelines.meeting_pipeline import MeetingPipeline
from app.schemas.meeting import MeetingOutput
from app.core.logging import logger


async def process_meeting_task(audio_path: str, meeting_title: str, language: str) -> dict:
    """
    Async task wrapper for MeetingPipeline.run().
    Runs the synchronous pipeline in a thread pool executor.
    """
    import asyncio

    loop = asyncio.get_event_loop()
    pipeline = MeetingPipeline()

    logger.info(f"Starting meeting task: audio={audio_path}")
    result: MeetingOutput = await loop.run_in_executor(
        None,
        lambda: pipeline.run(audio_path=audio_path, meeting_title=meeting_title, language=language),
    )

    return result.model_dump(mode="json")
