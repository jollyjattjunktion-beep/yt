from pathlib import Path
from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore", case_sensitive=False)
    app_name: str = "Preet Sports AI Cricket Production Studio"
    output_dir: Path = Path("./output")
    data_dir: Path = Path("./data")
    gemini_api_key: str = ""
    gemini_model: str = "gemini-3.8-flash"
    # Gemini is used for text analysis/scriptwriting within the user's available free quota.
    colab_image_worker_url: str = ""
    colab_worker_token: str = ""
    colab_image_timeout_seconds: int = 300
    tts_fallback_order: str = "indicvoice,edge"
    indicvoice_enabled: bool = True
    tts_provider: str = "both"
    edge_tts_voice: str = "hi-IN-MadhurNeural"
    cricket_data_api_url: str = ""
    cricket_data_api_key: str = ""
    ffmpeg_bin: str = "ffmpeg"
    youtube_client_secrets_file: Path = Path("./client_secret.json")
    youtube_token_file: Path = Path("./youtube_token.json")

    @field_validator("gemini_model", mode="before")
    @classmethod
    def migrate_legacy_gemini_model(cls, value):
        legacy = {"gemini-2.5-flash", "models/gemini-2.5-flash"}
        if isinstance(value, str) and value.strip() in legacy:
            return "gemini-3.8-flash"
        return value

settings = Settings()
settings.output_dir.mkdir(parents=True, exist_ok=True)
settings.data_dir.mkdir(parents=True, exist_ok=True)

def reload_settings():
    fresh = Settings()
    for key, value in fresh.__dict__.items():
        setattr(settings, key, value)
