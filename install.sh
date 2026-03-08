#!/usr/bin/env bash
set -euo pipefail

echo ""
echo "+============================================================+"
echo ":          Watch Voice Recorder - Install All                 :"
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

echo "[INFO] Connected devices:"
adb devices -l
echo ""

# Parse connected devices
WATCH_SERIAL=""
PHONE_SERIAL=""
DEVICE_COUNT=0

while IFS=$'\t' read -r serial status; do
  if [ "$status" = "device" ]; then
    DEVICE_COUNT=$((DEVICE_COUNT + 1))
    MODEL=$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    echo "[INFO] Found: $serial ($MODEL)"

    if echo "$MODEL" | grep -qi "Watch"; then
      WATCH_SERIAL="$serial"
    else
      PHONE_SERIAL="$serial"
    fi
  fi
done < <(adb devices | tail -n +2)

if [ "$DEVICE_COUNT" -eq 0 ]; then
  echo "[ERROR] No devices found. Connect a device and try again."
  exit 1
fi

echo ""

# Install watch APK (only to watch device)
WEAR_APK="wear/build/outputs/apk/debug/wear-debug.apk"
if [ -f "$WEAR_APK" ]; then
  if [ -n "$WATCH_SERIAL" ]; then
    echo "[INFO] Installing watch APK to $WATCH_SERIAL..."
    if adb -s "$WATCH_SERIAL" install -r "$WEAR_APK"; then
      echo "[SUCCESS] Watch APK installed!"
      adb -s "$WATCH_SERIAL" shell am start -n com.watchvoice.recorder/.MainActivity
    else
      echo "[WARNING] Watch APK install failed."
    fi
  else
    echo "[SKIP] No watch found - skipping watch APK."
  fi
else
  echo "[SKIP] Watch APK not found. Run: ./gradlew :wear:assembleDebug"
fi

echo ""

# Install phone APK (only to phone device)
MOBILE_APK="mobile/build/outputs/apk/debug/mobile-debug.apk"
if [ -f "$MOBILE_APK" ]; then
  if [ -n "$PHONE_SERIAL" ]; then
    echo "[INFO] Installing phone APK to $PHONE_SERIAL..."
    if adb -s "$PHONE_SERIAL" install -r "$MOBILE_APK"; then
      echo "[SUCCESS] Phone APK installed!"
      adb -s "$PHONE_SERIAL" shell am start -n com.watchvoice.recorder/.MainActivity
    else
      echo "[WARNING] Phone APK install failed."
    fi
  else
    echo "[SKIP] No phone found - skipping phone APK."
    echo "       (This prevents installing the phone app on the watch)"
  fi
else
  echo "[SKIP] Phone APK not found. Run: ./gradlew :mobile:assembleDebug"
fi

echo ""
echo "+============================================================+"
echo ":                   Install Complete                           :"
echo "+============================================================+"
echo ""
