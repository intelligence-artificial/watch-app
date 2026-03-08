#!/usr/bin/env bash
set -euo pipefail

echo ""
echo "+============================================================+"
echo ":          Voice Notes - Watch Debug Log                      :"
echo "+============================================================+"
echo ""
echo "Showing live logs from the watch app."
echo "Press Ctrl+C to stop."
echo ""

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

echo "[INFO] Clearing old logs..."
adb logcat -c

echo "[INFO] Streaming logs..."
echo ""

adb logcat -s \
  AudioRecorder:* \
  DataLayerSender:* \
  WearableService:* \
  GmsWearable:*
