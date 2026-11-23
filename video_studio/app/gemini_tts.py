"""Free narration adapters: local/open-source IndicVoice, then Edge TTS fallback.

No paid TTS API is called by this module. IndicVoice requires its model dependencies
and weights to be installed on the worker; Edge TTS is the lightweight fallback.
"""
import asyncio
import shutil
import subprocess
from pathlib import Path
from .config import settings


async def _edge_synthesize(text: str, output: Path, voice: str):
    import edge_tts
    communicate = edge_tts.Communicate(text, voice=voice, rate="+0%", pitch="-2Hz")
    output.parent.mkdir(parents=True, exist_ok=True)
    mp3 = output.with_suffix(".edge.mp3")
    await communicate.save(str(mp3))
    ffmpeg = shutil.which(settings.ffmpeg_bin) or settings.ffmpeg_bin
    proc = subprocess.run(
        [ffmpeg, "-y", "-i", str(mp3), "-ac", "1", "-ar", "24000", "-c:a", "pcm_s16le", str(output)],
        capture_output=True, text=True,
    )
    mp3.unlink(missing_ok=True)
    if proc.returncode or not output.is_file():
        raise RuntimeError("Edge TTS audio conversion failed: " + proc.stderr[-1000:])
    return output


async def _indicvoice_synthesize(text: str, output: Path, language: str = "hi"):
    def run():
        try:
            from indicvoice import IndicPipeline
            import numpy as np
            import soundfile as sf
        except ImportError as exc:
            raise RuntimeError("IndicVoice is not installed; using the free Edge TTS fallback.") from exc
        pipeline = IndicPipeline(lang_code=language, repo_id="Bindkushal/IndicVoice-82M")
        chunks = [audio for _, _, audio in pipeline(text, voice="am_adam")]
        if not chunks:
            raise RuntimeError("IndicVoice returned no audio.")
        audio = np.concatenate(chunks)
        output.parent.mkdir(parents=True, exist_ok=True)
        sf.write(str(output), audio, 24000)
        return output
    return await asyncio.to_thread(run)


async def synthesize(text: str, output: Path, provider: str | None = None, voice: str | None = None):
    selected = (provider or settings.tts_provider).lower()
    if selected in {"both", "auto", "indicvoice"}:
        failures = []
        order = (settings.tts_fallback_order or "indicvoice,edge").split(",") if selected in {"both", "auto"} else ["indicvoice"]
        for engine in [item.strip().lower() for item in order if item.strip()]:
            try:
                if engine == "indicvoice":
                    return await _indicvoice_synthesize(text, output, "hi")
                if engine == "edge":
                    return await _edge_synthesize(text, output, voice or settings.edge_tts_voice)
            except Exception as exc:
                failures.append(engine + ": " + str(exc)[:250])
        raise RuntimeError("All free narration providers failed: " + " | ".join(failures))
    if selected == "edge":
        return await _edge_synthesize(text, output, voice or settings.edge_tts_voice)
    raise RuntimeError(f"Unsupported free TTS provider: {selected}. Choose both, indicvoice or edge.")
