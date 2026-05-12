from app.services.backend_api.meeting_api import MeetingAPI


def create_meeting(api: MeetingAPI, payload: dict) -> dict:
    return api.create_meeting(payload or {"title": "Meeting mới"})


def list_meetings(api: MeetingAPI) -> dict:
    return api.list_meetings()
