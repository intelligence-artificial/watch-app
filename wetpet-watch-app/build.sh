#!/usr/bin/env bash
# Build all WetPet modules
set -euo pipefail
cd "$(dirname "$0")"

echo "╔════════════════════════════════════════════╗"
echo "║  WetPet — Build                            ║"
echo "╚════════════════════════════════════════════╝"

[[ -z "${ANDROID_HOME:-}" ]] && export ANDROID_HOME="$HOME/Android/Sdk"
[[ -z "${JAVA_HOME:-}" ]]    && export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"

if [[ ! -d "$ANDROID_HOME" ]]; then
  echo "❌ ANDROID_HOME not found at $ANDROID_HOME"
  exit 1
fi

echo "  ANDROID_HOME = $ANDROID_HOME"
echo "  JAVA_HOME    = $JAVA_HOME"
echo ""
echo "🔨 Building 3 modules:"
echo "   watch_face — WetPet watch face (WFF resource-only)"
echo "   wear       — WetPet companion app (Compose)"
echo "   mobile     — WetPet phone app (Material 3)"
echo ""

./gradlew assembleDebug --warning-mode=all

echo ""
echo "╔════════════════════════════════════════════╗"
echo "║  BUILD COMPLETE ✓                          ║"
echo "╠════════════════════════════════════════════╣"
echo "║  watch_face: watch_face/build/outputs/apk/ ║"
echo "║  wear:       wear/build/outputs/apk/       ║"
echo "║  mobile:     mobile/build/outputs/apk/     ║"
echo "╚════════════════════════════════════════════╝"
