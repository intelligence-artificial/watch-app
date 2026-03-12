# WetPet Watch App → Watch Face Integration & UI Overhaul

The watch app has a rich emotion engine and needs system, but the watch face only receives a pet sprite image via `PetComplicationService`. The emotion status text, needs bars, and theme colors don't propagate to the watch face. The UI on both sides also needs polish.

## Proposed Changes

### Data Bridge: New Complication Services

The WFF watch face (`hasCode=false`) can only receive data through ComplicationSlots. Currently there's 1 custom slot (pet sprite as `SMALL_IMAGE`). We need to add new complication data sources so the watch face can display live pet state.

---

#### [NEW] [EmotionComplicationService.kt](file:///home/braindead/github/watch-app/wetpet-watch-app/wear/src/main/java/com/tamagotchi/pet/EmotionComplicationService.kt)

New `ComplicationDataSourceService` that reads emotion from SharedPrefs and serves it as `SHORT_TEXT`. The watch face will consume this to show the pet's mood (e.g., "all good", "sleepy time", "LET'S GO!!") below the pet sprite.

---

#### [NEW] [NeedsComplicationService.kt](file:///home/braindead/github/watch-app/wetpet-watch-app/wear/src/main/java/com/tamagotchi/pet/NeedsComplicationService.kt)

New `ComplicationDataSourceService` that reads the 3 primary needs (hunger, happiness, energy) from SharedPrefs and serves as `RANGED_VALUE` (value 0–100, min 0, max 100). The watch face will use this to draw need arcs or indicators.

---

#### [MODIFY] [PetComplicationService.kt](file:///home/braindead/github/watch-app/wetpet-watch-app/wear/src/main/java/com/tamagotchi/pet/PetComplicationService.kt)

No functional changes — this already works correctly for the pet sprite.

---

#### [MODIFY] [AndroidManifest.xml](file:///home/braindead/github/watch-app/wetpet-watch-app/wear/src/main/AndroidManifest.xml)

Register `EmotionComplicationService` and `NeedsComplicationService` with:
- `SUPPORTED_TYPES`: `SHORT_TEXT` for emotion, `RANGED_VALUE` for needs
- `UPDATE_PERIOD_SECONDS`: `30` (fast enough for emotion changes)

---

#### [MODIFY] [PetStateManager.kt](file:///home/braindead/github/watch-app/wetpet-watch-app/wear/src/main/java/com/tamagotchi/pet/PetStateManager.kt)

Add a method `requestComplicationUpdate()` that calls `ComplicationDataSourceUpdateRequester` to push updates to all custom complication services when emotion or needs change. Called from the `HomeScreen.kt` update loop.

---

#### [MODIFY] [HomeScreen.kt](file:///home/braindead/github/watch-app/wetpet-watch-app/wear/src/main/java/com/tamagotchi/pet/HomeScreen.kt)

- Call `requestComplicationUpdate()` when emotion changes (in the existing `LaunchedEffect` loop)
- Also save needs to SharedPrefs so the complication services can read them
- **UI redesign**: Larger pet sprite (90dp → 100dp), improved arc ring glow and gradients, glassmorphic card backgrounds for needs bars and stat chips, better spacing and typography

---

### Watch Face XML Updates

#### [MODIFY] [watchface.xml](file:///home/braindead/github/watch-app/wetpet-watch-app/watch_face/src/main/res/raw/watchface.xml)

- Add new `ComplicationSlot` (slotId 3) for emotion status text — positioned below the pet sprite, above the clock
- Add new `ComplicationSlot` (slotId 4) for needs — rendered as 3 small colored arcs or a combined status
- Point `defaultDataSource` to our custom complication services
- Improve overall visual polish: better arc spacing, refined color palette, smoother ambient transitions

---

### Watch App UI Polish

#### [MODIFY] [StatsScreen.kt](file:///home/braindead/github/watch-app/wetpet-watch-app/wear/src/main/java/com/tamagotchi/pet/StatsScreen.kt)

Polish the data presentation — better cards, consistent colors, larger stat values.

#### [MODIFY] [CustomizeScreen.kt](file:///home/braindead/github/watch-app/wetpet-watch-app/wear/src/main/java/com/tamagotchi/pet/CustomizeScreen.kt)

Improve pickers with better borders, selection highlights, and smoother layout.

---

## Verification Plan

### Build Verification
```bash
cd /home/braindead/github/watch-app/wetpet-watch-app
./gradlew :wear:assembleDebug :watch_face:assembleDebug
```
Both modules must compile without errors.

### Device Verification
1. Install both APKs on the connected watch (`adb -s 10.0.0.57:42979 install`)
2. Verify the watch face shows emotion text below the pet sprite
3. Verify the watch face updates when the app's emotion changes
4. Take screenshots of both the watch face and watch app for visual comparison

### Manual Verification (User)
1. Open the WetPet watch app on your watch — confirm the UI looks polished with the new layout
2. Navigate to the watch face — confirm the emotion text and needs data appear
3. Wait for an emotion change (or force one by walking) — confirm the watch face updates
4. Check ambient mode — confirm it still works correctly with the new complications
