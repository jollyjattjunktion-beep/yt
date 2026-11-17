"""Selectable narration providers. All providers return a standards-compliant WAV."""
import asyncio, base64, io, wave
from pathlib import Path
import httpx
import edge_tts
from .config import settings

async def _edge_synthesize(text: str, output: Path, voice: str):
    communicate = edge_tts.Communicate(text, voice=voice, rate="+0%", pitch="-2Hz")
    output.parent.mkdir(parents=True, exist_ok=True)
    # Edge TTS returns MP3; convert with the installed FFmpeg to PCM WAV.
    mp3 = output.with_suffix(".edge.mp3")
    await communicate.save(str(mp3))
    import shutil, subprocess
    ffmpeg = shutil.which(settings.ffmpeg_bin) or settings.ffmpeg_bin
    proc = subprocess.run([ffmpeg, "-y", "-i", str(mp3), "-ac", "1", "-ar", "24000", "-c:a", "pcm_s16le", str(output)], capture_output=True, text=True)
    try: mp3.unlink(missing_ok=True)
    except OSError: pass
    if proc.returncode or not output.is_file():
        raise RuntimeError("Edge TTS audio conversion failed: " + proc.stderr[-1200:])
    return output

async def _gemini_synthesize(text: str, output: Path, voice: str | None = None):
    if not settings.gemini_api_key:
        raise RuntimeError("Gemini API key is missing. Add it in Studio Settings.")
    model=settings.gemini_tts_model
    url=f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    voice_name=voice or settings.gemini_tts_voice
    payload={
      "contents":[{"role":"user","parts":[{"text":"Speak this Roman Hinglish cricket pre-analysis script verbatim. Natural Indian male sports presenter, warm confident delivery, clear pronunciation, energetic but not shouting. Preserve names, numbers and wording.\n\n"+text}]}],
      "generationConfig":{"responseModalities":["AUDIO"],"speechConfig":{"voiceConfig":{"prebuiltVoiceConfig":{"voiceName":voice_name}}}}
    }
    async with httpx.AsyncClient(timeout=240) as client:
        response=await client.post(url,headers={"x-goog-api-key":settings.gemini_api_key},json=payload)
        if response.is_error: raise RuntimeError(f"Gemini TTS error {response.status_code}: {response.text[:1200]}")
        data=response.json()
    try:
        part=data["candidates"][0]["content"]["parts"][0]["inlineData"]
        raw=base64.b64decode(part["data"])
        mime=part.get("mimeType","audio/L16;rate=24000")
    except (KeyError,IndexError,TypeError):
        raise RuntimeError("Gemini TTS returned no audio data. Check model access and voice.")
    # Gemini speech responses are raw PCM (typically 24 kHz, mono, 16-bit), not a WAV container.
    import re
    match=re.search(r"rate=(\d+)",mime)
    sample_rate=int(match.group(1)) if match else 24000
    output.parent.mkdir(parents=True,exist_ok=True)
    with wave.open(str(output),"wb") as wav:
        wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(sample_rate); wav.writeframes(raw)
    return output

async def synthesize(text: str, output: Path, provider: str | None = None, voice: str | None = None):
    selected=(provider or settings.tts_provider).lower()
    if selected == "edge":
        return await _edge_synthesize(text, output, voice or settings.edge_tts_voice)
    if selected == "gemini":
        return await _gemini_synthesize(text, output, voice)
    raise RuntimeError(f"Unknown TTS provider: {selected}. Choose edge or gemini.")
