# Preet Sports AI Cricket Production Studio — Completion Plan

This plan upgrades the existing MVP in-place. The original YouTube skills and helper scripts remain untouched. Work stays on `feature/automated-video-studio` until end-to-end acceptance tests pass.

## Product contract

A user enters a match name and selects a 5–7 minute preview. The studio resolves the fixture, gathers source-backed cricket facts, creates a Roman Hinglish presenter script, synthesizes narration, builds data-driven visual scenes, renders landscape and portrait outputs, and packages the script, subtitles, thumbnail and YouTube metadata. YouTube publishing is always an explicit opt-in and defaults to private.

A job is successful only when all required outputs exist, pass validation and can be downloaded. Missing research must be shown as missing; the system must not fabricate squads, player photos, statistics, venue conditions, pitch reports or head-to-head records.

## Ten production stages and acceptance criteria

1. **Fixture resolution** — resolve teams, match number, format, local start time, venue and fixture identity. Ambiguous matches require user selection rather than guessing.
2. **Research aggregation** — provider adapters return normalized fixture, teams, squads, player profiles, recent form, venue, pitch, head-to-head, source URLs and retrieval timestamps.
3. **Fact validation** — normalize names and dates, detect conflicting values, attach provenance to claims, and mark unavailable facts. No unsupported claim may enter the script or graphics.
4. **Script generation** — create 650–950 spoken words for a 5–7 minute script (validated against selected speaking rate), Roman Hinglish only, natural hooks and transitions, clear analysis and appropriately qualified prediction. Retry or fail clearly when the script is too short.
5. **Voice synthesis** — Gemini TTS provider with configurable voice/style and an explicit fallback provider. Save audio and timing/alignment metadata when supported.
6. **Visual scene engine** — data-driven templates for stadium opening, team lineups, player cards, pitch report, head-to-head, key battles, tactical factors and prediction. Use licensed/provider-supplied assets only; use initials/silhouettes when player images are unavailable.
7. **Editorial assembly** — time scenes to narration; create readable subtitles (SRT and burned-in option), transitions, subtle original background music and controlled sound design. No broadcast match footage unless rights are supplied.
8. **Multi-format render** — render 16:9 and 9:16 sequentially with bounded FFmpeg threads and memory-aware settings. Generate a thumbnail and retain project metadata.
9. **Publishing package** — title, description, tags, chapters, thumbnail and output manifest. OAuth upload is optional, user-authorized and private by default.
10. **Quality gate and delivery** — verify audio/video duration, codecs, aspect ratios, subtitles, thumbnail, metadata, scene coverage, source manifest and file existence. Only then mark complete.

## Required runtime architecture

- **Web/API service:** lightweight FastAPI dashboard and authenticated job API only. It must not render video in the web process.
- **Persistent job store:** PostgreSQL in production, with status, progress, errors, inputs, provenance and output manifest. No in-memory-only job state.
- **Queue:** Redis-backed worker queue (or an equivalent durable queue) with retries, idempotency and stale-job recovery.
- **Render worker:** separate machine/process with FFmpeg and bounded concurrency. The existing Render free web instance has a 512 MiB memory limit and has already been OOM-killed; it is not a safe render worker.
- **Artifact storage:** persistent object/file storage for generated media. Do not rely on an ephemeral web container filesystem.
- **Security:** authentication, upload limits, secret management, private job outputs and explicit YouTube consent.

## Provider and credential configuration

The application can be developed without embedding secrets. Before real match production, configure:
- A cricket data adapter endpoint and API key with permitted access to fixtures, squads, player records and historical results.
- Gemini API key and supported TTS model/voice settings.
- Durable PostgreSQL and queue connection.
- Worker host with FFmpeg/ffprobe and persistent output storage.
- Optional Google YouTube OAuth client only if publishing is enabled.
- Optional licensed visual asset source or user-owned brand/team/player assets.

## Delivery gates

- **Gate A — foundations:** persistent jobs, queue/worker separation, health and progress reporting.
- **Gate B — cricket intelligence:** provider adapter, source provenance, conflict/missing-data handling and fixture disambiguation.
- **Gate C — editorial engine:** long-form script validation, Gemini TTS, scene templates and subtitle timing.
- **Gate D — rendering:** two aspect ratios, thumbnail, bounded resource use and complete output manifest.
- **Gate E — end-to-end:** automated tests with fixture fixtures, missing-data cases, render smoke test, quality gate and opt-in private upload test.

The first live match is not considered production-ready until Gates A–E pass. A deployed dashboard alone is not proof that the studio is complete.
