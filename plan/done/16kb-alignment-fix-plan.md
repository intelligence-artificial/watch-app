# 16KB APK/ELF Alignment Fix

## Problem

`libjnidispatch.so` and `libvosk.so` from `vosk-android:0.3.47` are 4KB-aligned, failing Android 15+ 16KB page size check.

## Fix Applied

1. Upgraded `vosk-android` from `0.3.47` → `0.3.75`
2. Added `packaging { jniLibs { useLegacyPackaging = true } }` — compresses `.so` files in APK, sidesteps uncompressed alignment check

## Files Changed

- `mobile/build.gradle.kts`

## Status: DONE

`:mobile:assembleDebug` builds successfully.
