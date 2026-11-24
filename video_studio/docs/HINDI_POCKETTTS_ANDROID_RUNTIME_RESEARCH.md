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

## Native dependency audit of Android reference (2026-11-24)

A second pass over the reference project's Android CMake target, JNI wrapper,
and native dependency preparation script exposed a version mismatch that must
be resolved before copying its build setup:

- The reference CMake links an ABI-specific `libonnxruntime.so` and headers
  from its app tree; it does not link the Kotlin AAR as a substitute.
- Its preparation script pins ONNX Runtime Android **1.20.0**, verifies the
  downloaded AAR against a fixed SHA-256, then extracts both the ARM64 native
  library and headers from that same artifact.
- Its comments report device-specific SIGILL or severe regressions with
  several later ORT releases on one Snapdragon 8 Elite test device. This is
  evidence from that project/device, not proof that those versions fail on
  this app's target OPPO device.
- This app currently declares `onnxruntime-android:1.23.2`. Therefore, copying
  the reference CMake or its dependency script unchanged would create an
  unreviewed native-vs-managed ORT version split.

The reference's Kotlin wrapper confirms its JNI surface is designed around
eight configurable constructor values after model/voice paths, plus
synthesis, stop, and close. That wrapper alone does not establish Hindi model
compatibility: its source currently selects English/German voices and its
instrumentation exercises those languages, not the Hindi BOS contract.

### Decision / next gate

Do not import the reference's ORT 1.20.0 binary or downgrade this app based on
another device's benchmark. First determine the exact ONNX Runtime version
required by the Hindi ONNX release and validate it on the target Android ABI.
Then choose one matching managed/native runtime version, pin the native AAR
hash, and build a small JNI smoke-test target. Hindi BOS conditioning remains
a separate mandatory gate before speech output can be enabled.

## Hindi release snapshot compatibility correction (2026-11-24)

A review of the current `saryps-labs/pocket-tts-hindi/onnx` release documentation
and its validation record corrects one earlier conclusion above: the **Hindi
release's own reviewed runtime snapshot does implement the BOS-before-voice
behavior**. Its README says the runtime detects `onnx/bos_before_voice.npy`
and prepends it while encoding a raw audio prompt; the validation record says
omitting this frame caused premature EOS. The earlier statement that the
Android community runtime had no BOS support remains true for that separate
project, but must not be generalized to the Hindi release's own runtime.

The Hindi release records these facts:

- Runtime base: `VolgaGerm/PocketTTS.cpp@e801e7d6c2692121a39e80ae525cb5265174a495`,
  with Hindi compatibility fixes in the release's `onnx/runtime/` snapshot.
- The source snapshot is identified in the release commit by
  `onnx/runtime/pocket_tts.cpp` SHA-256
  `9c1c2208f68448f61c9cb169432f3af19c1741a35d2eef6984cdbcf448ea85e9`.
- The validation record reports that all five graphs loaded with ONNX Runtime
  1.23.2 CPUExecutionProvider and that the native CLI smoke tests used the
  required `[BOS, Mimi(prompt)]` voice state.
- The release pins ONNX Runtime 1.23.2. These checks establish Linux CPU
  compatibility, **not Android ABI compatibility or OPPO-device performance**.
- The model README recommends plain Devanagari Hindi; Latin/Roman text,
  numbers, short isolated tokens and punctuation-heavy input are less reliable.

Sources:
- https://huggingface.co/saryps-labs/pocket-tts-hindi/blob/main/onnx/README.md
- https://huggingface.co/saryps-labs/pocket-tts-hindi/blob/main/onnx/VALIDATION.md
- https://huggingface.co/saryps-labs/pocket-tts-hindi/commit/c09efe62d2b17c0848318104d8b88e20b83e32b8

### Revised integration decision

Use the **Hindi release's own pinned runtime snapshot** as the primary porting
candidate, rather than the unrelated Android community fork. Before writing
JNI, inspect that exact snapshot's exported C API, build dependencies and
voice-conditioning implementation; then design an Android CMake target against
the exact same ONNX Runtime 1.23.2 native headers/library. Keep the Android
community project's build setup as reference only. Do not enable synthesis
until the native build, BOS conditioning, model load, voice prompt, generated
PCM/WAV, cancellation and device ABI are verified.

## Exact Hindi runtime C API inspection (2026-11-24)

The current Hindi release's actual `onnx/runtime/pocket_tts.cpp` was inspected,
not just the upstream base revision. Its exported C API is a seven-argument
`ptt_create(models_dir, voices_dir, tokenizer_path, precision, temperature,
lsd_steps, num_threads)`; it also exports:

- `ptt_warmup(handle)`
- `ptt_set_generation_options(handle, temperature, lsd_steps, eos_threshold,
  eos_extra_frames, first_chunk_frames, max_chunk_frames)`
- `ptt_set_append_terminal_punctuation(handle, enabled)`
- `ptt_stream_start(handle, text, voice_path)`
- `ptt_stream_start_embedding(handle, text, embedding, frames, dimensions)`
- `ptt_stream_read(stream, &samples, &sample_count)`
- `ptt_stream_cancel(stream)` and `ptt_stream_end(stream)`
- `ptt_free_audio(samples)` and `ptt_destroy(handle)`

This corrects the earlier assumption that the Hindi release's C API still
needed to be inferred from upstream: its exact seven-argument constructor
matches the upstream base API, but the Hindi snapshot adds generation-option
setters and the embedding stream entry point. It is **not** the Android fork's
nine-argument constructor.

### Android port constraints from the actual source

The file is a single large C++ translation unit that includes ONNX Runtime C++
and SentencePiece headers, plus embedded implementations of dr_wav, dr_mp3,
and dr_flac. It also includes POSIX socket headers and a CLI/HTTP-server entry
point. Android integration must therefore compile it as a shared library with
the CLI/server entry point disabled, supply the native ONNX Runtime 1.23.2
headers and ABI-matched shared library, and supply SentencePiece. The build
must account for Android/Bionic header availability and remove or guard
server-only socket functionality if Android's NDK build rejects it. Merely
adding a JNI wrapper around the file is not enough.

The source's stream callback allocates each PCM chunk with `malloc`; consumers
must release it with `ptt_free_audio`. `ptt_stream_end` joins the worker and
frees any queued chunks, so JNI cancellation and lifecycle code must not race
stream destruction or double-free chunks. The source offers a direct
file-based voice stream and a precomputed embedding stream; use the file-based
path initially so the Hindi runtime itself performs BOS conditioning from the
raw WAV prompt. Do not generate or cache embeddings until their BOS-frame
contract is separately validated.

The source's `ptt_set_append_terminal_punctuation(handle, 0)` must be called
for this Hindi release. Use conservative initial settings from the published
smoke test (two threads, temperature 0.3, one flow step, EOS threshold -1.0),
but treat them as a starting point, not a mobile performance guarantee.

### Next implementation gate

Before JNI implementation, make an Android NDK compile-only experiment for
the exact source with the server/CLI excluded. Resolve only compiler/linker
errors required for that target; do not fork or alter model inference logic.
Then add JNI lifecycle and streaming wrappers, followed by a model-load test
and a generated-WAV test on an ARM64 Android device. The model's published
native validation is Linux CPU only and makes no ARM/mobile benchmark claim.
