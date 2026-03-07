# Eye Icon & Animated Record Button Plan

## Problem

- App icon was a white mic on colored background — generic
- Record button was a plain red/green circle — boring

## Solution

1. **App icon**: Replaced `ic_mic_foreground.xml` in both `wear/` and `mobile/` with a B&W eye vector (white sclera, dark iris, black pupil, white highlight). Background changed to black.
2. **Record button**: Rewrote `RecordScreen.kt` with a Canvas-drawn eye:
   - Almond-shaped eye outline drawn with bezier paths
   - Iris + pupil track wrist rotation via accelerometer sensor
   - Blink animation via keyframes (every 4s)
   - Pulse animation when recording
   - Iris turns green during recording
   - All original record/stop/sync logic preserved

## Files Changed

- `wear/src/main/res/drawable/ic_mic_foreground.xml` — eye vector
- `mobile/src/main/res/drawable/ic_mic_foreground.xml` — eye vector
- `wear/src/main/res/values/colors.xml` — black bg
- `mobile/src/main/res/values/colors.xml` — black bg
- `wear/src/main/java/com/watchvoice/recorder/RecordScreen.kt` — eye button + sensor

## Status: DONE

Both `:wear:assembleDebug` and `:mobile:assembleDebug` compile successfully.
