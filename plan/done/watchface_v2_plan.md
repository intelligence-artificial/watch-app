# WetPet Watchface V2 — Scuba-Style Redesign

## Goal
Rewrite the `TamagotchiWatchFaceService.kt` renderer to follow scuba dive-computer design rules: concentric arc zones with background tracks, glow fills, tick boundaries, and labeled value readouts.

## Design Rules Applied

| Rule | Implementation |
|------|---------------|
| Always draw a track | Background arc at alpha ~35 of fill color |
| ROUND stroke caps | `strokeCap = Paint.Cap.ROUND` on all arcs |
| Glow = wider transparent copy | Fill arc at `trackWidth + 6f`, alpha 30 |
| Tick boundaries | Short radial lines at arc start/end |
| Max 3 complication rings | Steps (bottom-left), BPM (bottom-right), Battery (top) |
| Labels inside arc zone | Value + label text at arc midpoint, inside the ring |

## Arc Layout (3 complications + seconds ticker)

```
          ┌─────────────────────┐
          │   ╭━ BATTERY ━╮     │
          │  ╱  BAT  87%   ╲    │
          │ ╱                ╲   │
          ││     ┌────────┐   │  │
          ││     │  🐾    │   │  │
          ││     │ PET    │   │  │
          ││     │ 2:34   │   │  │
          ││     └────────┘   │  │
          │ ╲               ╱   │
          │  ╰━STEPS━╮╭━BPM━╯   │
          │    4231   ││  72     │
          └─────────────────────┘
```

## Proposed Changes

### 1. Renderer Rewrite

#### [MODIFY] [TamagotchiWatchFaceService.kt](file:///home/braindead/github/watch-app/wetpet-watchface/watch_faces/src/main/java/com/watchvoice/faces/TamagotchiWatchFaceService.kt)

Full rewrite of `WatchPetRenderer.render()`:

- **Add `drawArcComplication()` helper** (user's exact function, adapted to the renderer)
- **3-ring layout:**
  - Steps → bottom-left (startAngle=150°, sweep=80°, green)
  - BPM → bottom-right (startAngle=310°, sweep=80°, pink/red)
  - Battery → top (startAngle=200°, sweep=140°, cyan)
- **Seconds ticker** → full 360° innermost ring, dim white
- **Pet sprite** → centered, with emotion-driven color tint + bounce
- **Clock** → below the pet, large monospace digits
- **Date** → small, below clock
- **Emotion glow ring** → pulsing ring around the pet zone using pet mood color

**Emotion integration:** Read from `SharedPreferences` (written by the WetPet wear app's `PetStatusEngine`). This connects the watchface to the mood system.

---

### 2. Enhanced HealthDataManager

#### [MODIFY] [HealthDataManager.kt](file:///home/braindead/github/watch-app/wetpet-watchface/watch_faces/src/main/java/com/watchvoice/faces/HealthDataManager.kt)

- Add `calories` field
- Add capability check before registering
- Add `CALORIES_DAILY` to passive listener config

---

### 3. Ambient Mode

The renderer will detect `renderParameters.drawMode == DrawMode.AMBIENT` and:
- Switch all arcs to dim gray (`#30FFFFFF`)
- Hide glow effects and blur filters
- Show only clock + sleep sprite (grayscale)
- Update at 1-minute intervals instead of 500ms

---

### 4. WFF XML (Optional)

The WFF XML (`watch_face_tamagotchi.xml`) defines the declarative watch face. The Kotlin `CanvasRenderer` is the programmatic version. Both exist as alternative approaches. For v2, focus on the Kotlin renderer since it allows the `drawArcComplication()` helper and dynamic emotion state — WFF XML can't do custom Canvas drawing.

## Verification

```bash
./gradlew :watch_faces:assembleDebug
adb install -r watch_faces/build/outputs/apk/debug/watch_faces-debug.apk
```
