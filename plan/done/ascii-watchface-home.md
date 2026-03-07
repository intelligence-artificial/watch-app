# ASCII Watchface + Recorder Transition

## Problem

User wants the default screen to be a watchface (not the eyeball), with tap-to-enter recorder mode.

## Changes

### `RecordScreen.kt`

- Added `isInRecorderMode` state: `false` = watchface, `true` = eye recorder
- **Watchface mode**: Full-screen Canvas draws a retro terminal-style clock:
  - `VOICE NOTES v1.0` label, box-drawing borders
  - Large HH:MM time in terminal green with blinking cursor
  - Seconds display, date line
  - `[ TAP TO RECORD ]` amber hint (or sync status if just recorded)
- **Tap**: enters recorder mode AND starts recording immediately
- **Stop** (tap or auto-silence): stops recording AND returns to watchface
- All eye animation improvements preserved (lerp pupil, random blinks, micro-saccades, glow ring)

## Verification

- `gradlew :wear:assembleDebug` — ✅ BUILD SUCCESSFUL
