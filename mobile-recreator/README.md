# Preet Reel Recreator (Android)

Android-first app scaffold for recreating the *editing style* of a user-selected reference Reel.

## Mobile workflow
- Select a reference video from Android Files/Gallery using the system document picker.
- Choose one of two modes:
  - Same song, new visuals.
  - Same editing style, different Punjabi song.
- In the second mode, select replacement audio from the phone.
- Prepare the project for cloud rendering.

## Current implementation status
This first Android milestone implements the native Compose UI and phone file pickers. It does **not** yet render or upload a Reel. The next milestone is the authenticated cloud processing API and job-status UI.

## Build APK without a PC
The GitHub Actions workflow `.github/workflows/android-apk.yml` builds a debug APK in the cloud when this folder is pushed or the workflow is manually run. Download the APK from the workflow's Artifacts section on GitHub using the GitHub mobile app or phone browser.

## Planned cloud pipeline
1. Upload reference video and optional replacement audio.
2. Extract audio, scene boundaries, on-screen text and visual style.
3. Transcribe Punjabi lyrics and align timestamps.
4. Select licensed/owned aesthetic footage or user media.
5. Render 9:16 MP4 with lyric overlays, subtle zoom/pan and crossfades.
6. Return an export URL and job status to the Android app.

## Important
Do not scrape or download private Instagram media. The app processes a video the user selects from their device. Use music and footage that the user owns or is licensed to use.
