#!/usr/bin/env bash
set -euo pipefail

echo ""
echo "+============================================================+"
echo ":          Watch Voice Recorder - Setup SDK                   :"
echo "+============================================================+"
echo ""

SDK_ROOT="${ANDROID_HOME:-$HOME/Android/Sdk}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

# Create SDK directory
mkdir -p "$SDK_ROOT/cmdline-tools"

# Check for Java
if ! command -v java &>/dev/null; then
  echo "[INFO] Java not found. Install it with:"
  echo "       sudo apt install openjdk-17-jdk"
  echo "  or use SDKMAN: sdk install java 17.0.2-open"
  exit 1
fi
echo "[INFO] Using Java: $(java -version 2>&1 | head -1)"

# Download and extract cmdline-tools
echo "[INFO] Downloading Command Line Tools..."
TEMP_ZIP="/tmp/cmdline-tools.zip"
curl -L -o "$TEMP_ZIP" "$CMDLINE_TOOLS_URL"
unzip -o "$TEMP_ZIP" -d "$SDK_ROOT/cmdline-tools"

# Organize for sdkmanager (move to 'latest')
if [ -d "$SDK_ROOT/cmdline-tools/latest" ]; then
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
fi
mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"

# Install packages (API 33 for Wear OS)
echo "[INFO] Installing Android SDK packages..."
SDK_BIN="$SDK_ROOT/cmdline-tools/latest/bin"
yes | "$SDK_BIN/sdkmanager" --licenses || true
"$SDK_BIN/sdkmanager" "platform-tools" "build-tools;34.0.0" "platforms;android-34" "platforms;android-33"

# Clean up
rm -f "$TEMP_ZIP"

echo ""
echo "[SUCCESS] SDK setup complete!"
echo "          ANDROID_HOME=$SDK_ROOT"
echo ""
echo "  Add to your ~/.bashrc:"
echo "    export ANDROID_HOME=\"$SDK_ROOT\""
echo "    export PATH=\"\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/latest/bin:\$PATH\""
echo ""
