# Smooth Eye Animation & Auto-Close on Silence

## Problem

1. Eye animation feels stiff — pupil tracking snaps, blink is abrupt, no organic motion
2. User must manually tap the eye again to stop recording; should auto-stop when they stop talking

## Proposed Changes

### 1. Smoother Eye Animation — `RecordScreen.kt`

#### Pupil Tracking

- Add **lerp smoothing** to the accelerometer-driven pupil offset: instead of directly setting the raw value, `animateTo` / lerp toward the target at ~0.15f per frame → eliminates jitter and gives a "lazy eye" feel
- Use `Animatable` pair for pupilX/pupilY with spring-based snapping

#### Blink

- Replace the single rigid keyframe blink with **randomized blink intervals** (2.5–5s gap) using `LaunchedEffect` + `delay(random)` + `Animatable`
- Add a **double-blink** chance (~20%) for realism
- Use `tween(150ms, EaseInOutCubic)` for the close and `tween(120ms)` for the open — faster than current 200ms which feels mechanical

#### Glow / Breathing

- During recording: add a soft animated **glow ring** (`drawCircle` with alpha pulse) around the eye using the existing `pulseScale` but with a translucent green overlay that fades in/out
- Iris color transition should be **animated** (use `animateColorAsState`) instead of hard-switching between gray/green

#### Micro-saccades

- Add tiny random pupil micro-movements (±2px) every 1-2s to simulate unconscious eye micro-saccades — makes the eye look alive even when the watch is still

---

### 2. Auto-Close on Silence — `AudioRecorderService.kt` + `RecordScreen.kt`

#### `AudioRecorderService.kt`

- Expose `fun getAmplitude(): Int` that returns `recorder?.maxAmplitude ?: 0`
  - `MediaRecorder.maxAmplitude` is a built-in API (resets on each call) — no extra permissions or audio processing needed

#### `RecordScreen.kt`

- Add a `LaunchedEffect(isRecording)` that polls `recorderService.getAmplitude()` every 300ms while recording
- Track `silentMs` counter: if amplitude < threshold (e.g. 500), increment; if above, reset to 0
- When `silentMs >= 3000` (3 seconds of silence), auto-trigger the same stop-recording logic that the click handler uses
- Show a brief "Auto-stopped" text instead of the sync status so the user knows what happened
- Minimum recording time of 2s before auto-stop can trigger (prevents instant stop on initial silence)

---

## Verification Plan

### Automated

```
cd d:\Github\watch-app
.\gradlew :wear:assembleDebug
```

Confirms no compile errors after changes.

### Manual (User deploys to watch)

1. Open the watch app — eye should look noticeably smoother: pupil glides instead of snapping, blinks vary in timing, micro-saccades visible
2. Tap to start recording — iris should smoothly transition to green, glow ring pulses
3. Speak for a few seconds, then stop talking — after ~3s of silence the recording should auto-stop, send to phone, and show "Auto-stopped ✓"
4. Tap to record, immediately stop talking — should NOT auto-stop in the first 2 seconds (minimum recording guard)
