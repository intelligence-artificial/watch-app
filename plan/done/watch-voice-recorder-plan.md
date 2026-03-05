# Wear OS Voice Recorder App for Google Pixel Watch 2

A minimal Wear OS app: tap a button → record audio → save locally. Recordings are stored on-watch for later retrieval and LLM transcription on a phone or PC.

## User Review Required

> [!IMPORTANT]
> **Architecture choice**: Jetpack Compose for Wear OS + `MediaRecorder` API. This is the simplest stack that runs natively on Pixel Watch 2 (Wear OS 4, API 33). No companion phone app is included in v1 — recordings are retrieved via ADB or a future companion app.

> [!WARNING]
> **Transcription scope**: v1 stores `.m4a` audio files only. LLM-based transcription is **not** included in this build. You pull the files off the watch and transcribe on your PC/phone. If you want on-watch transcription via Android Speech API or Whisper, that's a v2 feature — let me know.

> [!IMPORTANT]
> **Build environment**: Requires OpenJDK 17 + Android SDK (API 33 with Wear OS). Same CLI-driven setup we used for the ToF Measure app. Batch scripts will be provided. **You will need to run `setup_sdk.bat` first if you haven't already.**

---

## Proposed Changes

### Project Scaffolding

#### [NEW] [settings.gradle.kts](file:///d:/Github/watch-app/settings.gradle.kts)

Root Gradle settings — project name `WatchVoiceRecorder`, includes `:app` module.

#### [NEW] [build.gradle.kts](file:///d:/Github/watch-app/build.gradle.kts)

Root build file declaring Android Gradle Plugin 8.2.2 + Kotlin 1.9.22.

#### [NEW] [gradle.properties](file:///d:/Github/watch-app/gradle.properties)

Standard AndroidX + Kotlin code generation settings.

#### [NEW] [gradlew.bat](file:///d:/Github/watch-app/gradlew.bat)

Gradle wrapper script (standard).

#### [NEW] [gradle/wrapper/gradle-wrapper.properties](file:///d:/Github/watch-app/gradle/wrapper/gradle-wrapper.properties)

Points to Gradle 8.2 distribution.

---

### App Module — Core

#### [NEW] [app/build.gradle.kts](file:///d:/Github/watch-app/app/build.gradle.kts)

- `minSdk = 30`, `targetSdk = 33` (Wear OS 3+)
- Jetpack Compose for Wear OS dependencies
- `com.android.application` + `org.jetbrains.kotlin.android` plugins

#### [NEW] [app/src/main/AndroidManifest.xml](file:///d:/Github/watch-app/app/src/main/AndroidManifest.xml)

- Permissions: `RECORD_AUDIO`, `WRITE_EXTERNAL_STORAGE`, `VIBRATE`
- Feature: `android.hardware.microphone`
- Feature: `android.hardware.type.watch` (marks as Wear OS)
- Standalone watch app (`meta-data com.google.android.wearable.standalone = true`)

---

### App Module — Kotlin Sources

#### [NEW] [MainActivity.kt](file:///d:/Github/watch-app/app/src/main/java/com/watchvoice/recorder/MainActivity.kt)

- `ComponentActivity` entry point
- Sets Compose content to `WatchVoiceRecorderApp()`

#### [NEW] [WatchVoiceRecorderApp.kt](file:///d:/Github/watch-app/app/src/main/java/com/watchvoice/recorder/WatchVoiceRecorderApp.kt)

- Main composable with navigation (`SwipeDismissableNavHost`)
- Two screens: **Record** (home) and **Recordings** (list)

#### [NEW] [RecordScreen.kt](file:///d:/Github/watch-app/app/src/main/java/com/watchvoice/recorder/RecordScreen.kt)

- Giant circular record button (red = idle, pulsing green = recording)
- Tap to start, tap again to stop
- Shows elapsed recording time
- Haptic feedback on start/stop
- Button to navigate to recordings list

#### [NEW] [RecordingsScreen.kt](file:///d:/Github/watch-app/app/src/main/java/com/watchvoice/recorder/RecordingsScreen.kt)

- `ScalingLazyColumn` listing all saved recordings
- Each item shows timestamp + duration
- Tap to play back on-watch speaker/BT headphones
- Swipe to delete

#### [NEW] [AudioRecorderService.kt](file:///d:/Github/watch-app/app/src/main/java/com/watchvoice/recorder/AudioRecorderService.kt)

- Wraps `MediaRecorder` with start/stop/release lifecycle
- Saves to app-internal storage as `.m4a` (AAC codec)
- Generates filenames from ISO timestamp

#### [NEW] [AudioPlayerService.kt](file:///d:/Github/watch-app/app/src/main/java/com/watchvoice/recorder/AudioPlayerService.kt)

- Wraps `MediaPlayer` for playback of saved recordings

---

### App Module — Resources

#### [NEW] [app/src/main/res/values/strings.xml](file:///d:/Github/watch-app/app/src/main/res/values/strings.xml)

App name and UI strings.

#### [NEW] [app/src/main/res/mipmap-hdpi/ic_launcher.xml](file:///d:/Github/watch-app/app/src/main/res/mipmap-hdpi/ic_launcher.xml)

Adaptive icon (simple vector mic icon).

---

### Automation Scripts

#### [NEW] [setup_sdk.bat](file:///d:/Github/watch-app/setup_sdk.bat)

Downloads OpenJDK 17 + Android SDK cmdline-tools. Installs `platforms;android-33`, `build-tools;34.0.0`, `platform-tools`. Identical pattern to ToF project but adds Wear OS system image.

#### [NEW] [build.bat](file:///d:/Github/watch-app/build.bat)

Runs `gradlew assembleDebug` with environment setup.

#### [NEW] [install.bat](file:///d:/Github/watch-app/install.bat)

Installs APK via ADB and launches on watch.

#### [NEW] [wireless_setup.bat](file:///d:/Github/watch-app/wireless_setup.bat)

Wireless ADB setup for the watch.

#### [NEW] [pull_recordings.bat](file:///d:/Github/watch-app/pull_recordings.bat)

Pulls all `.m4a` recordings from the watch to a local `recordings/` folder via ADB. This is how you get files off the watch for transcription.

---

### Documentation

#### [NEW] [README.md](file:///d:/Github/watch-app/README.md)

Setup instructions, build/deploy workflow, and usage guide.

---

## Verification Plan

### Automated Tests

```bash
# Build verification — must produce APK successfully
cd d:\Github\watch-app
.\gradlew.bat assembleDebug
# Expected: BUILD SUCCESSFUL + APK at app\build\outputs\apk\debug\app-debug.apk
```

### Manual Verification (on your Pixel Watch 2)

1. **Install**: Connect watch via ADB → run `install.bat`
2. **Permissions**: App should request microphone permission on first launch → grant it
3. **Record**: Tap the big red button → it turns green, timer starts counting
4. **Stop**: Tap again → recording saves, brief haptic buzz confirms
5. **Recordings list**: Navigate to list → new recording appears with timestamp
6. **Playback**: Tap a recording → audio plays through watch speaker / BT
7. **Pull files**: Run `pull_recordings.bat` → `.m4a` files appear in local `recordings/` folder
8. **Transcribe**: Open any `.m4a` in a speech-to-text tool to verify audio quality
