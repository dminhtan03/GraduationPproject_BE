from app.services.backend_api.task_api import TaskAPI


def create_task(api: TaskAPI, text: str) -> dict:
    payload = {"title": text, "task_type": "personal", "priority": "medium"}
    return api.create_task(payload)


def update_task(api: TaskAPI, text: str) -> dict:
    return api.update_task("123", {"title": text})


def delete_task(api: TaskAPI) -> dict:
    return api.delete_task("123")


def cancel_task(api: TaskAPI) -> dict:
    return api.cancel_task("123")


def get_task(api: TaskAPI) -> dict:
    return api.get_task("123")
