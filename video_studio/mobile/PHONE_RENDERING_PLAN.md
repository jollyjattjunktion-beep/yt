# Phone-side rendering acceptance plan

The Android renderer is being built as a native pipeline, not a remote call to the Python worker.

## Planned render stages
1. Gemini generates a validated Roman Hinglish script.
2. Gemini TTS returns narration audio; provider MIME/sample format must be parsed, not assumed.
3. Original branded scene cards are drawn with Android Canvas.
4. Android MediaCodec encodes H.264 video and AAC narration; MediaMuxer writes MP4.
5. Export both 1280x720 (16:9) and 720x1280 (9:16) sequentially to avoid peak memory pressure.
6. Android MediaStore saves completed files to Movies/Preet Sports.
7. Persistent job state, foreground notification, cancellation, low-storage and interruption recovery.

## Device limits
A 5–7 minute 720p export can take several minutes and consume substantial battery, storage and memory. The app must show estimates and let the user cancel. Do not render both formats concurrently on low-memory devices.

## Current state
Canvas-based original scene graphics and local job metadata are in source. Full MediaCodec/MediaMuxer audio-video muxing, MediaStore export, foreground service, device tests and APK signing remain outstanding. Until those acceptance checks pass, the app must not label a job as a completed video.
