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

## Android reference implementation review (2026-11-24)

The community Android engine source was inspected directly at its current
repository revision. Its JNI bridge declares the nine-argument
`ptt_create(..., sentence_pause_ms, max_text_tokens)` API and also calls
`ptt_stream_cancel`. Its vendored C++ source implements that same API and
returns stream chunks allocated with `malloc`, released by `ptt_free_audio`.
This confirms that the bridge must be pinned together with its matching C++
source; the seven-argument upstream API is not ABI-compatible.

The inspected Android runtime source contains no implementation or reference
for the Hindi pack's `bos_before_voice.npy` / `insert_bos_before_voice`
contract. Therefore, the community Android engine is a useful build/JNI
reference but is **not yet verified as a compatible Hindi runtime**. Do not
vendor it as-is or enable synthesis. The next native milestone is a deliberate
Hindi compatibility port: locate the model's BOS tensor loading/conditioning
path, add it to this exact runtime revision, and validate output against the
Hindi release's expected tensor sequence before building the app bridge.

The Android reference's native CMake target also expects an ABI-specific
`libonnxruntime.so` and headers under its own app tree, plus SentencePiece,
`dr_libs`, and Sonic. These are not present in this app merely because the
Kotlin ONNX Runtime AAR dependency exists. Dependency acquisition, licenses,
ABI packaging, and reproducible hashes must be addressed before adding its
CMake target.

## Current decision

Do not copy the reference JNI bridge unchanged and do not enable a fake Generate action. First pin a runtime revision and validate it against the Hindi model graphs, then implement the matching JNI bridge and Android CMake build on this experimental branch only.

## Pinned-source API and Android build findings

The Hindi ONNX README identifies the reviewed runtime snapshot as based on
`VolgaGerm/PocketTTS.cpp@e801e7d6c2692121a39e80ae525cb5265174a495`.
The pinned upstream source at that revision exposes this seven-argument C
constructor (the JNI declaration must be checked against the Hindi snapshot
itself before use):

```cpp
void* ptt_create(
    const char* models_dir,
    const char* voices_dir,
    const char* tokenizer_path,
    const char* precision,
    float temperature,
    int lsd_steps,
    int num_threads
);
```

The same pinned upstream source exposes streaming calls
`ptt_stream_start(handle, text, voice)`,
`ptt_stream_read(stream_ctx, &samples, &sample_count)`, and
`ptt_stream_end(stream_ctx)`, plus `ptt_destroy(handle)` and
`ptt_free_audio(samples)`. Ownership and thread-safety must be preserved in
the JNI wrapper; especially, every returned sample buffer must be freed with
the matching native API.

The bundled runtime's desktop CMake configuration is not an Android build
recipe: it selects Linux/macOS/Windows ONNX Runtime archives and builds a CLI
executable. It uses CMake 3.28+, C++17, SentencePiece, dr_libs and ONNX Runtime
1.23.2. Android integration therefore needs a separate Android-aware CMake
target, Android-compatible ONNX Runtime headers/shared library for each
packaged ABI, and a JNI shared-library target. Do not point Android CMake at
the desktop Linux ONNX Runtime archive.

The Hindi model README also explicitly requires the Hindi BOS-before-voice
tensor and warns that older runtimes omitting it can terminate synthesis
prematurely. This must be verified in the exact Hindi source snapshot, not
assumed from the upstream base revision.
