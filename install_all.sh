#!/usr/bin/env bash
# Install WetPet watch APKs — uninstalls old versions first
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

FACE_APK="$SCRIPT_DIR/wetpet-watch-app/watch_face/build/outputs/apk/debug/watch_face-debug.apk"
WEAR_APK="$SCRIPT_DIR/wetpet-watch-app/wear/build/outputs/apk/debug/wear-debug.apk"

# Old package IDs to remove (prevents duplicates)
OLD_PACKAGES=(
  "com.watchvoice.recorder"
  "com.watchvoice.faces"
  "com.tamagotchi.pet"
  "com.tamagotchi.pet.watchface"
  "com.tamagotchi.wetpet.watchface"
)

echo "╔════════════════════════════════════════════╗"
echo "║  WetPet — Install (Watch Only)             ║"
echo "╚════════════════════════════════════════════╝"

if [[ ! -f "$FACE_APK" ]] || [[ ! -f "$WEAR_APK" ]]; then
  echo "❌ APKs not found. Run ./build_all.sh first."
  exit 1
fi

echo ""
echo "📱 Available devices:"
adb devices -l
echo ""

# Auto-detect watch
WATCH_ID=""
while IFS= read -r line; do
  if echo "$line" | grep -qi "product:.*watch\|model:.*watch\|transport_id.*watch"; then
    WATCH_ID=$(echo "$line" | awk '{print $1}')
  fi
done <<< "$(adb devices -l | tail -n +2)"

if [[ -z "$WATCH_ID" ]]; then
  # Fallback: use first device
  WATCH_ID=$(adb devices | tail -n +2 | head -1 | awk '{print $1}')
fi

if [[ -z "$WATCH_ID" ]]; then
  echo "❌ No device found. Connect your watch and try again."
  exit 1
fi

echo "⌚ Target: $WATCH_ID"
echo ""

# Uninstall old versions
echo "🧹 Removing old versions..."
for pkg in "${OLD_PACKAGES[@]}"; do
  adb -s "$WATCH_ID" uninstall "$pkg" 2>/dev/null && echo "   Removed $pkg" || true
done
echo ""

# Install
echo "⌚ Installing WetPet watch face (WFF)..."
adb -s "$WATCH_ID" install -r "$FACE_APK" && echo "   ✓ Watch face installed" || echo "   ❌ Watch face install failed"

echo "⌚ Installing WetPet companion app..."
adb -s "$WATCH_ID" install -r "$WEAR_APK" && echo "   ✓ Companion app installed" || echo "   ❌ Companion app install failed"

echo ""
echo "╔════════════════════════════════════════════╗"
echo "║  INSTALL COMPLETE ✓                        ║"
echo "║  Long-press watch face → Choose 'WetPet'   ║"
echo "╚════════════════════════════════════════════╝"
