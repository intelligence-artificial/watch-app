Now I have the full picture. Here's the comprehensive emotion/status system:

***

# WetPet Comprehensive Emotion & Status System

This system replaces the placeholder `TamagotchiMoodComplicationService.kt` battery-only logic  and expands `HealthDataManager.kt`'s two data types  into a full multi-signal emotion engine.

***

## Architecture Overview

The system is split into three layers:

```
[ HealthDataManager ] → raw sensor values
        ↓
[ PetStatusEngine ]   → computes NeedsVector + resolves Emotion
        ↓
[ WatchPetRenderer ]  → selects sprite, animation, colors, HUD text
```

***

## Layer 1 — Expanded Health Data Input

### `HealthDataManager.kt` — New Data Fields

Add these `@Volatile` properties alongside the existing `heartRate` and `dailySteps`:

```kotlin
@Volatile var heartRate: Int = 0            // bpm
@Volatile var dailySteps: Int = 0           // steps since midnight
@Volatile var calories: Int = 0             // kcal burned today
@Volatile var floorsClimbed: Int = 0        // floors today
@Volatile var distance: Float = 0f          // meters today
@Volatile var heartRateVariability: Float = 0f  // ms (RMSSD)
@Volatile var skinTemperature: Float = 0f   // °C deviation from baseline
@Volatile var spo2: Int = 0                 // blood oxygen %
@Volatile var lastActiveTimestamp: Long = System.currentTimeMillis() // for sedentary tracking
@Volatile var activeMinutesZone2Plus: Int = 0  // cardio + peak zone minutes
```

Update `PassiveListenerConfig` to subscribe to all types:

```kotlin
.setDataTypes(setOf(
    DataType.HEART_RATE_BPM,
    DataType.STEPS_DAILY,
    DataType.CALORIES_DAILY,
    DataType.FLOORS_DAILY,
    DataType.DISTANCE_DAILY,
    DataType.HEART_RATE_VARIABILITY,
    DataType.SKIN_TEMPERATURE,
    DataType.VO2_MAX,
    DataType.RESPIRATORY_RATE
))
```

Add a **sedentary timer**: if no steps increment for 45+ minutes, flag `isSedentary = true`.

***

## Layer 2 — PetStatusEngine.kt (New File)

This is the core logic file. It takes all health inputs and outputs a resolved `PetEmotion` plus a `NeedsVector`.

### NeedsVector — 6-Dimensional Pet State

```kotlin
data class NeedsVector(
    val hunger: Float,      // 0.0–1.0 (1.0 = full)
    val energy: Float,      // 0.0–1.0 (1.0 = energized)
    val happiness: Float,   // 0.0–1.0
    val health: Float,      // 0.0–1.0 (physiological wellness)
    val social: Float,      // 0.0–1.0 (interaction/engagement)
    val stress: Float       // 0.0–1.0 (1.0 = very stressed)
)
```

Each dimension is computed from real Fitbit signals:

| Need | Driven By | Formula |
|---|---|---|
| `hunger` | Calories burned vs. estimated burn | `1.0 - (calorieDeficit / 500f).coerceIn(0,1)` |
| `energy` | HRV (high = rested), sleep hours (via time), SpO2 | `(hrv / 60f + spo2Norm + restHours / 8f) / 3` |
| `happiness` | Steps progress, floors, calories, active minutes | `(stepsRatio + floorsRatio + activeMinRatio) / 3` |
| `health` | SpO2 (should be 95–100%), skin temp deviation, resting HR | penalize for anomalies |
| `social` | User interactions (tap count), time of day | resets each morning |
| `stress` | HR elevated at rest, low HRV, sedentary alert triggered | `hrAtRestScore * sedentaryScore * lowHrvScore` |

***

## Layer 3 — Emotion Resolution

### Primary Emotions (14 States)

Each emotion has a **priority rank** — higher-priority emotions override lower ones when multiple conditions are true simultaneously.

