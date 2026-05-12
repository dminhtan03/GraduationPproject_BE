from typing import Optional
import httpx
from app.core.logging import logger

BACKEND_URL = "http://localhost:8000"
TIMEOUT = 30.0


class BackendAPIClient:
    """HTTP client for the business-assistant backend."""

    def __init__(self, base_url: str = BACKEND_URL, access_token: Optional[str] = None):
        self.base_url = base_url
        self._token = access_token

    def _headers(self) -> dict:
        h = {"Content-Type": "application/json"}
        if self._token:
            h["Authorization"] = f"Bearer {self._token}"
        return h

    def get(self, path: str, params: Optional[dict] = None) -> dict:
        try:
            with httpx.Client(timeout=TIMEOUT) as c:
                r = c.get(f"{self.base_url}{path}", params=params, headers=self._headers())
                r.raise_for_status()
                return r.json()
        except Exception as e:
            logger.error(f"BackendAPIClient GET {path} failed: {e}")
            return {"error": str(e)}

    def post(self, path: str, json: Optional[dict] = None) -> dict:
        try:
            with httpx.Client(timeout=TIMEOUT) as c:
                r = c.post(f"{self.base_url}{path}", json=json, headers=self._headers())
                r.raise_for_status()
                if r.status_code == 204:
                    return {"success": True}
                return r.json()
        except Exception as e:
            logger.error(f"BackendAPIClient POST {path} failed: {e}")
            return {"error": str(e)}

    def patch(self, path: str, json: Optional[dict] = None) -> dict:
        try:
            with httpx.Client(timeout=TIMEOUT) as c:
                r = c.patch(f"{self.base_url}{path}", json=json, headers=self._headers())
                r.raise_for_status()
                return r.json()
        except Exception as e:
            logger.error(f"BackendAPIClient PATCH {path} failed: {e}")
            return {"error": str(e)}

    def delete(self, path: str) -> dict:
        try:
            with httpx.Client(timeout=TIMEOUT) as c:
                r = c.delete(f"{self.base_url}{path}", headers=self._headers())
                r.raise_for_status()
                return {"success": True}
        except Exception as e:
            logger.error(f"BackendAPIClient DELETE {path} failed: {e}")
            return {"error": str(e)}
