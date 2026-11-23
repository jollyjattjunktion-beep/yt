"""Narration provider adapters with Vexyl -> IndicVoice -> Edge fallback.

Vexyl is an optional separately hosted open-source server. IndicVoice is lazy-loaded
and only runs where its model dependencies and weights are available.
"""
import asyncio, base64, io, json, shutil, subprocess, wave
from pathlib import Path
import httpx
from .config import settings


async def _edge_synthesize(text: str, output: Path, voice: str):
    import edge_tts
    communicate = edge_tts.Communicate(text, voice=voice, rate="+0%", pitch="-2Hz")
    output.parent.mkdir(parents=True, exist_ok=True)
    mp3 = output.with_suffix(".edge.mp3")
    await communicate.save(str(mp3))
    ffmpeg = shutil.which(settings.ffmpeg_bin) or settings.ffmpeg_bin
    proc = subprocess.run([ffmpeg, "-y", "-i", str(mp3), "-ac", "1", "-ar", "24000",
                           "-c:a", "pcm_s16le", str(output)], capture_output=True, text=True)
    mp3.unlink(missing_ok=True)
    if proc.returncode or not output.is_file():
        raise RuntimeError("Edge TTS audio conversion failed: " + proc.stderr[-1000:])
    return output


async def _gemini_synthesize(text: str, output: Path, voice: str | None = None):
    if not settings.gemini_api_key:
        raise RuntimeError("Gemini API key is missing.")
    url=f"https://generativelanguage.googleapis.com/v1beta/models/{settings.gemini_tts_model}:generateContent"
    payload={"contents":[{"role":"user","parts":[{"text":"Speak this Roman Hinglish cricket script verbatim. Natural Indian male sports presenter, clear pronunciation, energetic but not shouting.\\n\\n"+text}]}],
      "generationConfig":{"responseModalities":["AUDIO"],"speechConfig":{"voiceConfig":{"prebuiltVoiceConfig":{"voiceName":voice or settings.gemini_tts_voice}}}}}
    async with httpx.AsyncClient(timeout=240) as client:
        response=await client.post(url,headers={"x-goog-api-key":settings.gemini_api_key},json=payload)
        if response.is_error: raise RuntimeError(f"Gemini TTS error {response.status_code}: {response.text[:500]}")
        data=response.json()
    part=data["candidates"][0]["content"]["parts"][0]["inlineData"]
    raw=base64.b64decode(part["data"])
    import re
    match=re.search(r"rate=(\\d+)",part.get("mimeType",""))
    output.parent.mkdir(parents=True,exist_ok=True)
    with wave.open(str(output),"wb") as wav:
        wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(int(match.group(1)) if match else 24000); wav.writeframes(raw)
    return output


async def _vexyl_synthesize(text: str, output: Path):
    base=(settings.vexyl_tts_url or "").rstrip("/")
    if not base:
        raise RuntimeError("VEXYL_TTS_URL is not configured.")
    headers={"Content-Type":"application/json"}
    if settings.vexyl_tts_api_key: headers["X-API-Key"]=settings.vexyl_tts_api_key
    payload={"text":text[:5000],"lang":settings.vexyl_tts_language,"style":settings.vexyl_tts_style,
             "description":"Gurpreet speaks in a confident, energetic Indian Punjabi cricket commentator style with clear diction."}
    async with httpx.AsyncClient(timeout=240) as client:
        submitted=await client.post(base+"/batch/synthesize",headers=headers,json=payload)
        if submitted.is_error: raise RuntimeError(f"Vexyl submit failed ({submitted.status_code}): {submitted.text[:400]}")
        job=submitted.json().get("job_id")
        if not job: raise RuntimeError("Vexyl did not return a batch job ID.")
        for _ in range(120):
            status=await client.get(base+"/batch/status/"+job,headers=headers)
            if status.is_error: raise RuntimeError(f"Vexyl status failed ({status.status_code}).")
            data=status.json()
            if data.get("status") in {"completed","complete","done"}:
                audio=data.get("audio_b64")
                if not audio:
                    result=await client.get(base+"/batch/result/"+job,headers=headers)
                    if result.is_error: raise RuntimeError("Vexyl result is not ready.")
                    audio=result.json().get("audio_b64")
                if not audio: raise RuntimeError("Vexyl completed without audio.")
                raw=base64.b64decode(audio)
                output.parent.mkdir(parents=True,exist_ok=True)
                output.write_bytes(raw)
                # Vexyl returns WAV; normalize to renderer's mono 24k PCM WAV.
                tmp=output.with_suffix(".vexyl.wav")
                tmp.write_bytes(raw)
                ffmpeg=shutil.which(settings.ffmpeg_bin) or settings.ffmpeg_bin
                proc=subprocess.run([ffmpeg,"-y","-i",str(tmp),"-ac","1","-ar","24000","-c:a","pcm_s16le",str(output)],capture_output=True,text=True)
                tmp.unlink(missing_ok=True)
                if proc.returncode: raise RuntimeError("Vexyl WAV normalization failed: "+proc.stderr[-500:])
                return output
            if data.get("status") in {"failed","error"}: raise RuntimeError("Vexyl synthesis failed: "+str(data)[:400])
            await asyncio.sleep(2)
    raise RuntimeError("Vexyl synthesis timed out.")


async def _indicvoice_synthesize(text: str, output: Path, language: str = "hi"):
    def run():
        try:
            from indicvoice import IndicPipeline
            import soundfile as sf
        except ImportError as exc:
            raise RuntimeError("IndicVoice dependencies are not installed on this worker.") from exc
        pipeline=IndicPipeline(lang_code=language,repo_id="Bindkushal/IndicVoice-82M")
        chunks=[audio for _,_,audio in pipeline(text,voice="am_adam")]
        if not chunks: raise RuntimeError("IndicVoice returned no audio.")
        import numpy as np
        audio=np.concatenate(chunks)
        output.parent.mkdir(parents=True,exist_ok=True)
        sf.write(str(output),audio,24000)
        return output
    return await asyncio.to_thread(run)


async def synthesize(text: str, output: Path, provider: str | None = None, voice: str | None = None):
    selected=(provider or settings.tts_provider).lower()
    if selected == "edge": return await _edge_synthesize(text,output,voice or settings.edge_tts_voice)
    if selected == "gemini": return await _gemini_synthesize(text,output,voice)
    if selected in {"both","auto","vexyl","indicvoice"}:
        order=(settings.tts_fallback_order or "vexyl,indicvoice,edge").split(",") if selected in {"both","auto"} else [selected]
        failures=[]
        for engine in [x.strip().lower() for x in order if x.strip()]:
            try:
                if engine=="vexyl": return await _vexyl_synthesize(text,output)
                if engine=="indicvoice": return await _indicvoice_synthesize(text,output,"pa" if settings.vexyl_tts_language=="pa-IN" else "hi")
                if engine=="edge": return await _edge_synthesize(text,output,voice or settings.edge_tts_voice)
            except Exception as exc:
                failures.append(engine+": "+str(exc)[:250])
        raise RuntimeError("All narration providers failed: "+" | ".join(failures))
    raise RuntimeError(f"Unknown TTS provider: {selected}. Choose edge, gemini or both.")
