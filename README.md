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

---

## Voice Recorder — Debugging

### Connect Watch (WiFi)

```bash
adb connect 10.0.0.57:37287
```

### Screenshot

```bash
adb -s 10.0.0.57:37287 exec-out screencap -p > watch_screenshot.png
```

### Live Logcat (Voice Recorder)

Run this **before** tapping record, then tap and watch the output:

```bash
adb -s 10.0.0.57:37287 logcat -c && \
adb -s 10.0.0.57:37287 logcat -v time \
  'AudioRecorder:*' 'RecordScreen:*' 'DataLayerSender:*' 'AndroidRuntime:*' '*:E'
```

### Save Logcat to File

```bash
adb -s 10.0.0.57:37287 logcat -c && \
adb -s 10.0.0.57:37287 logcat -v time \
  'AudioRecorder:*' 'RecordScreen:*' 'DataLayerSender:*' 'AndroidRuntime:*' '*:E' \
  > /tmp/watch_crash.log &
# Tap record, wait for crash, then:
kill %1 && cat /tmp/watch_crash.log
```

### Check Permissions

```bash
adb -s 10.0.0.57:37287 shell dumpsys package com.watchvoice.recorder | grep -A5 "runtime permissions"
```

### Grant Permissions Manually

```bash
adb -s 10.0.0.57:37287 shell pm grant com.watchvoice.recorder android.permission.RECORD_AUDIO
```

### Build + Install + Debug (All-in-One Script)

```bash
./debug_recorder.sh --build
```

### Build & Install Only

```bash
bash note-taking-app/build.sh && bash note-taking-app/install.sh
```

---

## WetPet Watch Face — Pet Complication Sync

The watch face displays the pet sprite from the wear app via the **WFF ComplicationSlot** system. Here's how it works and what NOT to break.

### Architecture

```
┌─────────────────────────────┐      ┌──────────────────────────────┐
│  watch_face APK             │      │  wear APK (com.wetpet.watch) │
│  (com.tamagotchi.wetpet.    │      │                              │
│   watchface)                │      │  PetComplicationService.kt   │
│                             │      │  ├─ reads SharedPreferences  │
│  watchface.xml              │      │  │  (pet_type, color, mood)  │
│  └─ ComplicationSlot        │◄─────│  ├─ builds sprite name       │
│     primaryDataSource=      │      │  │  e.g. "dog_idle_2_green"  │
│     "com.wetpet.watch/      │      │  └─ sends SmallImage Icon    │
│      com.tamagotchi.pet.    │      │                              │
│      PetComplicationService"│      │  HealthDataManager.kt        │
│                             │      │  └─ calls requestUpdate()    │
│  [COMPLICATION.SMALL_IMAGE] │      │     on pet state change      │
│  renders the received icon  │      │                              │
└─────────────────────────────┘      └──────────────────────────────┘
```

### Critical Rules (Don't Break These)

1. **Use `primaryDataSource`, NOT `defaultDataSource`**
   - `defaultDataSource` is just a hint — the system ignores it
   - `primaryDataSource` force-locks the slot to our service
   - Also set `primarySource` inside `DefaultProviderPolicy`

2. **Do NOT add `EMPTY` to `supportedTypes`**
   - WFF renders ALL matching Complication type layouts simultaneously
   - If you have both `SMALL_IMAGE` and `EMPTY`, the EMPTY blob renders ON TOP of the dog
   - Only use `supportedTypes="SMALL_IMAGE"`

3. **Component name must match exactly**
   - Watch face XML: `com.wetpet.watch/com.tamagotchi.pet.PetComplicationService`
   - Wear manifest: service `.PetComplicationService` in namespace `com.tamagotchi.pet`, applicationId `com.wetpet.watch`
   - Don't change namespace or applicationId without updating both

4. **AOD pet sprite must use `alpha="0"` default**
   - Set `alpha="0"` on the `PartImage` element itself
   - Use `<Variant mode="AMBIENT" target="alpha" value="60"/>` to show only in AOD
   - `<Variant mode="INTERACTIVE" target="alpha" value="0"/>` alone can bleed through in the editor

5. **No fallback pet sprites in the watch face XML**
   - The complication handles everything
   - Extra static `PartImage` elements with pet sprites will overlap the complication

### How Pet Changes Sync

1. User changes pet in wear app → writes to `wetpet_state.xml` SharedPreferences
2. `HealthDataManager.start()` calls `PetComplicationService.requestUpdate(context)`
3. System calls `onComplicationRequest()` on the service
4. Service reads `pet_type`, `color_theme`, `emotion` from prefs
5. Builds sprite name (e.g. `dog_idle_2_green`) → sends as `SmallImage` Icon
6. Watch face renders via `[COMPLICATION.SMALL_IMAGE]`

### After Fresh Install

The complication may not bind immediately:
1. Open the WetPet wear app once (triggers `requestUpdate()`)
2. If still not showing, long-press watch face → edit → tap complication → select "WetPet"

### Debugging

```bash
# Check if complication service is being called
adb logcat -s "PetCompSvc:D"

# Check pet state in SharedPreferences
adb shell "run-as com.wetpet.watch cat shared_prefs/wetpet_state.xml"

# Verify service registration
adb shell pm dump com.wetpet.watch | grep PetComplication
```
