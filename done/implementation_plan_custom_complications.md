# Implementation Plan: Custom Complications for Watch Face

## Overview
Currently, the watch face taps into the default system providers for `STEP_COUNT` and `HEART_RATE`. On Pixel Watch 2, this links directly to Fitbit and triggers Fitbit sign-in. To decouple the watch face UI from Fitbit, we must serve this data directly from our Watch App (which uses the open `HealthServicesClient`). 

We will achieve this by creating custom Complication Data Sources in the watch app, and pointing the watch face to them.

## Changes

1. **Create `StepsComplicationService.kt`**
   - Read `daily_steps` from `wetpet_health_data` `SharedPreferences`.
   - Implement `SHORT_TEXT` (e.g. `1234`) and `RANGED_VALUE` (e.g. `val = steps, max = 10000`).

2. **Create `HeartRateComplicationService.kt`**
   - Read `heart_rate` from `wetpet_health_data` `SharedPreferences`.
   - Implement `SHORT_TEXT` (e.g. `75 BPM`).

3. **Update `wear/src/main/AndroidManifest.xml`**
   - Add `<service android:name=".StepsComplicationService"...>` and `<service android:name=".HeartRateComplicationService"...>` with the required Wear OS complication intents and metadata (supported types, update period).

4. **Update `PetStateManager.kt`**
   - In `requestComplicationUpdates()`, add calls to `StepsComplicationService.requestUpdate(context)` and `HeartRateComplicationService.requestUpdate(context)` so the watch face updates whenever the background engine ticks.

5. **Update `watchface.xml`**
   - **Steps Slot (slotId="1")**: Change `defaultSystemProvider="STEP_COUNT"` to `defaultSystemProvider="EMPTY"`. Add `defaultDataSource="com.wetpet.watch/com.tamagotchi.pet.StepsComplicationService"`.
   - **Heart Rate Slot (slotId="2")**: Change `defaultSystemProvider="HEART_RATE"` to `defaultSystemProvider="EMPTY"`. Add `defaultDataSource="com.wetpet.watch/com.tamagotchi.pet.HeartRateComplicationService"`.

## Verification Plan
### Automated Tests
- Syntax checking with `./gradlew compileDebugKotlin` or similar.

### Manual Verification
- After successful installation via ADB, tapping the Steps complication or HR complication should no longer bounce to Fitbit. It should behave as our app's custom complication.
