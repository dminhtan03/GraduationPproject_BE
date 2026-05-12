from app.services.backend_api.task_api import TaskAPI


def assign_task(api: TaskAPI, text: str) -> dict:
    payload = {"assignee_user_id": "user_1", "assignment_note": text}
    return api.assign_task("123", payload)


def cancel_assignment(api: TaskAPI) -> dict:
    return api.cancel_assignment("123", "user_1")
