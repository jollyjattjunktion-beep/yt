import uuid, asyncio, re
from contextlib import asynccontextmanager
from urllib.parse import urlparse, unquote
from pathlib import Path
from fastapi import FastAPI, HTTPException, UploadFile, File, Form
from fastapi.responses import HTMLResponse, FileResponse
from pydantic import BaseModel, field_validator
from .config import settings
from .schemas import MatchRequest
from .job_store import create_job, get_job, connect

@asynccontextmanager
async def lifespan(app: FastAPI):
    from .worker import main as worker_main
    task = asyncio.create_task(worker_main(), name="preet-production-worker")
    app.state.worker_task = task
    try:
        yield
    finally:
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass

app=FastAPI(title=settings.app_name,version="0.4.0",lifespan=lifespan)

@app.get("/",response_class=HTMLResponse)
def home():
    return Path(__file__).with_name("dashboard.html").read_text(encoding="utf-8")

@app.get("/health")
def health():
    with connect() as db: db.execute("SELECT 1").fetchone()
    task = getattr(app.state, "worker_task", None)
    return {"ok":True,"mode":"render","job_store":"sqlite","worker_running":bool(task and not task.done())}

@app.get("/api/settings")
def read_settings():
    return {"gemini_api_key_set":bool(settings.gemini_api_key),
      "gemini_api_key_masked":("••••••••"+settings.gemini_api_key[-4:]) if settings.gemini_api_key else "",
      "gemini_model":settings.gemini_model,"gemini_tts_model":settings.gemini_tts_model,
      "gemini_tts_voice":settings.gemini_tts_voice,"tts_provider":settings.tts_provider,
      "edge_tts_voice":settings.edge_tts_voice,"cricket_data_api_url":settings.cricket_data_api_url}

class SettingsRequest(BaseModel):
    gemini_api_key:str=""
    gemini_model:str="gemini-3.8-flash"
    gemini_tts_model:str="gemini-2.5-flash-preview-tts"
    gemini_tts_voice:str="Charon"
    tts_provider:str="edge"
    edge_tts_voice:str="hi-IN-MadhurNeural"
    cricket_data_api_url:str=""
    cricket_data_api_key:str=""

    @field_validator("gemini_model", mode="before")
    @classmethod
    def migrate_legacy_gemini_model(cls, value):
        legacy = {"gemini-2.5-flash", "models/gemini-2.5-flash"}
        if isinstance(value, str) and value.strip() in legacy:
            return "gemini-3.8-flash"
        return value

@app.post("/api/settings")
def save_settings(req:SettingsRequest):
    env_path=Path(".env"); existing={}
    if env_path.exists():
        for line in env_path.read_text(encoding="utf-8").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                k,v=line.split("=",1); existing[k]=v
    values={"GEMINI_API_KEY":req.gemini_api_key.strip(),"GEMINI_MODEL":req.gemini_model.strip(),
      "GEMINI_TTS_MODEL":req.gemini_tts_model.strip(),"GEMINI_TTS_VOICE":req.gemini_tts_voice.strip(),
      "TTS_PROVIDER":req.tts_provider.strip(),"EDGE_TTS_VOICE":req.edge_tts_voice.strip(),
      "CRICKET_DATA_API_URL":req.cricket_data_api_url.strip(),"CRICKET_DATA_API_KEY":req.cricket_data_api_key.strip()}
    for k,v in values.items():
        if v or k not in existing: existing[k]=v
    env_path.write_text("\n".join(f"{k}={v}" for k,v in existing.items())+"\n",encoding="utf-8")
    for attr,key in [("gemini_api_key","GEMINI_API_KEY"),("gemini_model","GEMINI_MODEL"),
      ("gemini_tts_model","GEMINI_TTS_MODEL"),("gemini_tts_voice","GEMINI_TTS_VOICE"),
      ("tts_provider","TTS_PROVIDER"),("edge_tts_voice","EDGE_TTS_VOICE"),
      ("cricket_data_api_url","CRICKET_DATA_API_URL"),("cricket_data_api_key","CRICKET_DATA_API_KEY")]:
        setattr(settings,attr,existing.get(key,""))
    # Re-read through Settings so legacy model IDs are normalized consistently.
    from .config import reload_settings
    reload_settings()
    return {"saved":True,"gemini_api_key_set":bool(settings.gemini_api_key),"gemini_model":settings.gemini_model}

