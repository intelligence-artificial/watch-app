#!/usr/bin/env bash
# Install all WetPet APKs — watch face + wear app + phone app
# Uninstalls old packages first to prevent duplicates
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

FACE_APK="$SCRIPT_DIR/wetpet-watch-app/watch_face/build/outputs/apk/debug/watch_face-debug.apk"
WEAR_APK="$SCRIPT_DIR/wetpet-watch-app/wear/build/outputs/apk/debug/wear-debug.apk"
MOBILE_APK="$SCRIPT_DIR/wetpet-watch-app/mobile/build/outputs/apk/debug/mobile-debug.apk"

echo "╔════════════════════════════════════════════╗"
echo "║  WetPet — Full Install                     ║"
echo "╚════════════════════════════════════════════╝"

if [[ ! -f "$FACE_APK" ]] || [[ ! -f "$WEAR_APK" ]] || [[ ! -f "$MOBILE_APK" ]]; then
  echo "❌ APKs not found. Run ./build_all.sh first."
  exit 1
fi

echo ""
echo "📱 Available devices:"
adb devices -l
echo ""

# Auto-detect watch and phone
WATCH_ID=""
PHONE_ID=""

while IFS= read -r line; do
  if echo "$line" | grep -qi "product:.*watch\|model:.*watch"; then
    WATCH_ID=$(echo "$line" | awk '{print $1}')
  elif echo "$line" | grep -qi "device$\|product:"; then
    # Non-watch device = phone
    if ! echo "$line" | grep -qi "watch"; then
      PHONE_ID=$(echo "$line" | awk '{print $1}')
    fi
  fi
done <<< "$(adb devices -l | tail -n +2)"

# If no phone detected but only one non-watch device, use it
if [[ -z "$PHONE_ID" ]] && [[ -z "$WATCH_ID" ]]; then
  WATCH_ID=$(adb devices | tail -n +2 | head -1 | awk '{print $1}')
fi

# Remove old packages
echo "🧹 Removing old versions..."
OLD_PACKAGES=("com.watchvoice.recorder" "com.watchvoice.faces" "com.tamagotchi.pet")
for pkg in "${OLD_PACKAGES[@]}"; do
  if [[ -n "$WATCH_ID" ]]; then
    adb -s "$WATCH_ID" uninstall "$pkg" >/dev/null 2>&1 && echo "   ⌚ Removed $pkg from watch" || true
  fi
  if [[ -n "$PHONE_ID" ]]; then
    adb -s "$PHONE_ID" uninstall "$pkg" >/dev/null 2>&1 && echo "   📱 Removed $pkg from phone" || true
  fi
done
echo ""

# Install to watch
if [[ -n "$WATCH_ID" ]]; then
  echo "⌚ Installing WetPet watch face..."
  adb -s "$WATCH_ID" install -r "$FACE_APK" && echo "   ✓ Watch face installed" || echo "   ❌ Failed"

  echo "⌚ Installing WetPet wear companion..."
  adb -s "$WATCH_ID" install -r "$WEAR_APK" && echo "   ✓ Wear companion installed" || echo "   ❌ Failed"
else
  echo "⚠️  No watch detected. Install manually:"
  echo "   adb install -r $FACE_APK"
  echo "   adb install -r $WEAR_APK"
fi

# Install to phone
if [[ -n "$PHONE_ID" ]]; then
  echo "📱 Installing WetPet phone app..."
  adb -s "$PHONE_ID" install -r "$MOBILE_APK" && echo "   ✓ Phone app installed" || echo "   ❌ Failed"
else
  echo "⚠️  No phone detected. Install manually:"
  echo "   adb install -r $MOBILE_APK"
fi

echo ""
echo "╔════════════════════════════════════════════╗"
echo "║  INSTALL COMPLETE ✓                        ║"
echo "╠════════════════════════════════════════════╣"
echo "║  Watch: WetPet face + companion app        ║"
echo "║  Phone: WetPet voice notes receiver        ║"
echo "╚════════════════════════════════════════════╝"
echo ""
echo "To select the watch face:"
echo "  Long-press watch face → Choose 'WetPet'"
