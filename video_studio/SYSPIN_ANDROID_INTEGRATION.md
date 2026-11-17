# SYSPIN Hindi Male — Android Experimental Voice Integration

Status: integration candidate verified; Android runtime integration is not yet enabled.

## Selected model

- Repository: https://huggingface.co/codebucketsolutions/bolo-syspin-hindi-male
- Voice: `syspin-hi-male` (Hindi male, VITS ONNX)
- Model file: `model.onnx` (114 MB)
- Token file: `tokens.txt`
- Runtime metadata: `voice.json`
- Model weights: MIT
- Training corpus: SYSPIN corpus, CC-BY-4.0
- Source model: https://huggingface.co/SYSPIN/vits_Hindi_Male
- Source revision: `7cc1ad298110c97adec25e520a83321c377df5a6`
- Conversion provenance: Bolo export; model card says weights were not retrained or changed.
- Attribution and full licence files must be bundled with any model download/cache and included in app third-party notices.

## Android runtime

Use the upstream sherpa-onnx Android Kotlin API and native runtime, not a Python process or a cloud service. Upstream project: https://github.com/k2-fsa/sherpa-onnx (Apache-2.0 runtime).

The upstream Android TTS Kotlin API exposes `OfflineTtsVitsModelConfig`, `OfflineTtsConfig`, and `OfflineTts`. Before coding against a dependency, pin a tested upstream release and confirm the exact artifact/API for that release. Do not silently substitute a third-party repackaging of native libraries.

## Model download / storage requirements

- Do not put the 114 MB model in Git history.
- Download only after the user selects “Download offline Hindi voice”.
- Download to app-private storage, show progress, support cancellation/retry, verify SHA-256 against a pinned manifest, and atomically rename after validation.
- Keep the original model card, ATTRIBUTION and LICENSE alongside the model.
- Provide a delete-model action and report required storage.
- Do not auto-download on first launch or use mobile data without an explicit user action.

## App behavior

- Gemini remains the default production narration engine and fallback.
- Offline SYSPIN is opt-in and labelled Experimental.
- If the model is missing, corrupt, too slow, or fails synthesis, show the reason and offer Gemini; never silently change the selected voice.
- Preserve the user's original Roman Hinglish script. Do not blindly transliterate player names, team names, score strings, acronyms, URLs, or English cricket terms. Any optional Hindi text normalization must be reversible and tested.
- Synthesize off the UI thread and stream/append PCM to WAV to limit memory use.
- Test output audio sample rate, clipping, pauses, and long-script chunk continuity.

## Device gate

Target device: OPPO CPH2119, Android 12.

Before enabling by default, measure:
- model download and installed size;
- cold-start and warm-start model load time;
- real-time factor and total synthesis time for 5-, 10-, and 20-minute scripts;
- peak memory and thermal/battery impact;
- pronunciation of player names, team names, numbers, scorecards and Hinglish code-switching;
- MP4 mux/playback after generated WAV.

A 114 MB model and a native ONNX runtime can increase APK/app storage and RAM use. Do not claim support on the target phone until physical-device testing passes.

## Release / compliance gate

The MIT model and CC-BY-4.0 corpus notices must be retained. Include sherpa-onnx Apache-2.0 notices and all native/runtime dependency notices. Verify the exact pinned model revision and files before release. Commercial use and redistribution are supported by the stated licences subject to their conditions; this is not a warranty about voice quality, identity rights, or every downstream use.