| Priority | Emotion | Trigger Conditions | Pet Behavior |
|---|---|---|---|
| 1 | `CRITICAL` | SpO2 < 90% OR HR > 180 at rest | Red flash, rapid pulse animation, SOS text |
| 2 | `SICK` | Skin temp > +1.5°C deviation OR SpO2 92–94% OR HR > 110 resting for 10+ min | Wavy body, pale color, slow movement |
| 3 | `EXHAUSTED` | HRV < 20ms AND energy < 0.2 AND hour > 14 | Eyes half-closed, dragging feet animation |
| 4 | `STRESSED` | stress > 0.7 (high resting HR + low HRV + sedentary) | Shaking animation, dark tint |
| 5 | `SLEEPY` | Hour 22–6 OR energy < 0.25 | Sleep sprite, Zzz particles, slow bounce |
| 6 | `HUNGRY` | hunger < 0.3 AND hour ≥ 10 | Open-mouth sprite, stomach rumble jitter |
| 7 | `SAD` | happiness < 0.3 AND hour ≥ 18 (evening slacker) | Teardrop pixel, downturned eyes, grey tint |
| 8 | `BORED` | isSedentary AND happiness 0.3–0.5 | Slow idle, yawn animation every 30s |
| 9 | `IDLE` | All needs nominal, no special triggers | Normal idle bounce, alternating sprites |
| 10 | `CONTENT` | All needs > 0.6, no stress | Slight smile variant, warm glow |
| 11 | `ACTIVE` | HR 100–139 (zone 2 cardio) | Jogging animation, cyan body tint |
| 12 | `EXCITED` | HR ≥ 140 (zone 3+) OR step milestone just hit | Star eyes, fast bounce, rainbow glow |
| 13 | `HAPPY` | steps > 8k OR daily goal just met | Big smile, arms-up sprite, confetti particles |
| 14 | `ECSTATIC` | All needs > 0.85 AND step goal 100% met | Full sparkle animation, maximum glow, firework particles |

### Resolution Algorithm

```kotlin
fun resolveEmotion(needs: NeedsVector, health: HealthDataManager, hour: Int): PetEmotion {
    // Critical physiological checks first (always override)
    if (health.spo2 in 1..89 || (health.heartRate > 180 && health.dailySteps < 10)) 
        return PetEmotion.CRITICAL
    if (health.skinTemperature > 1.5f || health.spo2 in 92..94) 
        return PetEmotion.SICK
    if (needs.energy < 0.2f && health.heartRateVariability < 20f) 
        return PetEmotion.EXHAUSTED
    if (needs.stress > 0.7f) 
        return PetEmotion.STRESSED

    // Time-gated states
    if (hour in 22..23 || hour in 0..5 || needs.energy < 0.25f) 
        return PetEmotion.SLEEPY
    if (needs.hunger < 0.3f && hour >= 10) 
        return PetEmotion.HUNGRY

    // Activity states (HR-driven, always check before sedentary)
    if (health.heartRate >= 140) return PetEmotion.EXCITED
    if (health.heartRate in 100..139) return PetEmotion.ACTIVE

    // Positive achievement states
    if (needs.happiness > 0.85f && needs.energy > 0.85f && needs.health > 0.85f) 
        return PetEmotion.ECSTATIC
    if (needs.happiness > 0.7f) 
        return PetEmotion.HAPPY

    // Negative passive states
    if (health.isSedentary && needs.happiness < 0.5f) 
        return PetEmotion.BORED
    if (needs.happiness < 0.3f && hour >= 18) 
        return PetEmotion.SAD

    // Default range
    return if (needs.happiness > 0.6f) PetEmotion.CONTENT else PetEmotion.IDLE
}
```

***

## Layer 4 — Per-Emotion Rendering Spec

Each emotion maps to a concrete set of rendering instructions for the `WatchPetRenderer`:

### Sprite & Animation

| Emotion | Sprite | Frame Rate | Bounce | Special FX |
|---|---|---|---|---|
| `CRITICAL` | Existing idle + red overlay | 100ms (fast flash) | None | Full screen red pulse |
| `SICK` | `spriteSick` (new) | 1500ms | Minimal ±1px | Body color `#b0c0b0` (pale green) |
| `EXHAUSTED` | `spriteExhausted` (new) | 2000ms | None | Drooping eyes, dim body |
| `STRESSED` | `spriteStressed` (new) | 300ms jitter | ±3px horizontal | Dark vignette overlay |
| `SLEEPY` | `spriteSleep` (existing) | 2000ms | None | Zzz particle float up |
| `HUNGRY` | `spriteHungry` (new) | 800ms | ±2px horizontal rumble | Open mouth, stomach indicator |
| `SAD` | `spriteSad` (new) | 1500ms | None | Single teardrop pixel falling |
| `BORED` | `spriteIdle1` (existing) | 3000ms | ±1px | Periodic yawn mouth frame |
| `IDLE` | `spriteIdle1/2` alternating | 500ms | ±3px sin wave | None |
| `CONTENT` | `spriteIdle1/2` alternating | 700ms | ±4px | Warm `#ffd080` glow |
| `ACTIVE` | `spriteActive` (new) | 250ms | ±6px | Cyan body tint, motion blur |
| `EXCITED` | `spriteExcited` (new) | 200ms | ±8px fast | Star eyes, rainbow glow |
| `HAPPY` | `spriteHappy` (new) | 400ms | ±6px | Sparkle pixels around body |
| `ECSTATIC` | `spriteHappy` (new) | 200ms | ±10px | Full confetti, max rainbow glow |

