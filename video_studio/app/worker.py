"""Standalone local worker. Run with: python -m app.worker"""
import asyncio, json, time, traceback
from .job_store import next_job, update_job, recover_interrupted
from .schemas import MatchRequest
from .research import research_match
from .scriptwriter import generate_script
from .renderer import render_video, safe_name
from .config import settings

async def process(job):
    job_id=job["id"]
    try:
        req=MatchRequest.model_validate_json(job["payload"])
        update_job(job_id,status="running",stage="Researching match",progress=8)
        research=await research_match(req.match_name,req.match_facts)
        update_job(job_id,stage="Writing Roman Hinglish script",progress=22)
        script=await generate_script(req.match_name,research,req.duration_minutes,req.language)
        update_job(job_id,stage="Generating Gemini voice and video scenes",progress=38)
        folder=settings.output_dir/safe_name(req.match_name)/job_id
        result=await render_video(script,req.match_name,folder,req.voice)
        update_job(job_id,status="completed",stage="Production complete",progress=100,result={"research":research,**result})
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
