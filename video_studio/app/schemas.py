from pydantic import BaseModel, Field
from typing import Any

class MatchRequest(BaseModel):
    match_name: str = Field(min_length=3, max_length=180)
    format: str = "ODI"
    duration_minutes: int = Field(default=10, ge=8, le=12)
    language: str = "Roman Hinglish"
    voice: str | None = "edge:hi-IN-MadhurNeural"
    tts_provider: str = "edge"
    match_facts: dict[str, Any] = Field(default_factory=dict)
    publish_to_youtube: bool = False
    privacy_status: str = "private"

class JobResponse(BaseModel):
    job_id: str
    status: str
    message: str
