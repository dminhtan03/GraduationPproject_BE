from typing import Optional
from app.services.backend_api.client import BackendAPIClient
from app.core.logging import logger


class TaskAPI:
    def __init__(self, client: BackendAPIClient):
        self._client = client

    def create_task(self, payload: dict) -> dict:
        logger.info("TaskAPI: create_task")
        return self._client.post("/tasks", json=payload)

    def list_assigned_to_me(self) -> dict:
        logger.info("TaskAPI: list_assigned_to_me")
        return self._client.get("/tasks/assigned-to-me")

    def list_my_personal_tasks(self) -> dict:
        logger.info("TaskAPI: list_my_personal_tasks")
        return self._client.get("/tasks", params={"task_type": "personal"})

    def get_today_summary(self) -> dict:
        logger.info("TaskAPI: get_today_summary")
        return self._client.get("/summary/today")

    def get_task(self, task_id: str) -> dict:
        logger.info(f"TaskAPI: get_task task_id={task_id}")
        return self._client.get(f"/tasks/{task_id}")

    def update_task(self, task_id: str, payload: dict) -> dict:
        logger.info(f"TaskAPI: update_task task_id={task_id}")
        return self._client.patch(f"/tasks/{task_id}", json=payload)

    def cancel_task(self, task_id: str) -> dict:
        logger.info(f"TaskAPI: cancel_task task_id={task_id}")
        return self._client.patch(f"/tasks/{task_id}/cancel")

    def delete_task(self, task_id: str) -> dict:
        logger.info(f"TaskAPI: delete_task task_id={task_id}")
        return self._client.delete(f"/tasks/{task_id}")

    def assign_task(self, task_id: str, payload: dict) -> dict:
        logger.info(f"TaskAPI: assign_task task_id={task_id}")
        return self._client.post(f"/tasks/{task_id}/assignments", json=payload)

    def add_supporter(self, task_id: str, user_id: str) -> dict:
        logger.info(f"TaskAPI: add_supporter task_id={task_id} user_id={user_id}")
        return self._client.post(f"/tasks/{task_id}/supporters", json={"user_id": user_id})
