from typing import Optional
from datetime import datetime
from pydantic import BaseModel, ConfigDict

class FeedbackCreate(BaseModel):
    session_id: str
    author: Optional[str] = "Clinician"
    message: str

class FeedbackResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    session_id: str
    author: str
    message: str
    created_at: datetime
