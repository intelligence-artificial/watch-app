# Set Up Custom Watch App Complications Checkout

- [ ] Create `StepsComplicationService.kt` in `wear/src/.../tamagotchi/pet/`
  - Supports `SHORT_TEXT` and `RANGED_VALUE`
  - Reads `KEY_DAILY_STEPS` from `wetpet_health_data` SharedPreferences
- [ ] Create `HeartRateComplicationService.kt` in `wear/src/.../tamagotchi/pet/`
  - Supports `SHORT_TEXT`
  - Reads `KEY_HEART_RATE` from `wetpet_health_data` SharedPreferences
- [ ] Update `AndroidManifest.xml` in wear app to register both new services.
- [ ] Update `PetStateManager.kt`'s `requestComplicationUpdates()` to trigger updates for the new services.
- [ ] Modify `watch_face_tamagotchi.xml` (or `watchface.xml`):
  - Steps: Change `defaultSystemProvider` to `EMPTY` and add `defaultDataSource="com.wetpet.watch/com.tamagotchi.pet.StepsComplicationService"`.
  - Heart Rate: Change `defaultSystemProvider` to `EMPTY` and add `defaultDataSource="com.wetpet.watch/com.tamagotchi.pet.HeartRateComplicationService"`.
- [ ] Move checklists and plans to `done/`.
