from typing import Optional
import httpx
from app.core.logging import logger

# Java Spring Boot backend at :8080, all endpoints prefixed with /api/v1
BACKEND_URL = "http://localhost:8080/api/v1"
TIMEOUT = 30.0


def _unwrap(raw: dict | list) -> dict | list:
    """
    Java Response<T> wraps payload as { "data": <T>, "meta": {...} }.
    Unwrap so callers always receive the actual payload.
    If the response is already a list or has no "data" key, return as-is.
    """
    if isinstance(raw, list):
        return raw
    if isinstance(raw, dict) and "data" in raw:
        return raw["data"]
    return raw


class BackendAPIClient:
    """HTTP client for the Java Spring Boot business backend."""

    def __init__(self, base_url: str = BACKEND_URL, access_token: Optional[str] = None):
        self.base_url = base_url
        self._token = access_token

    def _headers(self) -> dict:
        h = {"Content-Type": "application/json"}
        if self._token:
            h["Authorization"] = f"Bearer {self._token}"
        return h

    def get(self, path: str, params: Optional[dict] = None) -> dict | list:
        try:
            with httpx.Client(timeout=TIMEOUT) as c:
                r = c.get(f"{self.base_url}{path}", params=params, headers=self._headers())
                r.raise_for_status()
                return _unwrap(r.json())
        except Exception as e:
            logger.error(f"BackendAPIClient GET {path} failed: {e}")
            return {"error": str(e)}

    def post(self, path: str, json: Optional[dict] = None) -> dict | list:
        try:
            with httpx.Client(timeout=TIMEOUT) as c:
                r = c.post(f"{self.base_url}{path}", json=json, headers=self._headers())
                r.raise_for_status()
                if r.status_code == 204:
                    return {"success": True}
                return _unwrap(r.json())
        except Exception as e:
            logger.error(f"BackendAPIClient POST {path} failed: {e}")
            return {"error": str(e)}

    def patch(self, path: str, json: Optional[dict] = None) -> dict | list:
        try:
            with httpx.Client(timeout=TIMEOUT) as c:
                r = c.patch(f"{self.base_url}{path}", json=json, headers=self._headers())
                r.raise_for_status()
                return _unwrap(r.json())
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

    def find_users_by_name(self, name: str) -> list[dict]:
        """Search user in org by name (substring + token match)."""
        result = self.get("/users")
        if not isinstance(result, list):
            return []
        name_lower = name.lower().strip()
        tokens = [t for t in name_lower.split() if len(t) >= 2]
        matches = []
        seen: set[str] = set()
        for u in result:
            uid = u.get("id", "")
            if uid in seen:
                continue
            # Java returns fullName (camelCase) — support both
            full_name = (u.get("fullName") or u.get("full_name") or "").lower()
            if name_lower in full_name or full_name in name_lower:
                matches.append(u)
                seen.add(uid)
            elif any(t in full_name for t in tokens):
                matches.append(u)
                seen.add(uid)
        return matches
