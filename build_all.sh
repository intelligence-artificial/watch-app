#!/usr/bin/env bash
# Build all WetPet modules (watch face + wear companion + phone app)
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "╔════════════════════════════════════════════╗"
echo "║  WetPet — Full Build                       ║"
echo "╚════════════════════════════════════════════╝"

[[ -z "${ANDROID_HOME:-}" ]] && export ANDROID_HOME="$HOME/Android/Sdk"
[[ -z "${JAVA_HOME:-}" ]]    && export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"

echo ""
echo "🔨 Building wetpet-watch-app (watch_face + wear + mobile)..."
cd "$SCRIPT_DIR/wetpet-watch-app"
./gradlew assembleDebug --warning-mode=all

echo ""
echo "╔════════════════════════════════════════════╗"
echo "║  BUILD COMPLETE ✓                          ║"
echo "╠════════════════════════════════════════════╣"
echo "║  watch_face: WFF resource-only APK         ║"
echo "║  wear:       Compose watch companion       ║"
echo "║  mobile:     Phone voice notes app         ║"
echo "╚════════════════════════════════════════════╝"
echo ""
echo "📦 APKs:"
echo "   watch_face/build/outputs/apk/debug/watch_face-debug.apk"
echo "   wear/build/outputs/apk/debug/wear-debug.apk"
echo "   mobile/build/outputs/apk/debug/mobile-debug.apk"
