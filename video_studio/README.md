# Preet Sports AI Cricket Production Studio — Local Windows Edition

This is the local-first production application. It runs on the user's Windows PC; Render is not required for video rendering.

## Current build status

The local foundation includes:
- Windows installer and start/stop scripts
- Local FastAPI dashboard bound to localhost
- Persistent SQLite job records and separate polling worker
- Gemini API settings form; keys are stored in local `.env`, never in source control
- Gemini TTS adapter that requests WAV audio
- Sequential CPU FFmpeg rendering for 16:9 and 9:16

The visual scene renderer currently produces original branded graphic cards with labels for team introductions, player cards, pitch report, head-to-head and prediction. It does **not yet populate those cards with researched player photos, team logos, pitch facts or H2H statistics**. Those require the cricket provider adapter and the next visual-engine implementation. Do not treat the current renderer as the finished visual studio.

## Requirements

- Windows 10/11, 64-bit
- Python 3.11
- FFmpeg and ffprobe on PATH
- Internet access for Gemini API and (when configured) cricket data
- 16 GB RAM recommended for full HD CPU rendering; no NVIDIA GPU required

## Install

1. Download the repository ZIP from the `feature/automated-video-studio` branch, or clone that branch.
2. Open the `video_studio` folder.
3. Run `install_windows.bat`.
4. Install FFmpeg if the installer reports it missing. Add the FFmpeg `bin` folder to PATH.
5. Run `start_studio.bat`.
6. Open http://127.0.0.1:8000 and paste your Gemini API key into the settings panel.

## Gemini setup

The settings page has separate script and TTS model fields, voice selection, and optional cricket provider credentials. The Gemini key is saved to the local `.env` file. Never paste API keys into GitHub, screenshots, chat, or public logs.

The TTS adapter uses Gemini's audio response modality and writes the returned WAV data to disk. Current default TTS model is `gemini-3.8-flash-lite-tts`; select a model available to your API key/account. Voice names are configurable.

## Output

Generated jobs are stored in `data/studio.sqlite3`. Media is stored under `output/<match>/<job-id>/`. The worker runs separately from the API so a browser refresh does not cancel production.

## Important limitations before first real match

- The cricket research provider is still an adapter contract; configure a compatible endpoint returning source-backed fixture, squad, player, venue, pitch and H2H data.
- The long-form script generator and fact validator still need completion and tests.
- Player photos/team logos must come from licensed, permitted or user-owned assets. No broadcast footage is bundled.
- Subtitles, detailed scene-to-speech synchronization, thumbnail and final quality gate are still to be completed.
- YouTube publishing is not enabled in this local foundation.

Do not publish a video as a complete match preview until its research facts and generated output have been checked.
