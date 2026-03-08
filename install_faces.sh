#!/usr/bin/env bash
set -euo pipefail

echo ""
echo "+============================================================+"
echo ":          Watch Faces - Install                               :"
echo "+============================================================+"
echo ""

# Set up paths
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# Check for ADB
if ! command -v adb &>/dev/null; then
  echo "[ERROR] ADB not found. Run ./setup_sdk.sh first."
  exit 1
fi

# Find the APK
APK="watch_faces/build/outputs/apk/debug/watch_faces-debug.apk"
if [ ! -f "$APK" ]; then
  echo "[ERROR] APK not found. Run: ./gradlew :watch_faces:assembleDebug"
  exit 1
fi

echo "[INFO] Connected devices:"
adb devices -l
echo ""

# Find watch device
WATCH_SERIAL=""

while IFS=$'\t' read -r serial status; do
  if [ "$status" = "device" ]; then
    MODEL=$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    echo "[INFO] Found: $serial ($MODEL)"
    if echo "$MODEL" | grep -qi "Watch"; then
      WATCH_SERIAL="$serial"
    fi
  fi
done < <(adb devices | tail -n +2)

if [ -z "$WATCH_SERIAL" ]; then
  echo "[ERROR] No watch found. Connect your watch and try again."
  exit 1
fi

echo ""
echo "[INFO] Installing watch faces to $WATCH_SERIAL..."

if adb -s "$WATCH_SERIAL" install -r "$APK"; then
  echo ""
  echo "[SUCCESS] Watch faces installed!"
  echo ""
  echo "To select a watch face:"
  echo "  1. Long-press on your current watch face"
  echo "  2. Swipe to browse watch faces"
  echo "  3. Look for \"Moire\" or \"Void Mesh\""
else
  echo "[ERROR] Install failed."
fi

echo ""
echo "+============================================================+"
echo ":                   Install Complete                           :"
echo "+============================================================+"
echo ""
