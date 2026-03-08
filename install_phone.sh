#!/usr/bin/env bash
set -euo pipefail

echo ""
echo "+============================================================+"
echo ":          Voice Notes - Install Phone App                    :"
echo "+============================================================+"
echo ""

# Set up paths
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

MOBILE_APK="mobile/build/outputs/apk/debug/mobile-debug.apk"

if [ ! -f "$MOBILE_APK" ]; then
  echo "[ERROR] Phone APK not found at $MOBILE_APK"
  echo "        Please run ./build.sh first."
  exit 1
fi

echo "[INFO] Connected devices:"
adb devices -l
echo ""

# Find a non-watch device serial (phone)
PHONE_SERIAL=""

while IFS=$'\t' read -r serial status; do
  if [ "$status" = "device" ]; then
    MODEL=$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    if ! echo "$MODEL" | grep -qi "Watch"; then
      PHONE_SERIAL="$serial"
      break
    fi
  fi
done < <(adb devices | tail -n +2)

if [ -z "$PHONE_SERIAL" ]; then
  echo "[ERROR] No phone device found. Only watch connected?"
  echo "        Connect your phone via USB or wireless ADB."
  exit 1
fi

echo "[INFO] Using phone: $PHONE_SERIAL"
echo "[INFO] Installing phone APK..."

if adb -s "$PHONE_SERIAL" install -r "$MOBILE_APK"; then
  echo ""
  echo "[SUCCESS] Phone APK installed!"
  echo "[INFO] Launching app..."
  adb -s "$PHONE_SERIAL" shell am start -n com.watchvoice.recorder/.MainActivity
  echo ""
else
  echo ""
  echo "[ERROR] Installation failed."
  exit 1
fi
