from pydantic import BaseModel
from typing import Optional, Union


class BackendAPIResponse(BaseModel):
    success: bool
    status: str = "mock"
    data: Optional[Union[dict, list]] = None
    error: Optional[str] = None
