# Watch Voice Recorder

Wear OS voice recorder app for Google Pixel Watch 2. Tap a button, record your voice, retrieve the `.m4a` files for LLM transcription later.

## Quick Start

```bash
# 1. Setup SDK (first time only)
setup_sdk.bat

# 2. Build + Install on watch
run.bat

# 3. Pull recordings to PC for transcription
pull_recordings.bat
```

## How It Works

1. **Launch** the app on your Pixel Watch 2
2. **Tap** the big red button to start recording → it pulses green
3. **Tap again** to stop → recording is saved with haptic confirmation
4. **View recordings** by tapping the recordings count at the bottom
5. **Pull files** to your PC with `pull_recordings.bat` → files land in `./recordings/`
6. **Transcribe** using Whisper, Google Cloud Speech, or any LLM with audio support

## Project Structure

```
watch-app/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml          # Permissions + Wear OS config
│       ├── java/com/watchvoice/recorder/
│       │   ├── MainActivity.kt          # Entry point
│       │   ├── WatchVoiceRecorderApp.kt # Navigation host
│       │   ├── RecordScreen.kt          # Record button UI
│       │   ├── RecordingsScreen.kt      # Recordings list UI
│       │   ├── AudioRecorderService.kt  # MediaRecorder wrapper
│       │   └── AudioPlayerService.kt    # MediaPlayer wrapper
│       └── res/
│           ├── values/strings.xml
│           └── values/colors.xml
├── build.bat              # Compile debug APK
├── install.bat            # Push APK to watch
├── wireless_setup.bat     # Wireless ADB setup
├── pull_recordings.bat    # Pull .m4a files from watch
├── run.bat                # Build + Install in one step
└── setup_sdk.bat          # First-time SDK setup
```

## Requirements

- Google Pixel Watch 2 with Wear OS 4+
- ADB debugging enabled on the watch
- Windows PC with internet (for first-time SDK download)

## Connecting Your Watch

### Via USB

1. Use the Pixel Watch 2 charging cable
2. Enable Developer Options: Settings → System → About → tap Build Number 7 times
3. Enable ADB debugging: Settings → Developer Options → ADB debugging

### Via WiFi

1. Connect watch to same WiFi as PC
2. Run `wireless_setup.bat` with watch connected via USB
3. Disconnect USB — all subsequent `install.bat` calls work wirelessly

## Transcription (v2 planned)

Currently, recordings are stored as `.m4a` audio files. Pull them off the watch and transcribe using:

```bash
# OpenAI Whisper (local)
pip install openai-whisper
whisper recordings/*.m4a --model base

# Or any speech-to-text API / local LLM
```

Future versions may include on-watch transcription using Android's SpeechRecognizer API.
