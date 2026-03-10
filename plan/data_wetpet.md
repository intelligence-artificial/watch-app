Now I have a thorough understanding of the codebase. Here is the full dev plan:

***

# WetPet Scuba-Tamagotchi Watchface Redesign Plan

The goal is to evolve the current `TamagotchiWatchFaceService.kt` from a simple retro pixel-art face into a **data-rich "scuba dive computer"-inspired UI** — with curved arc rings wrapping the screen, multiple health data panels, Tamagotchi pet behaviors, and reactive animations driven by Fitbit/Health Services data.

***

## Current State Assessment

The existing [`TamagotchiWatchFaceService.kt`](https://github.com/intelligence-artificial/watch-app/blob/main/wetpet-watchface/watch_faces/src/main/java/com/watchvoice/faces/TamagotchiWatchFaceService.kt) already has: 
- A 12×12 pixel sprite with idle/sleep animations
- A battery arc (top-left quadrant) and a static steps arc (bottom-right)
- A `HealthDataManager` pulling passive heart rate + daily steps from Health Services 
- Basic clock, date, and seconds tick arc

**What's missing:** The arcs are underutilized, there's no heart rate display, no pet mood/state machine, and no complication slots wired up.

***

## Phase 1 — Scuba-Style Arc Ring Layout

**Objective:** Replace the sparse 2-arc layout with a full scuba dive computer ring system — multiple concentric arcs wrapping the circular screen, each dedicated to a specific health metric.

### Arc Zone Map (outer → inner)

| Ring | Metric | Color | Arc Span | Position |
|---|---|---|---|---|
| Outer Ring | Steps / Daily Goal | Cyan `#50e6ff` | 270° sweep (bottom 3/4) | Outermost |
| Second Ring | Heart Rate zone (resting/active/peak) | Green → Orange → Red | 180° left side | Left arc |
| Third Ring | Calories burned | Orange `#ff9f50` | 180° right side | Right arc |
| Inner Ring | Battery | Lime `#50ff78` | 90° top corners | Top only |
| Seconds Ring | Live seconds ticker | Dim white | Full 360° | Innermost |

**Implementation Notes:**
- Each arc should have a **bg track** (dim version) and a **fill arc** that sweeps proportionally to the value
- Draw small **tick marks** at 25%, 50%, 75% on each ring — this is the scuba dial aesthetic
- Use `Paint.Cap.ROUND` for all arcs, strokeWidth `8f` outer to `4f` inner
- Add small **icon labels** (text glyphs) at the start of each arc: `♥ HR`, `👟 STEPS`, `🔥 CAL`, `⚡ BAT`

***

## Phase 2 — Tamagotchi Pet Behavior State Machine

**Objective:** Make the WetPet actually *react* to Fitbit health data. The pet's mood, sprite, and animation should be driven by real sensor values.

### Pet States (add to `WatchPetRenderer`)

```
PetState enum:
  IDLE       → normal HR, normal steps
  HAPPY      → steps > 8000 OR calories > 500
  SLEEPY     → hour 22–6 OR HR < 50 for 5+ mins
  EXCITED    → HR > 120 (active workout)
  SAD        → steps < 1000 by 6pm
  HUNGRY     → calories < 200 by noon
  SICK       → HR anomaly (too high at rest, >100 bpm, no movement)
```

### New Sprites Needed (12×12 pixel art)
- `spriteHappy` — big smile, bouncing more aggressively (+5px bounce)
- `spriteExcited` — star eyes, fast 250ms frame toggle
- `spriteSad` — downturned eyes, slow 1000ms frame toggle
- `spriteHungry` — open mouth, rumble animation (±2px horizontal jitter)
- `spriteSick` — wavy body (use different `bodyDimColor`)

### State Transition Logic
```kotlin
fun computePetState(hr: Int, steps: Int, calories: Int, hour: Int): PetState {
    if (hour in 22..23 || hour in 0..5) return PetState.SLEEPY
    if (hr > 120) return PetState.EXCITED
    if (steps > 8000 || calories > 500) return PetState.HAPPY
    if (steps < 1000 && hour >= 18) return PetState.SAD
    if (calories < 200 && hour >= 12) return PetState.HUNGRY
    if (hr > 100 && steps < 100) return PetState.SICK
    return PetState.IDLE
}
```

The `render()` loop should call `computePetState()` every frame and pick the correct sprite + animation speed.

***

## Phase 3 — Health Data Expansion via HealthDataManager

**Objective:** Wire up more Fitbit/Health Services data types so all the arc rings have real data to display.

### Add to `HealthDataManager.kt`
```kotlin
@Volatile var calories: Int = 0
@Volatile var heartRateVariability: Float = 0f
@Volatile var floorsClimbed: Int = 0
@Volatile var distance: Float = 0f  // meters
```

Add to the `PassiveListenerConfig`:
```kotlin
.setDataTypes(setOf(
    DataType.HEART_RATE_BPM,
    DataType.STEPS_DAILY,
    DataType.CALORIES_DAILY,
    DataType.FLOORS_DAILY,
    DataType.DISTANCE_DAILY,
    DataType.HEART_RATE_VARIABILITY
))
```

Then in the callback handle each type similarly to the existing HR/steps logic .

***

## Phase 4 — Data Panel Zones (Scuba HUD Text)

**Objective:** In the space between the pet sprite and the arc rings, place text data panels — like a scuba computer's digital readouts.

### Layout (for a ~450px round screen)

```
         ┌──────────────────────────┐
         │  [TOP]  12:34    BAT 87% │
         │  [LEFT] ♥ 72bpm          │
         │  [CENTER]   🐾 PET       │
         │  [RIGHT]    🔥 342kcal   │
         │  [BOTTOM] 👟 6,234 steps │
         └──────────────────────────┘
```

- **Top:** Time (large, centered), battery % small next to it
- **Left panel:** Heart rate in BPM with a small color-coded heart icon (green/orange/red)
- **Right panel:** Calories with a flame icon
- **Bottom:** Step count with goal bar underneath
- **Pet zone:** Center 120×120px — unchanged but larger on bigger screens

Use `textSize = 14f` for data labels, `textSize = 22f` for values, all `Typeface.MONOSPACE` for the dive-computer aesthetic.

***

## Phase 5 — Complication Slots (Wear OS Buttons)

**Objective:** Register formal Wear OS complication slots so users can tap zones and customize data displayed, and optionally bind to Fitbit app data via complications.

### Add 4 Complication Slots to `TamagotchiWatchFaceService`

```kotlin
// In createComplicationSlotsManager():
val topSlot = ComplicationSlot.createRoundRectComplicationSlotBuilder(
    id = 0,
    canvasComplicationFactory = ...,
    supportedTypes = listOf(SHORT_TEXT, RANGED_VALUE),
    defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
        SystemDataSources.DATA_SOURCE_WATCH_BATTERY, ComplicationType.SHORT_TEXT
    ),
    bounds = ComplicationSlotBounds(RectF(0.3f, 0.05f, 0.7f, 0.2f))
)
```

Define 4 slots:
- **Top:** Battery / watch battery (default)
- **Bottom:** Step count (default)
- **Left:** Heart rate (default)
- **Right:** Calories / Fitbit complication (default)

Each slot is tappable — tapping opens the companion phone app or the Fitbit data detail screen.

***

## Phase 6 — WetPet XP & Needs System (Gamification)

**Objective:** Give the pet persistent state — hunger, happiness, and XP — that accumulate over the day based on actual movement/health goals, fully in the Tamagotchi spirit.

### PetNeeds data class
```kotlin
data class PetNeeds(
    var xp: Int = 0,           // Total lifetime XP
    var hunger: Float = 1.0f,  // 0.0 (starving) to 1.0 (full) — depletes over time
    var happiness: Float = 1.0f, // driven by steps + HR activity
    var energy: Float = 1.0f   // sleep quality proxy
)
```

### Rule Engine
- Every 1000 steps → `happiness += 0.1`, `xp += 10`
- Every active HR minute (HR > 100) → `energy -= 0.01`, `xp += 5`
- Every hour without movement → `hunger -= 0.05`
- Meeting daily step goal (10k) → big happiness spike, special "GOAL!" animation
- Store in `SharedPreferences` so state persists across screen-off

### Needs Indicators on Watchface
Draw 3 small pixel-art bars (hunger, happiness, energy) below the pet — 5px tall, 40px wide each, color-coded:
- Green (>0.7), Yellow (0.3–0.7), Red (<0.3)

***

## Phase 7 — Ambient Mode Polish

**Objective:** Preserve battery in ambient mode while keeping the scuba aesthetic readable.

- In ambient: show only the time, battery arc (dim), and the **sleep sprite** in grayscale
- Disable all `BlurMaskFilter` (glow effects) in ambient — they drain battery
- Reduce `interactiveDrawModeUpdateDelayMillis` to `60000L` (1 min) in ambient
- Switch all arc colors to `Color.argb(180, 200, 200, 200)` (dim white)

***

## Summary of File Changes

| File | Change |
|---|---|
| `TamagotchiWatchFaceService.kt` | Full renderer rewrite — arc rings, state machine, data panels, 4 complication slots, ambient mode |
| `HealthDataManager.kt` | Add calories, distance, floors, HRV data types |
| New: `PetStateEngine.kt` | Isolated state machine + `PetNeeds` persistence logic |
| New: `WetPetSprites.kt` | All sprite data extracted to a separate file (happy, excited, sad, hungry, sick) |
| `AndroidManifest.xml` | Add `BODY_SENSORS`, `ACTIVITY_RECOGNITION` permissions; register new complication slots |