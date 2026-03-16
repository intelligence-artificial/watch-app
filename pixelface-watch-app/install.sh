#!/usr/bin/env bash
# Install PixelFace APKs to devices
# Uninstalls old versions first to prevent duplicates
set -euo pipefail
cd "$(dirname "$0")"

WEAR_APK="wear/build/outputs/apk/debug/wear-debug.apk"
MOBILE_APK="mobile/build/outputs/apk/debug/mobile-debug.apk"

# Old package IDs to remove (prevents duplicates)
OLD_PACKAGES=(
  "com.watchvoice.recorder"
  "com.watchvoice.faces"
  "com.wetpet.watch"
  "com.pixelface.watch"
  "com.pixelface.watch.watchface"
  "com.pixelface.watch.mobile"
  "com.pixelface.watchface"
)

echo "╔════════════════════════════════════════════╗"
echo "║  PixelFace — Install                          ║"
echo "╚════════════════════════════════════════════╝"

if [[ ! -f "$WEAR_APK" ]] || [[ ! -f "$MOBILE_APK" ]]; then
  echo "❌ APKs not found. Run ./build.sh first."
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
  if echo "$line" | grep -qi "product:.*watch\|model:.*watch\|transport_id.*watch"; then
    WATCH_ID=$(echo "$line" | awk '{print $1}')
  elif echo "$line" | grep -qi "device$\|product:"; then
    PHONE_ID=$(echo "$line" | awk '{print $1}')
  fi
done <<< "$(adb devices -l | tail -n +2)"

# ── Uninstall old versions to prevent duplicates ──
echo "🧹 Removing old versions..."
for pkg in "${OLD_PACKAGES[@]}"; do
  if [[ -n "$WATCH_ID" ]]; then
    adb -s "$WATCH_ID" uninstall "$pkg" >/dev/null 2>&1 && echo "   ⌚ Removed $pkg from watch" || true
  fi
  if [[ -n "$PHONE_ID" ]]; then
    adb -s "$PHONE_ID" uninstall "$pkg" >/dev/null 2>&1 && echo "   📱 Removed $pkg from phone" || true
  fi
done
echo ""

# Install wear APK (includes watch face + companion app)
if [[ -n "$WATCH_ID" ]]; then
  echo "⌚ Installing PixelFace watch app + face..."
  adb -s "$WATCH_ID" install -r "$WEAR_APK"
  echo "   ✓ Watch app installed"
else
  echo "⚠️  No watch detected. Install manually:"
  echo "   adb install -r $WEAR_APK"
fi

# Install mobile APK
if [[ -n "$PHONE_ID" ]]; then
  echo "📱 Installing PixelFace phone app..."
  adb -s "$PHONE_ID" install -r "$MOBILE_APK"
  echo "   ✓ Phone app installed"
else
  echo "⚠️  No phone detected. Install manually:"
  echo "   adb install -r $MOBILE_APK"
fi

echo ""
echo "╔════════════════════════════════════════════╗"
echo "║  INSTALL COMPLETE ✓                        ║"
echo "╠════════════════════════════════════════════╣"
echo "║  Watch: PixelFace face + companion app        ║"
echo "║  Phone: PixelFace fitness dashboard           ║"
echo "╚════════════════════════════════════════════╝"
echo ""
echo "To select the watch face:"
echo "  Long-press watch face → Choose 'PixelFace'"
