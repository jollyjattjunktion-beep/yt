# Preet Sports Studio — Android app

Native Android app for on-device Roman Hinglish cricket video production.

## Build

- Android Studio stable
- JDK 17
- Android SDK 35
- Open this directory as the Gradle project and run `:app:assembleDebug`.

GitHub Actions also runs the Android debug build on pushes to the feature branch and pull requests.

## Current phone workflow

1. Enter a match title, format and target script duration.
2. Save a Gemini API key in Android encrypted preferences (the key is never committed).
3. Generate a Roman Hinglish script and Gemini narration.
4. Generate eight original scene cards locally.
5. Encode H.264 video and AAC narration with Android MediaCodec, then mux MP4 with MediaMuxer.
6. Export landscape 16:9 and portrait 9:16 MP4 files to **Movies/Preet Sports** using MediaStore.

The renderer spools encoded samples to the project folder to limit memory use. It uses a low frame rate for still-card scenes to keep phone rendering practical.

## Important limitations / validation

- This is a first native MP4 encoder implementation. It still requires a successful GitHub Actions build and real-device playback tests on supported Android phones before being described as production-ready.
- Gemini model access and returned audio WAV encoding depend on the user's Google AI account and response format. The app validates PCM WAV before encoding.
- Scene cards are original branded graphics, not licensed player photographs or official team logos.
- Pitch, squads, head-to-head and player facts are not automatically researched by this app yet. The script prompt is instructed not to invent missing match facts.
- The web dashboard/PWA remains a separate Python-backed workflow; phone rendering occurs in the native Android app.
- No API key should be placed in GitHub, source files, screenshots, or chat.

## Project output

Each project is saved under the app's external files directory in `PreetSports/`, including script, narration, scene cards and intermediate encoded samples. Final MP4s are also copied to the user's Movies library.
