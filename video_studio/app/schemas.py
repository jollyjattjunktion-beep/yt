from pydantic import BaseModel, Field, field_validator
from typing import Any

class MatchRequest(BaseModel):
    match_name: str = Field(min_length=3, max_length=180)
    format: str = "ODI"
    duration_minutes: int = Field(default=10, ge=8, le=12)
    language: str = "Roman Hinglish"
    voice: str | None = "edge:hi-IN-MadhurNeural"
    tts_provider: str = "edge"
    match_facts: dict[str, Any] = Field(default_factory=dict)
    source_url: str | None = None
    screenshot_paths: list[str] = Field(default_factory=list)
    publish_to_youtube: bool = False
    privacy_status: str = "private"

    @field_validator("source_url")
    @classmethod
    def validate_source_url(cls, value):
        if value is None or not value.strip():
            return None
        from urllib.parse import urlparse
        parsed = urlparse(value.strip())
        allowed = {"crex.com", "www.crex.com", "cricbuzz.com", "www.cricbuzz.com",
                   "icc-cricket.com", "www.icc-cricket.com"}
        if parsed.scheme != "https" or parsed.hostname not in allowed:
            raise ValueError("Use an HTTPS match page from CREX, Cricbuzz or ICC.")
        return value.strip()

class JobResponse(BaseModel):
    job_id: str
    status: str
    message: str
