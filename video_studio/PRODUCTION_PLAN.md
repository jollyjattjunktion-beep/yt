# Preet Sports AI Cricket Production Studio — Local-First Completion Plan

This is a local-first build. The complete studio will be created and tested before installation on the user's Windows PC. Render is not required for video production. The existing YouTube skills and helper scripts remain untouched; all changes stay on `feature/automated-video-studio` until acceptance tests pass.

## Product contract

Enter a match name and select a 5–7 minute preview. The studio resolves the fixture, gathers source-backed cricket facts, writes a Roman Hinglish presenter script, synthesizes narration, creates data-driven visual scenes, renders 16:9 and 9:16 videos, and packages subtitles, thumbnail, script and YouTube metadata. YouTube publishing is always an explicit opt-in and defaults to private.

A job is successful only when required outputs exist, pass validation and can be downloaded. Missing research must be visible; the system must not fabricate squads, player photos, statistics, venue conditions, pitch reports or head-to-head records.

## Ten stages and acceptance criteria

1. **Fixture resolution** — resolve teams, match number, format, local start time, venue and stable fixture identity. Ambiguous matches require a selection rather than guessing.
2. **Research aggregation** — normalized fixture, teams, squads, player profiles, recent form, venue, pitch, head-to-head, source URLs and retrieval timestamps from configured, permitted data providers.
3. **Fact validation** — normalize names/dates, detect conflicts, attach provenance to claims and mark unavailable facts. Unsupported claims cannot enter narration or graphics.
4. **Script generation** — 5–7 minute spoken script, length validated against speaking rate, Roman Hinglish only, natural hooks/transitions, tactical analysis and qualified prediction. Retry or fail clearly if too short.
5. **Voice synthesis** — Gemini TTS provider with configurable voice/style and explicit fallback. Save audio and timing/alignment metadata when supported.
6. **Visual scene engine** — stadium-style opening, team introductions, player cards, pitch report, H2H, key battles, tactical factors and prediction. Use licensed/provider-supplied or user-owned assets only; use initials/silhouettes when photos are unavailable.
7. **Editorial assembly** — time scenes to narration; create SRT and optional burned-in subtitles, transitions, subtle original music and controlled sound design. No broadcast footage without rights.
8. **Multi-format rendering** — render 16:9 and 9:16 sequentially with bounded FFmpeg threads and memory-aware settings. Generate thumbnail and project metadata.
9. **Publishing package** — title, description, tags, chapters, thumbnail and output manifest. OAuth upload is optional, user-authorized and private by default.
10. **Quality gate** — verify duration, codecs, aspect ratios, subtitles, thumbnail, metadata, scene coverage, source manifest and files before marking complete.

## Local-first runtime architecture

- **Windows desktop app:** local FastAPI dashboard opened in the browser, started/stopped with simple `.bat` scripts. No public deployment is needed for local production.
- **Local persistent jobs:** SQLite database by default so job history and progress survive restarts; PostgreSQL can be an optional later mode.
- **Separate worker process:** API submits jobs to a durable local queue; a worker performs research, TTS and FFmpeg rendering. Never run heavy video rendering inside the API request process.
- **Local artifact storage:** output folders on disk, organized by match and job ID; downloads and job history remain available after restart.
- **CPU-first rendering:** no NVIDIA GPU requirement. Sequential output rendering, bounded FFmpeg threads, sensible preview/production presets and memory checks for a 16 GB Windows PC.
- **Secrets and security:** local `.env` file, ignored by Git; dashboard bound to localhost by default. No public access unless explicitly configured.
- **Optional integrations:** Gemini API key for script/TTS; a permitted cricket data API adapter for current match facts; optional Google OAuth only when the user enables upload.

## Local installation package (created after implementation)

Provide `install_windows.bat`, `start_studio.bat`, `stop_studio.bat`, `check_setup.bat`, `.env.example`, pinned dependencies, FFmpeg setup/check instructions, and a clear README. Installation should create a Python virtual environment, install dependencies, initialize the local database and verify FFmpeg/ffprobe. Do not bundle or commit credentials.

## Build and acceptance gates

- **A — Local foundation:** persistent SQLite jobs, queue/worker separation, progress, restart recovery and Windows launch scripts.
- **B — Cricket intelligence:** adapter contract, source provenance, conflict/missing-data handling and fixture disambiguation.
- **C — Editorial engine:** long-form script validation, Gemini TTS, scene templates and subtitle timing.
- **D — Rendering:** landscape/portrait, thumbnail, resource-bounded CPU rendering and output manifest.
- **E — End-to-end:** automated tests for fixture/missing data, worker recovery, script length, render smoke test, quality gate and optional private upload.

The first real match is not production-ready until Gates A–E pass. The user will install the finished local package only after this build and test phase.
