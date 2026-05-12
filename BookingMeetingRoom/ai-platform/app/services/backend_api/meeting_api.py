from typing import Optional
from app.services.backend_api.client import BackendAPIClient
from app.core.logging import logger

# TODO: Replace placeholder endpoints with real backend API catalog once available.


class MeetingAPI:
    """Skeleton client for the business-assistant Meeting endpoints."""

    def __init__(self, client: BackendAPIClient):
        self._client = client

    def create_meeting(self, payload: dict) -> dict:
        logger.info("MeetingAPI: create_meeting")
        return self._client.post("/meetings", json=payload)

    def list_meetings(self, params: Optional[dict] = None) -> dict:
        logger.info("MeetingAPI: list_meetings")
        return self._client.get("/meetings", params=params)

    def upload_recording(self, meeting_id: str, file_path: str) -> dict:
        logger.info(f"MeetingAPI: upload_recording meeting_id={meeting_id}")
        # TODO: Replace mock json file_path with multipart upload when BackendAPIClient supports files.
        return self._client.post(
            f"/meetings/{meeting_id}/recording",
            json={"file_path": file_path},
        )
