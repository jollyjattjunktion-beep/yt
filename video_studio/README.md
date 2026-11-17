# Preet Sports AI Cricket Production Studio — Windows Laptop

A local-first desktop production dashboard for 8–12 minute Roman Hinglish cricket pre-analysis videos.

## Pipeline
Match research → Gemini Roman Hinglish script → narration WAV → Gemini scene plan → original sports-broadcast graphics → FFmpeg MP4.

## Requirements
- Windows 10/11 64-bit
- Python 3.11
- FFmpeg and ffprobe on PATH
- Internet for Gemini script/scene planning and Edge TTS; Gemini TTS is optional and requires model/API access
- 16 GB RAM recommended; CPU rendering, no NVIDIA GPU required

## Install and run
1. Download/clone branch `feature/automated-video-studio`.
2. Open `video_studio`.
3. Run `install_windows.bat`.
4. Ensure FFmpeg is installed and available in PATH.
5. Run `start_studio.bat`, then open http://127.0.0.1:8000.
6. Save your Gemini API key in Settings. For Madhur voice, select Edge TTS and `hi-IN-MadhurNeural`; this uses internet and is not offline. Gemini TTS can be selected as an alternative if enabled for your account.

## Duration and output
Choose 8, 10 or 12 minutes. The studio creates Roman Hinglish script TXT, narration WAV, SRT subtitles, thumbnail, metadata, output manifest, 16:9 MP4 and 9:16 MP4.

## Important
The first build uses original generated sports graphics, not real match footage. Player photographs/logos and current match statistics require licensed/user-owned assets or a configured cricket data provider. Missing facts must remain marked unavailable. Review all match facts and script before publishing. YouTube publishing is not enabled by default.
