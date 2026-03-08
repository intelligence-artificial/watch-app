#!/usr/bin/env bash
set -euo pipefail

echo ""
echo "+============================================================+"
echo ":       Watch Voice Recorder - Pull Recordings                :"
echo "+============================================================+"
echo ""

# Set up paths
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# Create local recordings directory
mkdir -p recordings

# Check for ADB
if ! command -v adb &>/dev/null; then
  echo "[ERROR] ADB not found. Run ./setup_sdk.sh first."
  exit 1
fi

echo "[INFO] Checking for connected devices..."
adb devices -l
echo ""

# Pull all .m4a files from app internal storage
echo "[INFO] Pulling recordings from watch..."
echo ""

TEMP_LIST="/tmp/watch_recordings_$$.txt"
adb shell "run-as com.watchvoice.recorder ls files/*.m4a 2>/dev/null" > "$TEMP_LIST" 2>/dev/null || true

FILE_COUNT=0
while IFS= read -r filepath; do
  [ -z "$filepath" ] && continue
  filename=$(basename "$filepath" | tr -d '\r')
  echo "  Pulling: $filename"
  adb shell "run-as com.watchvoice.recorder cat files/$filename" > "recordings/$filename"
  FILE_COUNT=$((FILE_COUNT + 1))
done < "$TEMP_LIST"

if [ "$FILE_COUNT" -eq 0 ]; then
  echo "[INFO] No recordings found on watch."
  echo ""
  echo "  Make sure:"
  echo "  1. The app is installed on the watch"
  echo "  2. You have made at least one recording"
  echo ""
else
  echo ""
  echo "+============================================================+"
  echo ":   Pulled $FILE_COUNT recording(s) to ./recordings/              :"
  echo "+============================================================+"
  echo ""
  echo "  Files saved in: $(pwd)/recordings/"
  echo ""
  echo "  You can now transcribe these with any speech-to-text tool:"
  echo "  - OpenAI Whisper: whisper recordings/*.m4a"
  echo "  - Google Cloud: gcloud ml speech recognize recordings/*.m4a"
  echo "  - Or any local LLM with audio support"
  echo ""
fi

rm -f "$TEMP_LIST"
