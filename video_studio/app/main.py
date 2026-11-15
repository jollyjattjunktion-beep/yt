import asyncio, uuid
from pathlib import Path
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from .config import settings
from .schemas import MatchRequest
from .research import research_match
from .scriptwriter import generate_script
from .renderer import render_video, safe_name

app=FastAPI(title=settings.app_name,version="0.1.0")
JOBS: dict[str,dict] = {}

@app.get("/")
def home():
    return {"name":settings.app_name,"status":"running","docs":"/docs",
            "note":"Configure cricket-data provider and Gemini key for sourced research and AI-written scripts."}

@app.get("/health")
def health(): return {"ok":True}

@app.post("/api/jobs")
async def create_job(req: MatchRequest):
    job_id=uuid.uuid4().hex[:12]
    JOBS[job_id]={"status":"queued","match_name":req.match_name,"result":None,"error":None}
    asyncio.create_task(run_job(job_id,req))
    return {"job_id":job_id,"status":"queued","message":"Video generation started."}

async def run_job(job_id: str, req: MatchRequest):
    try:
        JOBS[job_id]["status"]="researching"
        research=await research_match(req.match_name,req.match_facts)
        JOBS[job_id]["research"]=research
        JOBS[job_id]["status"]="writing_script"
        script=await generate_script(req.match_name,research,req.duration_minutes,req.language)
        JOBS[job_id]["status"]="rendering"
        folder=settings.output_dir/safe_name(req.match_name)/job_id
        result=await render_video(script,req.match_name,folder,req.voice)
        JOBS[job_id].update(status="completed",result=result,research=research)
    except Exception as exc:
        JOBS[job_id].update(status="failed",error=str(exc))

@app.get("/api/jobs/{job_id}")
def get_job(job_id: str):
    if job_id not in JOBS: raise HTTPException(404,"Job not found")
    return JOBS[job_id]

@app.get("/api/jobs/{job_id}/files/{kind}")
def download(job_id: str,kind: str):
    job=JOBS.get(job_id)
    if not job or job["status"]!="completed": raise HTTPException(404,"Output not ready")
    mapping={"landscape":job["result"]["videos"][0],"portrait":job["result"]["videos"][1],
             "audio":job["result"]["audio"],"script":job["result"]["script"],"metadata":job["result"]["metadata"]}
    if kind not in mapping: raise HTTPException(404,"Unknown output")
    path=Path(mapping[kind])
    if not path.is_file(): raise HTTPException(404,"File missing")
    return FileResponse(path,filename=path.name)