### Arc Ring Color Theming Per Emotion

The arc rings should also react to pet state — not just the sprite:

| Emotion | Arc Color Palette | Background Tint |
|---|---|---|
| `CRITICAL` | All arcs → pulsing red | `#1a0000` red-black |
| `SICK` | Muted grey-green | `#0a0f0a` |
| `STRESSED` | Orange-red arcs | `#120800` |
| `SLEEPY` | Dim purple `#6060a0` | `#05050f` |
| `HAPPY` | Bright cyan + gold | `#020a0a` |
| `ECSTATIC` | Rainbow cycling | `#000a0a` |
| `ACTIVE` | Bright cyan `#00ffee` | `#00080a` |
| Default | Lime `#50ff78` / Cyan `#50e6ff` | `#020206` |

***

## Layer 5 — Mood Persistence & Transition Smoothing

### Preventing Emotion Flapping

A raw HR spike or single low step-count reading should not instantly flip the pet into a new state. Use a **hysteresis + confirmation window**:

```kotlin
class EmotionSmoother(private val confirmationMs: Long = 30_000L) {
    private var candidateEmotion: PetEmotion = PetEmotion.IDLE
    private var candidateSince: Long = 0L
    var currentEmotion: PetEmotion = PetEmotion.IDLE
        private set

    fun update(resolved: PetEmotion) {
        val now = System.currentTimeMillis()
        if (resolved != candidateEmotion) {
            candidateEmotion = resolved
            candidateSince = now
        }
        // Only commit to new emotion after 30 seconds of sustained signal
        if (resolved == candidateEmotion && now - candidateSince >= confirmationMs) {
            currentEmotion = resolved
        }
    }
}
```

**Exception:** `CRITICAL` and `EXCITED` bypass the smoother and take effect immediately.

### SharedPreferences Persistence

Store the `NeedsVector` + XP daily to survive watch restarts:

```kotlin
// Save every 5 minutes
prefs.edit()
    .putFloat("hunger", needs.hunger)
    .putFloat("happiness", needs.happiness)
    .putFloat("energy", needs.energy)
    .putFloat("health", needs.health)
    .putFloat("stress", needs.stress)
    .putInt("xp_total", xpTotal)
    .putLong("last_saved", System.currentTimeMillis())
    .apply()
```

Reset `hunger`, `happiness`, and `social` to their baseline at midnight. `xp_total` is cumulative and never resets.

***

## Layer 6 — Status HUD Text System

Below the pet sprite, render a **2-line status message** that changes per emotion. These display inside the scuba-style ring layout in the bottom data panel zone:

| Emotion | Line 1 | Line 2 |
|---|---|---|
| `CRITICAL` | `⚠ ALERT` | `CHECK HEALTH` |
| `SICK` | `feeling ill...` | `rest up 🤒` |
| `EXHAUSTED` | `so tired...` | `HRV low` |
| `STRESSED` | `stressed out` | `take a breath` |
| `SLEEPY` | `sleepy time` | `Zzz...` |
| `HUNGRY` | `feed me!` | `low calories` |
| `SAD` | `feeling down` | `move around!` |
| `BORED` | `bored...` | `go for a walk` |
| `IDLE` | `all good` | `let's move!` |
| `CONTENT` | `feeling good` | `keep it up 😊` |
| `ACTIVE` | `in the zone!` | `HR: [bpm] bpm` |
| `EXCITED` | `LET'S GO!!` | `🔥 [HR] bpm` |
| `HAPPY` | `great day!` | `[steps] steps` |
| `ECSTATIC` | `GOAL SMASHED!` | `you're amazing` |

***

## New Files Summary

| File | Purpose |
|---|---|
| `PetStatusEngine.kt` | `NeedsVector` computation + `resolveEmotion()` + `EmotionSmoother` |
| `PetEmotion.kt` | Enum of all 14 states with metadata (priority, bypassSmoother flag) |
| `PetNeeds.kt` | Data class + SharedPreferences save/load/reset logic |
| `WetPetSprites.kt` | All 9 new sprite arrays extracted from the renderer |
| Updated `HealthDataManager.kt` | 8 new data fields + expanded PassiveListenerConfig |
| Updated `TamagotchiMoodComplicationService.kt` | Replace battery-only logic with full `PetStatusEngine` output |