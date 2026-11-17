# Preet Sports AI Cricket Production Studio — Windows Laptop Build

## Selected product
Local Windows desktop workflow, sports-broadcast visual style, 8–12 minute Roman Hinglish pre-analysis, CPU-first rendering, no Android dependency.

## Production pipeline
1. Match resolution and source-backed cricket research.
2. Gemini creates a spoken Roman Hinglish script sized for 8–12 minutes (125–140 words/minute).
3. User can review and correct the saved script before final publishing; generated scripts are retained as editable TXT.
4. TTS produces a standards-compliant 24 kHz mono WAV. Edge TTS MadhurNeural is the default no-additional-API-cost online voice; Gemini TTS is an optional provider requiring API access.
5. Gemini creates a JSON storyboard with narration anchors and visual types; deterministic sports graphics are used if planning is unavailable.
6. FFmpeg assembles landscape 1920x1080 and portrait 1080x1920 MP4, subtitles, thumbnail, metadata and manifest.
7. ffprobe validates streams, duration and dimensions before job completion.

## Visual language
Original navy/gold cricket broadcast package, stadium atmosphere, team matchup cards, player cards, pitch/venue report, stat panels, tactical field diagrams, key battles, prediction and outro. Use supplied/appropriately licensed player photos and logos only; use silhouettes or typography when unavailable. Do not use unlicensed broadcast footage.

## Runtime
Windows 10/11, Python 3.11, FFmpeg/ffprobe, local FastAPI + SQLite + worker. API binds to 127.0.0.1 only. Gemini key is stored in local ignored .env. No public hosting or GPU requirement.

## Honest limitations
A cricket data provider must be configured or facts supplied for source-backed player, venue, pitch, form and H2H analysis. Gemini scene plans are instructions, not generated footage. The current visual renderer uses original graphics cards; player-photo fetching, live data adapters, script approval UI and final end-to-end Windows playback testing remain later acceptance gates.
