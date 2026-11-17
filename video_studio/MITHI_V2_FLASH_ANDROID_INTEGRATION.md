# Mithi v2 Flash — Android voice integration

Status: **catalogued; Android synthesis adapter not yet complete.** Do not expose these voices as working production choices until the ONNX graph pipeline and text frontend pass device tests.

## Voice catalogue

Apache-2.0 model family, 24 kHz mono output, Hindi and Punjabi among 11 supported languages.

- Male: Satvik, Anirban, Nakul, Vasant, Siddhant
- Female: Charvi, Ahana, Vaidehi, Malini, Jahnavi

Original model: https://huggingface.co/Oomni/mithi-v2-flash

## Android ONNX export

ONNX conversion: https://huggingface.co/AXEUS6354/mithi-v2-flash-onnx

The ONNX export contains five graphs:
- indic_encoder
- writer_read
- writer_step
- kokoro_text
- kokoro_decode

Each graph has a sibling external `.onnx.data` file. The graph files and external data must be downloaded together and retained in their original relative layout.

The ONNX model card explicitly states that the frontend is not included. Android still needs:
- text normalisation and language routing
- SentencePiece tokenisation
- espeak-ng G2P / phonemisation for the supported languages
- word-level Indic encoder preparation
- autoregressive writer_read / writer_step loop and prosody extraction
- Kokoro text encoder duration alignment
- Kokoro decoder waveform generation
- streaming/chunking, cancellation, memory bounds and WAV output

This is a separate multi-graph runtime. It is **not** compatible with the Sherpa-ONNX VITS adapter used for SYSPIN.

## App integration requirements

1. Download on demand only, over HTTPS, with pinned repository revision and per-file SHA-256 verification.
2. Keep each ONNX graph beside its matching external data file.
3. Store the model in app-private storage; support progress, cancellation, retry, low-storage checks, atomic install and delete.
4. Preserve the original Roman Hinglish script. Prepare a separate reviewed speech-text representation. The Mithi frontend supports Latin/Indic text through its own normaliser; do not reuse SYSPIN's Devanagari-only conversion path by default.
5. Add a voice selector with all ten IDs and language selector for hi/pa (later all 11).
6. Keep Gemini TTS as fallback. Do not silently change voice provider after a failed local synthesis.
7. Include Apache-2.0 licence and upstream acknowledgements in the app's Open Source Licences screen and redistributed model package.
8. Benchmark OPPO CPH2119 / Android 12: model install size, first-load RAM, cold/warm latency, 5–7 minute speech generation, audio continuity, battery/thermal and low-memory recovery.

## Acceptance gate

The model is not production-enabled until:
- all required graph + external-data files are downloaded and checksummed;
- a small Hindi and Punjabi sample is synthesized for all ten voices;
- output is valid 24 kHz mono WAV and can be muxed into both MP4 formats;
- long-script chunk boundaries do not clip or duplicate speech;
- app survives backgrounding, cancellation and low storage;
- physical-device tests pass.

## Licensing

The original model and ONNX conversion identify Apache-2.0. Keep the exact licence and attribution files from both model repositories. Review all dependency notices (ONNX Runtime, SentencePiece, espeak-ng/phonemizer and any bundled frontend assets) separately before redistribution.
