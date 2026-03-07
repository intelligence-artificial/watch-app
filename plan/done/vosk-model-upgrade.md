# Upgrade Vosk Model to 0.22-lgraph

## What
Upgraded from `vosk-model-small-en-us-0.15` (~40MB) to `vosk-model-en-us-0.22-lgraph` (~128MB compressed, 204MB extracted) for better transcription accuracy (~20% improvement).

## Changes
- Replaced model files in `mobile/src/main/assets/model/`
- Added model versioning to `TranscriptionService.kt` — auto re-extracts when model version changes
- `MODEL_VERSION` const tracks which model is bundled

## Notes
- First launch after upgrade will re-extract model (~5-10s one-time)
- Future model swaps: just replace assets, bump `MODEL_VERSION` string
