#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo "+============================================================+"
echo ":          Note Taking App - Build                            :"
echo "+============================================================+"
echo ""

# Set up paths
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Check for Java
if ! command -v java &>/dev/null; then
  echo "[ERROR] Java not found."
  echo "        Set JAVA_HOME or install: sudo apt install openjdk-17-jdk"
  exit 1
fi

# Check for Android SDK
if [ ! -f "$ANDROID_HOME/platform-tools/adb" ]; then
  echo "[ERROR] Android SDK not found at $ANDROID_HOME"
  echo "        Run ../setup_sdk.sh first to install Android SDK."
  exit 1
fi

echo "[INFO] Java: ${JAVA_HOME:-system}"
echo "[INFO] Android SDK: $ANDROID_HOME"
echo ""

# Download gradle-wrapper.jar if missing
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "[INFO] Downloading Gradle wrapper JAR..."
  mkdir -p gradle/wrapper
  curl -L -o gradle/wrapper/gradle-wrapper.jar \
    https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar
  if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "[ERROR] Failed to download gradle-wrapper.jar"
    exit 1
  fi
  echo "[INFO] Gradle wrapper downloaded successfully"
fi

echo "[INFO] Building debug APKs (wear + mobile)..."
echo ""

# Run Gradle build
if ./gradlew assembleDebug --no-daemon; then
  echo ""
  echo "+============================================================+"
  echo ":                   BUILD SUCCESSFUL                          :"
  echo "+============================================================+"
  echo ""
  echo "  Watch APK: wear/build/outputs/apk/debug/wear-debug.apk"
  echo "  Phone APK: mobile/build/outputs/apk/debug/mobile-debug.apk"
  echo ""
else
  echo ""
  echo "+============================================================+"
  echo ":                    BUILD FAILED                             :"
  echo "+============================================================+"
  echo ""
  echo "  Check the error messages above."
  echo ""
  exit 1
fi
