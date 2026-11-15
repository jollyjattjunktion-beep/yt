from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore", case_sensitive=False)
    app_name: str = "Preet Sports AI Cricket Production Studio"
    output_dir: Path = Path("./output")
    data_dir: Path = Path("./data")
    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.5-flash"
    gemini_tts_model: str = "gemini-3.8-flash-lite-tts"
    gemini_tts_voice: str = "Charon"
    cricket_data_api_url: str = ""
    cricket_data_api_key: str = ""
    tts_voice: str = "en-IN-PrabhatNeural"
    tts_rate: str = "+0%"
    ffmpeg_bin: str = "ffmpeg"
    youtube_client_secrets_file: Path = Path("./client_secret.json")
    youtube_token_file: Path = Path("./youtube_token.json")

settings = Settings()
settings.output_dir.mkdir(parents=True, exist_ok=True)
settings.data_dir.mkdir(parents=True, exist_ok=True)
