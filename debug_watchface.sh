#!/usr/bin/env bash
# WetPet Watch Face — Debug/Test Script
# Takes a screenshot, checks logcat, and saves results
# Usage: ./debug_watchface.sh [--install] [--logcat]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WATCH_ID=$(adb devices | tail -n +2 | head -1 | awk '{print $1}')
SCREENSHOTS_DIR="$SCRIPT_DIR/.antigravity-outputs"
mkdir -p "$SCREENSHOTS_DIR"

if [[ -z "$WATCH_ID" ]]; then
  echo "❌ No watch connected"
  exit 1
fi

echo "⌚ Watch: $WATCH_ID"
echo ""

# ── Option: Install first ──
if [[ "${1:-}" == "--install" ]] || [[ "${1:-}" == "-i" ]]; then
  echo "📦 Building..."
  "$SCRIPT_DIR/build_all.sh" 2>&1 | tail -5
  echo ""
  echo "📲 Installing..."
  adb -s "$WATCH_ID" install -r "$SCRIPT_DIR/wetpet-watch-app/watch_face/build/outputs/apk/debug/watch_face-debug.apk" 2>&1
  adb -s "$WATCH_ID" install -r "$SCRIPT_DIR/wetpet-watch-app/wear/build/outputs/apk/debug/wear-debug.apk" 2>&1
  echo "   ✓ Installed"
  echo ""
  sleep 4
fi

# ── Wake watch screen ──
echo "🔆 Waking screen..."
adb -s "$WATCH_ID" shell input keyevent KEYCODE_WAKEUP 2>/dev/null || true
sleep 2

# ── Take screenshot ──
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
SCREENSHOT="$SCREENSHOTS_DIR/watchface_${TIMESTAMP}.png"
echo "📸 Taking screenshot..."
adb -s "$WATCH_ID" exec-out screencap -p > "$SCREENSHOT"
echo "   ✓ Saved: $SCREENSHOT"

# ── Check logcat for errors ──
if [[ "${1:-}" == "--logcat" ]] || [[ "${2:-}" == "--logcat" ]] || [[ "${1:-}" == "-l" ]]; then
  echo ""
  echo "🔍 Checking logcat for watch face errors..."
  ERRORS=$(adb -s "$WATCH_ID" logcat -d 2>&1 | grep -iE "FATAL|AndroidRuntime|DeclarativeWatchFace.*Error|DeclarativeWatchFace.*Unable|WatchFace.*crash" | tail -20)
  if [[ -z "$ERRORS" ]]; then
    echo "   ✓ No errors found"
  else
    echo "   ⚠️ Errors detected:"
    echo "$ERRORS"
  fi
fi

# ── Show latest screenshot path ──
echo ""
echo "╔════════════════════════════════════════════╗"
echo "║  DEBUG COMPLETE                            ║"
echo "║  Screenshot: $SCREENSHOT"
echo "╚════════════════════════════════════════════╝"
