# Watch Face Colors Plan
## Checklist
- [x] Identify where the hour and minute hands are drawn in the Watch Face Service (`PixelFaceWatchFaceService.kt`).
- [x] Change the active minute hand and ghost ring to light grey (`Color.rgb(200, 200, 200)`).
- [x] Change the active hour hand and ghost ring to dark grey (`Color.rgb(80, 80, 80)`).
- [x] Configure code to use SharedPreferences (`minute_color` and `hour_color`) with the matching fallbacks, allowing the user to pick colors in the future.
- [x] Re-build the application to verify it compiles and runs without crashing.
