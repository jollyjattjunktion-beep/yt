# Piper Hindi voice integration — commercial-use gate

Status: **BLOCKED for monetized Preet Sports output until the voice-model rights are cleared.**

Reviewed on 2026-11-16 for the Preet Sports Android Studio app.

## Candidate reviewed

- Voice: `hi_IN-rohan-medium`
- Language / speaker: Hindi (India), one male speaker
- Sample rate: 22,050 Hz
- Model card: https://github.com/rhasspy/piper-samples/blob/master/samples/hi/hi_IN/rohan/medium/MODEL_CARD
- Dataset: IIT Madras IndicTTS, Hindi Mono Male
- Dataset license linked by the model card: https://www.iitm.ac.in/donlab/indictts/downloads/license.pdf
- Training provenance in the model card: fine-tuned from U.S. English Lessac medium.

## Why this is not approved

Piper's upstream licensing guidance says the engine is GPL-3.0-or-later, but Piper does not grant rights to voice models. It says model rights depend on the training data and trainer, and that the model's dataset license line does not cover a fine-tuned base model. It specifically identifies Lessac-derived models as a commercial-use concern and advises checking the full training chain.

References:
- https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/LICENSING.md
- https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/COMMERCIAL_USE.md

The Rohan card explicitly names Lessac as its base. The current upstream commercial-use list does not list this Hindi voice among voices with a fully permissive training chain. Therefore this repository must not redistribute or enable this model for monetized use based only on the apparent MIT license shown for the model repository.

The IIT Madras PDF could not be independently retrieved during this review. No conclusion is made here about the full scope of that dataset license. A legal review and/or written permission covering the dataset, model weights, Lessac-derived training lineage, redistribution in an APK, and commercial generated-audio use is required.

## Safe integration policy

1. Keep the existing Gemini TTS path as the active production voice and fallback.
2. Do not bundle, download automatically, or activate `hi_IN-rohan-medium` in a monetized build.
3. Do not label Piper Hindi as commercially cleared.
4. Piper engine integration may proceed only after choosing a Hindi voice with documented commercial rights across its entire training chain, or obtaining written commercial and redistribution permission from the relevant rights holders.
5. Before shipping a cleared voice, include its exact `MODEL_CARD`, dataset notices, base-model notices, license texts, and attribution in the APK and repository.
6. Any experimental Piper build using a model not yet cleared must be explicitly non-monetized/internal evaluation only and must not be used for published Preet Sports videos.

## Roman Hinglish pronunciation requirements

The future adapter should:
- preserve player names, team names, acronyms, score strings, and English cricket terms;
- convert only Hindi/Hinglish phrases into Devanagari where a validated transliteration is available;
- provide a pronunciation dictionary for names and cricket vocabulary;
- retain the original Roman Hinglish script alongside normalized speech text;
- never silently alter match facts or numbers.

## Device validation gate

The target device is OPPO CPH2119 / Android 12. After a legally cleared model is selected, validate model download/storage, first-run initialization, 30-second voice sample, 5–7 minute script synthesis, WAV playback, memory use, synthesis time, app backgrounding, and video muxing. A successful CI build alone is not a device-runtime pass.

## Decision

**No Piper Hindi model is currently approved by this review for a monetized Preet Sports APK. Gemini remains the production voice.**
