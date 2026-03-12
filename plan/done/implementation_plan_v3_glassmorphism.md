# V3 Glassmorphism Watch Face — Implementation Plan

Redesign the WetPet watch face to use a glassmorphism aesthetic with frosted-glass complication bubbles and a dynamic 4-zone heart rate color system.

## Proposed Changes

### Asset Generation

#### [NEW] [generate_glass_assets.py](file:///home/braindead/github/watch-app/generate_glass_assets.py)

Python script using PIL/numpy to render glassmorphism-style PNG assets:
- **Frosted glass circles** (76×76): Semi-transparent radial gradient fill with a subtle white edge highlight, simulating glass bubbles on the OLED black background. One per complication theme color.
- **ECG waveform overlays** (76×76): Dim PQRST waveform pattern rendered as thin lines inside the HR bubble. 4 color variants (blue, green, amber, red) to match BPM zones.
- **Glass pill** (120×30): Frosted glass rounded rectangle for the battery indicator.
- **Glow ring** (110×110): Soft radial glow PNG for the pet ring.

All generated to `wetpet-watch-app/watch_face/src/main/res/drawable/`.

---

### Kotlin Service Update

#### [MODIFY] [HeartRateComplicationService.kt](file:///home/braindead/github/watch-app/wetpet-watch-app/wear/src/main/java/com/tamagotchi/pet/HeartRateComplicationService.kt)

Add `RANGED_VALUE` support alongside existing `SHORT_TEXT`:
- `minValue = 40f`, `maxValue = 200f`, `value = currentBpm.toFloat()`
- This enables WFF `<Condition>` blocks to access the numeric BPM value directly
- Keep SHORT_TEXT for backwards compatibility

---

### Watch Face XML

#### [MODIFY] [watchface.xml](file:///home/braindead/github/watch-app/wetpet-watch-app/watch_face/src/main/res/raw/watchface.xml)

Major changes to the complication sections:

**Steps complication (slotId=1):** Layer a `glass_bubble_steps.png` background image, then overlay the XML `<Arc>` progress ring and text on top.

**Heart Rate complication (slotId=2):**
- Change `supportedTypes` to include `RANGED_VALUE`
- Add `<Condition>` block checking `[COMPLICATION.RANGED_VALUE_VALUE]`
  - `< 70`: Blue zone — ring stroke `#4488FF`, text `#4488FF`, bg `glass_hr_blue.png`
  - `70–90`: Green zone — ring stroke `#00D68F`, text `#00D68F`, bg `glass_hr_green.png`
  - `90–120`: Amber zone — ring stroke `#FFB800`, text `#FFB800`, bg `glass_hr_amber.png`
  - `≥ 120`: Red zone — ring stroke `#FF3366`, text `#FF3366`, bg `glass_hr_red.png`
- Each zone shows the matching ECG waveform overlay
- Heart icon (`♥`) and BPM label colored to match zone

**Battery (non-complication):** Swap the circle design for a glassmorphism pill shape. Layer `glass_pill_battery.png` behind the arc and text.

**Pet glow ring:** Replace the XML `<Ellipse>` stroke with the rendered `glow_ring_pet.png` for a softer, more premium glow.

---

## Verification Plan

### Build Verification
1. Run `cd /home/braindead/github/watch-app && ./gradlew :wetpet-watch-app:watch_face:assembleDebug` to verify the XML parses and builds
2. Check that all referenced drawable resources exist and are sized correctly

### Visual Verification
- After building, take a screenshot via `adb shell screencap` if a watch/emulator is connected
- Otherwise inspect the generated PNGs visually to confirm the glassmorphism aesthetic

### Manual Testing (requires deploying to watch)
> [!IMPORTANT]
> This requires deploying to a real watch or emulator. Please confirm if you have an emulator set up.

- Deploy to Pixel Watch and verify:
  1. Heart rate bubble changes color as BPM varies
  2. ECG waveform is visible but subtle inside the HR bubble
  3. Glass bubble backgrounds look frosted/premium on OLED black
  4. Steps and battery complications show progress arcs on top of glass backgrounds
  5. Pet glow ring has a soft instead of harsh circle
