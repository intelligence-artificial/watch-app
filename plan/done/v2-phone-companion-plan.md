# V2: Phone Companion App + Auto-Transcription

Restructure to multi-module project. Watch auto-sends recordings to phone via Wear Data Layer API. Phone transcribes offline using Android `SpeechRecognizer`.

## User Review Required

> [!IMPORTANT]
> **Project restructure**: Current `app/` module becomes `wear/` module. New `mobile/` module for the phone app. Both share `applicationId = com.watchvoice.recorder`. Existing watch app behavior is unchanged.

> [!IMPORTANT]
> **Transcription engine**: Using Android's built-in `SpeechRecognizer` — free, offline, no API keys. Already installed on your Pixel 8 Pro. Quality is solid for voice memos.

---

## Proposed Changes

### Project Restructure

#### [MODIFY] [settings.gradle.kts](file:///d:/Github/watch-app/settings.gradle.kts)

- Change `include(":app")` → `include(":wear", ":mobile")`

#### [MODIFY] [build.gradle.kts](file:///d:/Github/watch-app/build.gradle.kts)

- No changes needed (plugin versions stay the same)

#### Rename `app/` → `wear/`

- Move entire `app/` directory to `wear/`
- All existing watch code stays identical

---

### Watch Module — Data Layer Integration

#### [MODIFY] [wear/build.gradle.kts](file:///d:/Github/watch-app/wear/build.gradle.kts)

- Add dependency: `com.google.android.gms:play-services-wearable:18.1.0`

#### [NEW] [DataLayerSender.kt](file:///d:/Github/watch-app/wear/src/main/java/com/watchvoice/recorder/DataLayerSender.kt)

- Uses `ChannelClient` to stream `.m4a` file to connected phone
- Called automatically after `stopRecording()` in `RecordScreen.kt`
- Shows brief "Sending…" / "Sent ✓" status on watch

#### [MODIFY] [RecordScreen.kt](file:///d:/Github/watch-app/wear/src/main/java/com/watchvoice/recorder/RecordScreen.kt)

- After `stopRecording()`, call `DataLayerSender.send(file)` in a coroutine
- Add sync status text below record button ("Sending…" / "Sent ✓" / "Phone not found")

#### [NEW] [wear/src/main/res/values/wear.xml](file:///d:/Github/watch-app/wear/src/main/res/values/wear.xml)

- Declares `wear_capability` for device discovery

---

### Mobile Module — New Phone App

#### [NEW] [mobile/build.gradle.kts](file:///d:/Github/watch-app/mobile/build.gradle.kts)

- Standard Android app with Material 3 Compose
- `minSdk = 28`, `targetSdk = 34`
- Dependencies: `play-services-wearable`, Compose, Coroutines

#### [NEW] [AndroidManifest.xml](file:///d:/Github/watch-app/mobile/src/main/AndroidManifest.xml)

- Phone app manifest with `WearableListenerService` receiver

#### [NEW] [MainActivity.kt](file:///d:/Github/watch-app/mobile/src/main/java/com/watchvoice/recorder/MainActivity.kt)

- Phone entry point

#### [NEW] [VoiceNotesApp.kt](file:///d:/Github/watch-app/mobile/src/main/java/com/watchvoice/recorder/VoiceNotesApp.kt)

- Main Compose app — shows list of transcribed notes
- Material 3 dark theme with premium styling

#### [NEW] [NotesListScreen.kt](file:///d:/Github/watch-app/mobile/src/main/java/com/watchvoice/recorder/NotesListScreen.kt)

- Card-based list of voice notes with timestamp, duration, transcription text
- Tap to expand full transcript
- Swipe to delete

#### [NEW] [NoteDetailScreen.kt](file:///d:/Github/watch-app/mobile/src/main/java/com/watchvoice/recorder/NoteDetailScreen.kt)

- Full transcript view with copy-to-clipboard, share, and playback controls

#### [NEW] [RecordingReceiver.kt](file:///d:/Github/watch-app/mobile/src/main/java/com/watchvoice/recorder/RecordingReceiver.kt)

- `WearableListenerService` — listens for incoming `ChannelClient` connections from watch
- Saves received `.m4a` to app storage
- Triggers transcription automatically

#### [NEW] [TranscriptionService.kt](file:///d:/Github/watch-app/mobile/src/main/java/com/watchvoice/recorder/TranscriptionService.kt)

- Wraps Android `SpeechRecognizer` for offline speech-to-text
- Converts `.m4a` → text string
- Saves result to local JSON store

#### [NEW] [NotesRepository.kt](file:///d:/Github/watch-app/mobile/src/main/java/com/watchvoice/recorder/NotesRepository.kt)

- Local JSON file-based storage for notes (audio path + transcript + timestamp)
- CRUD operations

#### [NEW] [mobile/src/main/res/values/](file:///d:/Github/watch-app/mobile/src/main/res/values/)

- `strings.xml`, `colors.xml`, `themes.xml` — Material 3 dark theme

---

### Batch Scripts

#### [MODIFY] [build.bat](file:///d:/Github/watch-app/build.bat)

- Build both modules: `gradlew assembleDebug` (builds wear + mobile)

#### [MODIFY] [install.bat](file:///d:/Github/watch-app/install.bat)

- Install both APKs: `wear-debug.apk` to watch, `mobile-debug.apk` to phone

#### [NEW] [install_phone.bat](file:///d:/Github/watch-app/install_phone.bat)

- Install just the phone APK separately

---

## Data Flow

```
Watch: Tap Stop → save .m4a → ChannelClient.sendFile()
                                    ↓ (Bluetooth/WiFi)
Phone: WearableListenerService → save .m4a → SpeechRecognizer → save transcript
                                                                      ↓
Phone UI: NotesListScreen shows card with transcript text
```

## Verification Plan

### Automated Tests

```bash
cd d:\Github\watch-app
.\gradlew.bat assembleDebug    # Must build both wear + mobile APKs
```

### Manual Verification

1. Install wear APK on watch, mobile APK on phone
2. Record a voice memo on watch → stop
3. Watch should show "Sending…" → "Sent ✓"
4. Open phone app → new note appears with transcript
5. Tap note to see full transcript, play audio, copy text
