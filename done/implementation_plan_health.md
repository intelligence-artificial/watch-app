# Implementation Plan: Migrate Health Data Off Fitbit API

## Overview
The goal is to cleanly decouple the `wetpet-watch-app` from Fitbit API bounds. We'll achieve this by excising `HealthConnectReader.kt` and its associated permissions/dependencies, relying exclusively on `HealthServicesClient` (passive monitoring) which is already efficiently implemented for real-time reads.

## Changes

1. **Remove Health Connect Dependency** (`wear/build.gradle.kts`)
   - [DELETE] `implementation("androidx.health.connect:connect-client:1.0.0-alpha11")`

2. **Clean Manifest Permissions** (`wear/src/main/AndroidManifest.xml`)
   - [DELETE] `<uses-permission android:name="android.permission.health.READ_HEART_RATE_VARIABILITY" />`
   - [DELETE] `<uses-permission android:name="android.permission.health.READ_OXYGEN_SATURATION" />`

3. **Delete Health Connect Reader** (`wear/src.../HealthConnectReader.kt`)
   - [DELETE] `HealthConnectReader.kt`

4. **Refactor Homescreen Integration** (`wear/src.../HomeScreen.kt`)
   - [MODIFY] Remove instantiation of `HealthConnectReader(context)`
   - [MODIFY] Remove the `LaunchedEffect` while loop that fetches Fitbit metrics dynamically.

5. **Clean HealthDataManager** (`wear/src.../HealthDataManager.kt`)
   - [MODIFY] Remove properties `heartRateVariability`, `skinTemperature`, `spo2`.
   - [MODIFY] Remove function `updateFitbitMetrics(...)`.
   - [MODIFY] Update `snapshot()` creation to not pass these values (letting them fall back to their default `0` in `HealthDataSnapshot` class).

## Verification Plan
### Automated Tests
- Syntax compile check loop via Gradle: `./gradlew :wear:compileDebugKotlin`.

### Manual Verification
- A manual review of the `PetStatusEngine` verifies that falling back to `0/0f` handles cleanly (e.g. `If spo2 > 0... else 0.8f`). The engine will simply omit Fitbit logic dynamically.
