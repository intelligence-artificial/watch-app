#!/usr/bin/env bash
set -euo pipefail

echo ""
echo "+============================================================+"
echo ":          Voice Notes - Phone Debug Log                      :"
echo "+============================================================+"
echo ""
echo "Showing live logs from the Voice Notes phone app."
echo "Make a recording on your watch to see sync activity."
echo "Press Ctrl+C to stop."
echo ""

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

echo "[INFO] Clearing old logs..."
adb logcat -c

echo "[INFO] Streaming logs..."
echo ""

adb logcat -s \
  MainActivity:* \
  RecordingReceiver:* \
  DataLayerSender:* \
  TranscriptionService:* \
  NotesRepository:* \
  WearableService:* \
  GmsWearable:* \
  WearableDataService:*
