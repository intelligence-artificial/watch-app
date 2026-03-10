#!/usr/bin/env bash
# Build WetPet watch modules only
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "╔════════════════════════════════════════════╗"
echo "║  WetPet — Build (Watch Only)               ║"
echo "╚════════════════════════════════════════════╝"

[[ -z "${ANDROID_HOME:-}" ]] && export ANDROID_HOME="$HOME/Android/Sdk"
[[ -z "${JAVA_HOME:-}" ]]    && export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"

echo ""
echo "🔨 Building wetpet-watch-app (watch face + wear companion)..."
cd "$SCRIPT_DIR/wetpet-watch-app"
./gradlew assembleDebug --warning-mode=all

echo ""
echo "╔════════════════════════════════════════════╗"
echo "║  BUILD COMPLETE ✓                          ║"
echo "║  watch_face: WFF resource-only APK         ║"
echo "║  wear:       Compose companion app         ║"
echo "╚════════════════════════════════════════════╝"
echo ""
echo "📦 APKs:"
echo "   watch_face/build/outputs/apk/debug/watch_face-debug.apk"
echo "   wear/build/outputs/apk/debug/wear-debug.apk"
