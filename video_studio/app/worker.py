"""Standalone local worker. Run with: python -m app.worker"""
import asyncio, traceback
from .job_store import next_job, update_job, recover_interrupted
from .schemas import MatchRequest
from .research import research_match
from .scriptwriter import generate_script
from .renderer import render_video, safe_name
from .scene_planner import plan_scenes
from .config import settings, reload_settings

async def process(job):
    job_id=job["id"]
    try:
        reload_settings()
        req=MatchRequest.model_validate_json(job["payload"])
        update_job(job_id,status="running",stage="Researching match",progress=8)
        research=await research_match(req.match_name,req.match_facts)
        update_job(job_id,stage=f"Writing {req.duration_minutes}-minute Roman Hinglish script",progress=20)
        script=await generate_script(req.match_name,research,req.duration_minutes,req.language)
        folder=settings.output_dir/safe_name(req.match_name)/job_id
        folder.mkdir(parents=True,exist_ok=True)
        (folder/"script.txt").write_text(script,encoding="utf-8")
        update_job(job_id,stage="Planning sports-broadcast scenes with Gemini",progress=32)
        # Scene timing is derived from actual audio duration after TTS; plan uses an estimate.
        scenes=await plan_scenes(req.match_name,script,research,req.duration_minutes*60)
        update_job(job_id,stage="Generating narration WAV and assembling sports graphics",progress=42)
        result=await render_video(script,req.match_name,folder,req.voice,research=research,scenes=scenes,tts_provider=req.tts_provider)
        update_job(job_id,status="completed",stage="Production complete · outputs validated",progress=100,result={"research":research,"scenes":scenes,**result})
    except Exception as exc:
        update_job(job_id,status="failed",stage="Production failed",error=str(exc))
        traceback.print_exc()

async def main():
    recover_interrupted()
    print("Preet Sports local worker ready. Waiting for jobs...")
    while True:
        job=next_job()
        if job: await process(job)
        else: await asyncio.sleep(2)

if __name__=="__main__": asyncio.run(main())
