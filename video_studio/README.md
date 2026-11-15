# Preet Sports AI Video Studio (MVP)

This folder adds an automated production application alongside the existing YouTube-agent skills. It does not replace or delete them.

## What works in this MVP
- FastAPI job API and status polling
- Optional Gemini script generation
- Fact-safe fallback script when Gemini is not configured
- Indian voice generation through Edge TTS
- Original branded title-card graphics generated with Pillow
- FFmpeg rendering to 1920x1080 and 1080x1920
- MP3, script and YouTube metadata package
- Responsive browser dashboard at `/`
- Opt-in YouTube upload for both formats using the channel owner's OAuth consent

## Important current limitations
- A match name alone is not a source of verified cricket facts. Configure `CRICKET_DATA_API_URL` to a trusted adapter or pass `match_facts` in the request. Without either, the fallback script intentionally avoids invented squads, stats, venue and pitch claims.
- The current visual renderer creates original branded motionless scene cards; it does not yet create player-photo animations, licensed highlight footage, or AI-generated stadium video.
- YouTube upload is opt-in and requires a valid Google OAuth desktop client JSON at `client_secret.json`. The first upload opens the Google consent flow on the rendering machine. Use private or unlisted for initial testing. YouTube API quota and channel policy still apply.
- Jobs are held in process memory in this MVP. Use a persistent queue/database before multi-user or production deployment.
- Edge TTS requires network access. Gemini is optional and requires a valid API key.

## Install locally
Requires Python 3.11+, FFmpeg installed and available on PATH.

```bash
cd video_studio
python -m venv .venv
# Windows: .venv\\Scripts\\activate
# Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Open http://127.0.0.1:8000/docs.

## Create a video job
```bash
curl -X POST http://127.0.0.1:8000/api/jobs ^
  -H "Content-Type: application/json" ^
  -d "{\"match_name\":\"India vs Australia 1st ODI\",\"duration_minutes\":6,\"language\":\"Roman Hinglish\",\"match_facts\":{}}"
```
Poll `GET /api/jobs/{job_id}`. When completed, download:
- `/api/jobs/{job_id}/files/landscape`
- `/api/jobs/{job_id}/files/portrait`
- `/api/jobs/{job_id}/files/audio`
- `/api/jobs/{job_id}/files/script`
- `/api/jobs/{job_id}/files/metadata`

Example cricket-data adapter response contract:
```json
{
  "fixture": {"date": "ISO-8601", "venue": "ground name", "format": "ODI", "status": "scheduled"},
  "teams": [{"name": "Team A", "squad": [], "recent_results": []}, {"name": "Team B", "squad": [], "recent_results": []}],
  "pitch_report": {"summary": "source-backed report", "source": "URL"},
  "players": [],
  "head_to_head": {"summary": "source-backed summary", "source": "URL"},
  "sources": [{"name": "provider", "url": "https://...", "retrieved_at": "ISO-8601"}]
}
```

## Production hardening still required
Persistent PostgreSQL jobs, Redis/Celery worker, secure authentication, file retention/cleanup, resumable rendering, actual provider adapters with terms-compliant access, script duration validation, subtitles, richer scene templates, YouTube OAuth upload and end-to-end tests. Do not expose this API publicly without authentication.
