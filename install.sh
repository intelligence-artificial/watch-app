#!/bin/bash
# Install WetPet watchface + wear app to connected watch
set -e

DEVICE="10.0.0.57:43995"

echo "=== Building watchface ==="
cd wetpet-watch-app
./gradlew :watch_face:assembleDebug 2>&1 | tail -3

echo "=== Installing watchface ==="
adb -s "$DEVICE" install -r watch_face/build/outputs/apk/debug/watch_face-debug.apk

echo "=== Building wear app ==="
./gradlew :wear:assembleDebug 2>&1 | tail -3

echo "=== Installing wear app ==="
adb -s "$DEVICE" install -r wear/build/outputs/apk/debug/wear-debug.apk

echo ""
echo "✅ Both installed! If the watchface isn't active, long-press the watch face to select WetPet."
