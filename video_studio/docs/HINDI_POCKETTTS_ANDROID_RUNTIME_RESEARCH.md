# Hindi PocketTTS Android runtime integration notes

Branch: `experiment/hindi-pockettts-voice-clone`

## Current app state

- The app imports a Hindi ONNX model ZIP into private storage and verifies SHA-256 checksums.
- The app imports a 5–30 second PCM 16-bit, mono, 24 kHz WAV prompt into private storage.
- Speech generation is intentionally disabled. There is no JNI library or Android CMake target in this app yet.
- Existing Android dependency: ONNX Runtime Android 1.23.2. This is a Java/Kotlin AAR dependency, not proof that a compatible native C++ runtime is linked.

## Runtime candidate reviewed

The community project `The-unknown-Shadowman/PocketTTS-Android-Engine` contains an Android JNI integration around `VolgaGerm/PocketTTS.cpp`, with an ARM64 Android build, ONNX Runtime native library and headers, SentencePiece, dr_libs, and a CMake target. It is a useful implementation reference, not a drop-in dependency.

Its vendored PocketTTS C API has a modified constructor signature:
`ptt_create(models_dir, voices_dir, tokenizer_path, precision, temperature, lsd_steps, threads, sentence_pause_ms, max_text_tokens)`.
The upstream `VolgaGerm/PocketTTS.cpp` master C API reviewed here exposes the 7-argument form:
`ptt_create(models_dir, voices_dir, tokenizer_path, precision, temperature, lsd_steps, threads)`.
JNI declarations must match the exact vendored source; mixing these versions would cause ABI/runtime errors.

## Required compatibility checks before enabling inference

1. Pin and vendor one reviewed PocketTTS.cpp revision; record its commit and license.
2. Confirm the Hindi model pack's ONNX graph names, tensor input/output names, tokenizer and configuration are accepted by that exact runtime revision. Matching filenames alone is insufficient.
3. Pin a matching ONNX Runtime Android native binary and headers. Do not assume the current 1.23.2 Maven AAR is compatible with another project's tested native binary.
4. Build only the verified target ABI first (likely `arm64-v8a`, but confirm the user's device ABI before restricting packaging).
5. Add JNI lifecycle, cancellation, audio streaming and WAV output tests. Run on a physical Android 12 device before enabling the Generate button.
6. Keep model and voice prompt in app-private storage; do not upload either.

## Sources reviewed

- https://github.com/VolgaGerm/PocketTTS.cpp
- https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine

## Current decision

Do not copy the reference JNI bridge unchanged and do not enable a fake Generate action. First pin a runtime revision and validate it against the Hindi model graphs, then implement the matching JNI bridge and Android CMake build on this experimental branch only.