@app.post("/api/jobs")
def create(req:MatchRequest):
    job_id=uuid.uuid4().hex[:12]; create_job(job_id,req)
    return {"job_id":job_id,"status":"queued","message":"Queued for local production worker."}

@app.post("/api/jobs/upload")
async def create_mobile_job(
    match_url: str = Form(...),
    match_name: str = Form(default=""),
    format: str = Form(default="Test"),
    duration_minutes: int = Form(default=10),
    language: str = Form(default="Roman Hinglish"),
    tts_provider: str = Form(default="both"),
    voice: str = Form(default="hi-IN-MadhurNeural"),
    screenshots: list[UploadFile] = File(default=[])
):
    parsed = urlparse(match_url.strip())
    allowed = {"crex.com", "www.crex.com", "cricbuzz.com", "www.cricbuzz.com", "icc-cricket.com", "www.icc-cricket.com"}
    if parsed.scheme != "https" or parsed.hostname not in allowed:
        raise HTTPException(400, "Use an HTTPS CREX, Cricbuzz or ICC match URL.")
    if len(screenshots) > 10:
        raise HTTPException(400, "Upload a maximum of 10 screenshots.")
    derived = unquote(parsed.path.rstrip("/").split("/")[-1]).replace("-", " ")
    derived = re.sub(r"[^A-Za-z0-9 ]+", " ", derived).strip()
    name = (match_name.strip() or derived or "Cricket Match")[:180]
    if len(name) < 3: name = "Cricket Match"
    job_id = uuid.uuid4().hex[:12]
    upload_dir = settings.data_dir / "uploads" / job_id
    upload_dir.mkdir(parents=True, exist_ok=True)
    saved = []
    for index, upload in enumerate(screenshots):
        if not (upload.content_type or "").startswith("image/"):
            raise HTTPException(415, "Only image screenshots are accepted.")
        raw = await upload.read(12 * 1024 * 1024 + 1)
        if not raw or len(raw) > 12 * 1024 * 1024:
            raise HTTPException(413, "Each screenshot must be smaller than 12 MB.")
        suffix = Path(upload.filename or "").suffix.lower()
        if suffix not in {".jpg", ".jpeg", ".png", ".webp"}:
            suffix = ".jpg" if upload.content_type == "image/jpeg" else ".png"
        target = upload_dir / f"source-{index + 1}{suffix}"
        target.write_bytes(raw)
        saved.append(str(target))
    try:
        req = MatchRequest(match_name=name, format=format, duration_minutes=duration_minutes, language=language,
                           tts_provider=tts_provider, voice=voice, source_url=match_url.strip(), screenshot_paths=saved)
    except Exception as exc:
        raise HTTPException(400, str(exc))
    create_job(job_id, req)
    return {"job_id":job_id,"status":"queued","message":"Mobile match package uploaded. Research worker queued.",
            "screenshots_received":len(saved),"match_name":name}

@app.get("/api/jobs/{job_id}")
def status(job_id:str):
    job=get_job(job_id)
    if not job: raise HTTPException(404,"Job not found")
    return job

@app.get("/api/jobs/{job_id}/files/{kind}")
def download(job_id:str,kind:str):
    job=get_job(job_id)
    if not job or job["status"]!="completed" or not job.get("result"): raise HTTPException(404,"Output not ready")
    mapping={"landscape":job["result"]["videos"][0],"portrait":job["result"]["videos"][1],
      "audio":job["result"]["audio"],"script":job["result"]["script"],"subtitles":job["result"]["subtitles"],
      "thumbnail":job["result"]["thumbnail"],"metadata":job["result"]["metadata"],"manifest":job["result"]["manifest"]}
    if kind not in mapping: raise HTTPException(404,"Unknown output")
    path=Path(mapping[kind]).resolve()
    if not path.is_file(): raise HTTPException(404,"File missing")
    return FileResponse(path,filename=path.name)
