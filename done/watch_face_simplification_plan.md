# Watch Face Simplification — Pet Monitor + Analog Clock

Simplify the WetPet watch face from a data-heavy design (complications, digital clock, date, battery) to a **pet-centered monitor** with an **arc-based analog clock** on the outer ring. Tapping the pet launches the full WetPet app for data.

## Design Concept

The watch face becomes a **pet health monitor at a glance**:

| Layer | Element | Description |
|-------|---------|-------------|
| 1 | Background | Dark `#050508` fill with subtle contour rings |
| 2 | **Hour indicator** | Bright arc dot on the outer ring at the current hour position (30° per hour + smooth minute offset) |
| 3 | **Minute sweep arc** | Colored arc sweeping from 12 o'clock clockwise to current minute (existing, kept as-is) |
| 4 | **Seconds orbiter** | Bright dot + comet tail orbiting the outer ring (existing, kept as-is) |
| 5 | 12 tick marks | Hour position markers for readability (existing, kept as-is) |
| 6 | **Glow ring** | Soft glassmorphism ring behind the pet (existing, enlarged) |
| 7 | **Pet sprite** | Large (~160×160) centered pet from `PetComplicationService` |
| 8 | **Tap zone** | Full pet area → launches WetPet app via `HomeLaunchActivity` |

### What's Removed
- ❌ Digital clock (time is now read from the analog ring)
- ❌ Date pill
- ❌ Steps complication
- ❌ Heart Rate complication
- ❌ Battery pill

### Hour Indicator Approach
Instead of traditional clock hands (which would require PNG assets and `AnalogClock` element), keep the existing modern arc-based style:
- **Hour**: A bright, wider arc dot at the current hour angle on the outer ring. Uses WFF expression `[HOUR_0_11] * 30 + [MINUTE] * 0.5` for smooth sub-hour positioning
- **Minute**: Already exists as a sweep arc from 12 o'clock
- **Seconds**: Already exists as the orbiting dot with comet tail

This produces a **clean, minimal analog reading** that matches the pet glow aesthetic.

---

## Proposed Changes

### Watch Face Module

#### [MODIFY] [watchface.xml](file:///home/braindead/github/watch-app/wetpet-watch-app/watch_face/src/main/res/raw/watchface.xml)

Complete rewrite of the watch face XML:
1. **Remove** `DigitalClock` element and date `Group`
2. **Enlarge pet** from 130×130 to 160×160, re-center at `(112, 112)` for 384×384 canvas
3. **Enlarge glow ring** proportionally
4. **Add hour indicator** — a new `PartDraw` with an arc dot using `[HOUR_0_11] * 30 + [MINUTE] * 0.5 - 90` for angle
5. **Change metadata** `CLOCK_TYPE` from `DIGITAL` to `ANALOG`
6. **Expand tap zone** to match enlarged pet area
7. **AOD mode**: dimmed pet + thin minute arc only (no seconds, no hour dot)

#### [MODIFY] [watchface.xml](file:///home/braindead/github/watch-app/wetpet-watch-app/watch_face_v2/src/main/res/raw/watchface.xml)

Mirror the same changes as `watch_face`. This version has more to remove (Steps, HR, Battery complications).

---

## Verification Plan

### Build Verification
```bash
cd /home/braindead/github/watch-app/wetpet-watch-app
./gradlew :watch_face:assembleDebug 2>&1 | tail -5
./gradlew :watch_face_v2:assembleDebug 2>&1 | tail -5
```

### Manual Verification
> [!IMPORTANT]
> After building, install to the Pixel Watch 2 and verify:
> 1. Pet is large and centered
> 2. Glow ring visible around pet
> 3. Minute sweep arc moves correctly
> 4. Seconds orbiter moves correctly
> 5. Hour indicator dot is at correct position
> 6. No text, data, or complications visible
> 7. Tapping pet opens WetPet app
> 8. AOD shows dimmed pet + thin minute arc only
