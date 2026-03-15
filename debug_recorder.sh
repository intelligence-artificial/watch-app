#!/usr/bin/env bash
# Voice Recorder — Debug Script
# Captures logcat for the voice recorder app on the watch
# Usage: ./debug_recorder.sh [--build]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# ── Find watch ──
echo "🔍 Looking for connected devices..."
adb devices -l
echo ""

WATCH_SERIAL=""
while IFS=$'\t' read -r serial status; do
  if [ "$status" = "device" ]; then
    MODEL=$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    echo "  Found: $serial ($MODEL)"
    if echo "$MODEL" | grep -qi "Watch"; then
      WATCH_SERIAL="$serial"
    fi
  fi
done < <(adb devices | tail -n +2)

if [ -z "$WATCH_SERIAL" ]; then
  echo "❌ No watch found. Connect your watch and try again."
  exit 1
fi

echo ""
echo "⌚ Watch: $WATCH_SERIAL"
echo ""

# ── Option: Build + Install ──
if [[ "${1:-}" == "--build" ]] || [[ "${1:-}" == "-b" ]]; then
  echo "📦 Building note-taking-app..."
  "$SCRIPT_DIR/note-taking-app/build.sh"
  echo ""
  echo "📲 Installing on watch..."
  adb -s "$WATCH_SERIAL" install -r "$SCRIPT_DIR/note-taking-app/wear/build/outputs/apk/debug/wear-debug.apk"
  echo "✅ Installed!"
  echo ""
  sleep 2
fi

# ── Check permissions ──
echo "🔑 Checking permissions..."
PERMS=$(adb -s "$WATCH_SERIAL" shell dumpsys package com.watchvoice.recorder 2>/dev/null | grep -A20 "runtime permissions" || echo "Package not installed")
echo "$PERMS"
echo ""

# ── Grant RECORD_AUDIO if needed ──
echo "🎤 Granting RECORD_AUDIO permission..."
adb -s "$WATCH_SERIAL" shell pm grant com.watchvoice.recorder android.permission.RECORD_AUDIO 2>/dev/null || echo "  (may already be granted or package not installed)"
echo ""

# ── Clear old logs ──
echo "🧹 Clearing old logcat..."
adb -s "$WATCH_SERIAL" logcat -c

# ── Start live logcat ──
echo "╔═══════════════════════════════════════════════════════════╗"
echo "║  LIVE LOGCAT — Voice Recorder                            ║"
echo "║  Now tap the watchface to start recording!               ║"
echo "║  Press Ctrl+C to stop watching logs.                     ║"
echo "╚═══════════════════════════════════════════════════════════╝"
echo ""

adb -s "$WATCH_SERIAL" logcat -v time \
  'AudioRecorder:*' \
  'RecordScreen:*' \
  'DataLayerSender:*' \
  'RecordingReceiver:*' \
  'AndroidRuntime:*' \
  '*:E' \
  '*:F'
