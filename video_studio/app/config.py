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
    gemini_image_model: str = "gemini-nano-banana-2.1"
    gemini_tts_model: str = "gemini-2.5-flash-preview-tts"
    gemini_tts_voice: str = "Charon"
    tts_fallback_order: str = "vexyl,indicvoice,edge"
    vexyl_tts_url: str = ""
    vexyl_tts_api_key: str = ""
    vexyl_tts_language: str = "hi-IN"
    vexyl_tts_style: str = "formal"
    indicvoice_enabled: bool = True
    tts_provider: str = "edge"
    edge_tts_voice: str = "hi-IN-MadhurNeural"
    cricket_data_api_url: str = ""
    cricket_data_api_key: str = ""
    ffmpeg_bin: str = "ffmpeg"
    youtube_client_secrets_file: Path = Path("./client_secret.json")
    youtube_token_file: Path = Path("./youtube_token.json")

    @field_validator("gemini_model", mode="before")
    @classmethod
    def migrate_legacy_gemini_model(cls, value):
        # Older local .env files and saved dashboard settings may still contain this model.
        legacy = {"gemini-2.5-flash", "models/gemini-2.5-flash"}
        if isinstance(value, str) and value.strip() in legacy:
            return "gemini-3.8-flash"
        return value

settings = Settings()
settings.output_dir.mkdir(parents=True, exist_ok=True)
settings.data_dir.mkdir(parents=True, exist_ok=True)

def reload_settings():
    fresh=Settings()
    for key,value in fresh.__dict__.items():
        setattr(settings,key,value)
