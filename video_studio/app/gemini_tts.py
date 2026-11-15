"""Gemini TTS adapter. Audio is saved as WAV; FFmpeg can convert it for muxing."""
import base64, httpx
from pathlib import Path
from .config import settings

async def synthesize(text: str, output: Path):
    if not settings.gemini_api_key:
        raise RuntimeError("Gemini API key is missing. Open Settings in the studio and add your key.")
    model=settings.gemini_tts_model
    url=f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    payload={
      "contents":[{"role":"user","parts":[{"text":"Read this Roman Hinglish cricket presenter script verbatim. Use a confident, warm, energetic Indian English/Hinglish male sports-presenter delivery, clear pronunciation, natural pauses and lively but controlled match commentary rhythm. Do not translate or paraphrase.\n\n"+text}]}],
      "generationConfig":{"responseModalities":["AUDIO"],"speechConfig":{"voiceConfig":{"prebuiltVoiceConfig":{"voiceName":settings.gemini_tts_voice}}}}
    }
    async with httpx.AsyncClient(timeout=240) as client:
        response=await client.post(url,headers={"x-goog-api-key":settings.gemini_api_key},json=payload)
        if response.is_error:
            raise RuntimeError(f"Gemini TTS error {response.status_code}: {response.text[:1200]}")
        data=response.json()
    try:
        audio=data["candidates"][0]["content"]["parts"][0]["inlineData"]["data"]
    except (KeyError,IndexError,TypeError):
        raise RuntimeError("Gemini TTS returned no audio data. Check the selected TTS model and API access.")
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_bytes(base64.b64decode(audio))
    return output
