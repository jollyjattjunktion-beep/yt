# Mobile + Web product architecture

## Product targets
- Android APK: native Android application, secure on-device API-key storage, match setup, production controls and phone-local output.
- Web/PWA: installable browser dashboard for desktop and mobile, backed by the existing FastAPI application.
- Existing Windows/Python production worker remains supported and is not removed.

## Phone-rendering design
Phone rendering must execute in the Android process and write exports to app storage / Android MediaStore. It must not silently forward a "render on phone" request to the PC worker. The native implementation will use Android MediaCodec + MediaMuxer for H.264/AAC output, with Canvas-generated branded scenes and a local job lifecycle. Gemini requests run over HTTPS; API keys remain in Android encrypted storage. Long jobs require foreground-service notification, cancellation, thermal/battery checks, storage checks and resumable stage state.

## Current delivery in this branch
- Native Android Studio project shell under `mobile/`.
- Compose match setup and Gemini-key settings screen.
- API key storage via AndroidX EncryptedSharedPreferences.
- Installable web app manifest and service worker.
- Existing browser dashboard updated for mobile touch targets and safe-area insets.

## Not yet production-ready
- Native Gemini script/TTS client and on-device audio decode.
- Android MediaCodec / MediaMuxer rendering pipeline and progress notifications.
- Android export permission / MediaStore integration.
- Instrumented device tests and signed APK.
- PWA offline mode only caches the app shell; production API requests still need connectivity to the FastAPI host.
- Existing Python renderer remains a separate backend path.

## Build validation
Open `mobile/` in Android Studio with JDK 17 and Android SDK 35. Build a debug APK on a connected Android device. A successful APK build alone is not acceptance: test API-key persistence, Gemini generation, 5-minute video render, playback, both aspect ratios, interruption/recovery, and low-storage handling on target devices.
