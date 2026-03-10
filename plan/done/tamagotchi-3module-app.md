# Tamagotchi Multi-Module App: Watch Face + Watch App + Phone App

Repurpose the existing `note-taking-app/` Data Layer communication pattern and `tamagotchi/watch_faces` pixel-art watch face into a single unified `tamagotchi-app/` sub-project with three modules that talk to each other.

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                    WATCH                              │
│                                                      │
│  ┌─────────────┐      ┌────────────────────────┐     │
│  │ watch_face  │◄────►│         wear           │     │
│  │ (WatchFace  │      │ (Wear Compose App)     │     │
│  │  Service)   │      │ - Customize pet colors │     │
│  │ - Pet sprite│      │ - View quick stats     │     │
│  │ - Clock     │      │ - Name your pet        │     │
│  │ - Arcs      │      └────────────────────────┘     │
│  │ - Steps/HR  │               │                     │
│  └─────────────┘               │ DataLayer API       │
│        │ SharedPrefs           │                     │
│        └───────────────────────┤                     │
└────────────────────────────────┼─────────────────────┘
                                 │ Bluetooth/WiFi
┌────────────────────────────────┼─────────────────────┐
│                    PHONE       │                     │
│  ┌─────────────────────────────┴──────────────────┐  │
│  │              mobile                            │  │
│  │ (Material 3 Compose App)                       │  │
│  │ - Fitness dashboard (steps, exercise, HR)      │  │
│  │ - Pet effects (how fitness → pet happiness)    │  │
│  │ - Pet overview + history                       │  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘
```

**Data Flow:**
- `watch_face` reads `HealthDataManager` (steps, HR) and pet state from `SharedPreferences`
- `wear` app writes pet customization (color, name) to `SharedPreferences` and syncs to `mobile` via DataLayer
- `wear` app sends periodic fitness snapshots (steps, HR, calories) to `mobile` via DataLayer
- `mobile` receives fitness data, computes pet effects (XP, mood), stores history, and syncs back

---

## Proposed Changes

### Project Structure

#### [NEW] [tamagotchi-app/settings.gradle.kts](file:///home/braindead/github/watch-app/tamagotchi-app/settings.gradle.kts)
Three modules: `:watch_face`, `:wear`, `:mobile`. Same plugin/dependency management pattern as `note-taking-app/`.

#### [NEW] [tamagotchi-app/build.gradle.kts](file:///home/braindead/github/watch-app/tamagotchi-app/build.gradle.kts)
Root build file with shared Kotlin + AGP plugin versions.

#### [NEW] [tamagotchi-app/gradle.properties](file:///home/braindead/github/watch-app/tamagotchi-app/gradle.properties)
AndroidX and Jetifier flags.

#### [NEW] [tamagotchi-app/build.sh](file:///home/braindead/github/watch-app/tamagotchi-app/build.sh)
Builds all 3 modules: `./gradlew assembleDebug`

#### [NEW] [tamagotchi-app/install.sh](file:///home/braindead/github/watch-app/tamagotchi-app/install.sh)
Installs `watch_face` + `wear` to watch, `mobile` to phone.

---

### Shared Data Models (used by all 3 modules — copied into each since no shared module)

Each module gets its own copy of these constants (only ~30 lines each, avoids shared module complexity):

- `DataLayerPaths.kt` — paths like `/pet_state`, `/fitness_update`, `/customization`
- `PetMood` enum — `HAPPY`, `CONTENT`, `TIRED`, `HUNGRY`, `CELEBRATING`, `SLEEPING`

---

### Watch Face Module (`watch_face`)

Package: `com.tamagotchi.pet`

#### [NEW] [watch_face/build.gradle.kts](file:///home/braindead/github/watch-app/tamagotchi-app/watch_face/build.gradle.kts)
Ported from existing `tamagotchi/watch_faces/build.gradle.kts`. Same WatchFace + Health Services deps.

#### [NEW] [TamagotchiWatchFaceService.kt](file:///home/braindead/github/watch-app/tamagotchi-app/watch_face/src/main/java/com/tamagotchi/pet/TamagotchiWatchFaceService.kt)
Ported from existing. Enhanced to read pet state from `SharedPreferences` (set by the `wear` app) to pick sprite mood. Shows:
- Pet sprite (animated, mood-aware)
- Clock
- Battery arc (top)
- Steps arc (bottom, from `HealthDataManager`)
- Pet name label

#### [NEW] [HealthDataManager.kt](file:///home/braindead/github/watch-app/tamagotchi-app/watch_face/src/main/java/com/tamagotchi/pet/HealthDataManager.kt)
Ported from existing. Passive step + HR monitoring.

#### [NEW] [TamagotchiMoodComplicationService.kt](file:///home/braindead/github/watch-app/tamagotchi-app/watch_face/src/main/java/com/tamagotchi/pet/TamagotchiMoodComplicationService.kt)
Ported from existing. Enhanced to read actual pet mood from SharedPrefs rather than just battery level.

#### [NEW] [PetStateReader.kt](file:///home/braindead/github/watch-app/tamagotchi-app/watch_face/src/main/java/com/tamagotchi/pet/PetStateReader.kt)
Reads pet state (mood, color theme, name, hunger, energy, XP) from SharedPreferences. The wear app writes these.

#### [NEW] [DataLayerPaths.kt](file:///home/braindead/github/watch-app/tamagotchi-app/watch_face/src/main/java/com/tamagotchi/pet/DataLayerPaths.kt)
Constant paths and keys.

#### [NEW] [AndroidManifest.xml](file:///home/braindead/github/watch-app/tamagotchi-app/watch_face/src/main/AndroidManifest.xml)
Tamagotchi watch face service + complication provider. Stripped of all non-tamagotchi faces.

#### [COPY] Sprite assets from existing `tamagotchi/watch_faces/src/main/res/drawable/`
All `pet_*.png` files.

#### [COPY] `scripts/generate_sprites.py` to `tamagotchi-app/scripts/`

---

### Wear Module (`wear`) — Watch Companion App

Package: `com.tamagotchi.pet`

#### [NEW] [wear/build.gradle.kts](file:///home/braindead/github/watch-app/tamagotchi-app/wear/build.gradle.kts)
Ported from `note-taking-app/wear/build.gradle.kts`. Wear Compose + DataLayer deps.

#### [NEW] [MainActivity.kt](file:///home/braindead/github/watch-app/tamagotchi-app/wear/src/main/java/com/tamagotchi/pet/MainActivity.kt)
Standard Wear Compose entry point.

#### [NEW] [TamagotchiWearApp.kt](file:///home/braindead/github/watch-app/tamagotchi-app/wear/src/main/java/com/tamagotchi/pet/TamagotchiWearApp.kt)
Navigation host with 3 screens: `home`, `customize`, `stats`.

#### [NEW] [HomeScreen.kt](file:///home/braindead/github/watch-app/tamagotchi-app/wear/src/main/java/com/tamagotchi/pet/HomeScreen.kt)
- Large pet sprite (from drawable resources, mood-aware)
- Pet name
- Quick stat chips: "Steps: 4,320" / "Mood: Happy" / "Level: 3"
- Buttons to navigate to Customize and Stats

#### [NEW] [CustomizeScreen.kt](file:///home/braindead/github/watch-app/tamagotchi-app/wear/src/main/java/com/tamagotchi/pet/CustomizeScreen.kt)
- Color theme picker (4 color circles: green 🟢, blue 🔵, pink 🩷, yellow 🟡)
- Pet name text input
- Saves to SharedPreferences (readable by watch_face) and syncs to phone via DataLayer

#### [NEW] [StatsScreen.kt](file:///home/braindead/github/watch-app/tamagotchi-app/wear/src/main/java/com/tamagotchi/pet/StatsScreen.kt)
- Today's steps, heart rate, calories burned
- Pet XP bar, level, hunger/energy meters

#### [NEW] [PetStateManager.kt](file:///home/braindead/github/watch-app/tamagotchi-app/wear/src/main/java/com/tamagotchi/pet/PetStateManager.kt)
- Reads/writes pet state in SharedPreferences
- Computes mood from hunger + energy + steps
- Sends pet state + fitness data to phone via DataLayer

#### [NEW] [FitnessDataSender.kt](file:///home/braindead/github/watch-app/tamagotchi-app/wear/src/main/java/com/tamagotchi/pet/FitnessDataSender.kt)
Adapted from `DataLayerSender.kt`. Sends fitness snapshots (steps, HR, calories, timestamp) to phone.

#### [NEW] [DataLayerPaths.kt](file:///home/braindead/github/watch-app/tamagotchi-app/wear/src/main/java/com/tamagotchi/pet/DataLayerPaths.kt)
Same constants as watch_face copy.

#### [NEW] [AndroidManifest.xml](file:///home/braindead/github/watch-app/tamagotchi-app/wear/src/main/AndroidManifest.xml)
Wear OS activity manifest.

---

### Mobile Module (`mobile`) — Phone Companion App

Package: `com.tamagotchi.pet`

#### [NEW] [mobile/build.gradle.kts](file:///home/braindead/github/watch-app/tamagotchi-app/mobile/build.gradle.kts)
Ported from `note-taking-app/mobile/build.gradle.kts`. Material 3 + DataLayer deps. No Vosk/whisper deps.

#### [NEW] [MainActivity.kt](file:///home/braindead/github/watch-app/tamagotchi-app/mobile/src/main/java/com/tamagotchi/pet/MainActivity.kt)
Standard Material 3 Compose entry point.

#### [NEW] [TamagotchiPhoneApp.kt](file:///home/braindead/github/watch-app/tamagotchi-app/mobile/src/main/java/com/tamagotchi/pet/TamagotchiPhoneApp.kt)
Navigation host with bottom nav: Dashboard, Fitness, Pet Effects.

#### [NEW] [DashboardScreen.kt](file:///home/braindead/github/watch-app/tamagotchi-app/mobile/src/main/java/com/tamagotchi/pet/DashboardScreen.kt)
- Hero pet sprite card (large, mood-aware, animated)
- Pet name, level, XP bar
- Quick stats row: steps today, HR, pet mood emoji

#### [NEW] [FitnessScreen.kt](file:///home/braindead/github/watch-app/tamagotchi-app/mobile/src/main/java/com/tamagotchi/pet/FitnessScreen.kt)
- Steps progress bar (goal: 10,000)
- Heart rate chart (recent readings)
- Active minutes / calories burned
- Daily/weekly summary cards

#### [NEW] [PetEffectsScreen.kt](file:///home/braindead/github/watch-app/tamagotchi-app/mobile/src/main/java/com/tamagotchi/pet/PetEffectsScreen.kt)
- Shows how fitness affects the pet:
  - "Walking 5,000 steps → +10 XP, -5 Hunger"
  - "Heart rate elevated → +2 Energy"
- Effect log cards showing recent changes
- Pet mood history

#### [NEW] [FitnessDataReceiver.kt](file:///home/braindead/github/watch-app/tamagotchi-app/mobile/src/main/java/com/tamagotchi/pet/FitnessDataReceiver.kt)
Adapted from `RecordingReceiver.kt`. `WearableListenerService` that receives fitness snapshots from the watch.

#### [NEW] [FitnessRepository.kt](file:///home/braindead/github/watch-app/tamagotchi-app/mobile/src/main/java/com/tamagotchi/pet/FitnessRepository.kt)
Local JSON-based storage for fitness history. Stores daily snapshots, computes weekly summaries.

#### [NEW] [PetEffectsEngine.kt](file:///home/braindead/github/watch-app/tamagotchi-app/mobile/src/main/java/com/tamagotchi/pet/PetEffectsEngine.kt)
Game logic that translates fitness data into pet stats:
- Steps → XP + hunger decay reduction
- Heart rate elevation → energy boost
- Inactivity → hunger increases, mood drops
- Milestone celebrations (10k steps → pet celebrates)

#### [NEW] [DataLayerPaths.kt](file:///home/braindead/github/watch-app/tamagotchi-app/mobile/src/main/java/com/tamagotchi/pet/DataLayerPaths.kt)
Same constants.

#### [NEW] [AndroidManifest.xml](file:///home/braindead/github/watch-app/tamagotchi-app/mobile/src/main/AndroidManifest.xml)
Phone app manifest with `WearableListenerService`.

---

### Build Scripts

#### [NEW] [build.sh](file:///home/braindead/github/watch-app/tamagotchi-app/build.sh)
Builds all 3 APKs. Runs sprite generation first.

#### [NEW] [install.sh](file:///home/braindead/github/watch-app/tamagotchi-app/install.sh)
Installs `watch_face-debug.apk` + `wear-debug.apk` to watch, `mobile-debug.apk` to phone.

---

## Verification Plan

### Automated Tests
```bash
cd /home/braindead/github/watch-app/tamagotchi-app
./build.sh  # Must compile all 3 modules without errors
```

This is the primary verification — a successful `assembleDebug` confirms all Kotlin compiles, all resources resolve, all manifests are valid, and all dependencies are satisfied.

### Manual Verification
The user will handle deployment at the end. No installation/device testing during this build phase.
