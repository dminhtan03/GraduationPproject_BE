from app.services.backend_api.meeting_api import MeetingAPI
from app.pipelines.meeting_pipeline import MeetingPipeline


def upload_recording(api: MeetingAPI, meeting_id: str, file_path: str) -> dict:
    return api.upload_recording(meeting_id, file_path)


def process_recording(pipeline: MeetingPipeline, file_path: str):
    return pipeline.run(file_path)
