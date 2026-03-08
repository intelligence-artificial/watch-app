#!/usr/bin/env bash
set -euo pipefail

echo ""
echo "+============================================================+"
echo ":       Watch Voice Recorder - Wireless ADB Setup             :"
echo "+============================================================+"
echo ""
echo "This script helps you set up wireless debugging with your Pixel Watch 2."
echo ""
echo "PREREQUISITES:"
echo "  1. Watch connected via USB with ADB debugging enabled"
echo "  2. Watch and PC on the same WiFi network"
echo ""

# Set up paths
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

if ! command -v adb &>/dev/null; then
  echo "[ERROR] ADB not found. Install Android SDK platform-tools."
  exit 1
fi

echo "[STEP 1] Current devices:"
adb devices -l
echo ""

echo "[STEP 2] Enabling TCP/IP mode on port 5555..."
adb tcpip 5555

echo ""
echo "[STEP 3] Getting device IP address..."
echo ""

# Try to get IP address from device
DEVICE_IP=$(adb shell ip route 2>/dev/null | grep wlan0 | awk '{print $9}' | head -1 | tr -d '\r')

if [ -n "$DEVICE_IP" ]; then
  echo "  Detected IP: $DEVICE_IP"
  echo ""
  echo "[STEP 4] You can now disconnect the USB cable."
  echo ""
  echo "  To connect wirelessly, run:"
  echo "  adb connect $DEVICE_IP:5555"
  echo ""

  read -rp "Connect now? (Y/N): " CONNECT
  if [ "${CONNECT^^}" = "Y" ]; then
    echo ""
    echo "[INFO] Connecting to $DEVICE_IP:5555..."
    sleep 3
    adb connect "$DEVICE_IP:5555"
    echo ""
    echo "[INFO] Verifying connection:"
    adb devices -l
  fi
else
  echo "  Could not auto-detect IP address."
  echo ""
  echo "  Find your watch's IP address:"
  echo "  Settings > Connectivity > Wi-Fi > your network > IP address"
  echo ""

  read -rp "Enter device IP: " MANUAL_IP
  if [ -n "$MANUAL_IP" ]; then
    echo ""
    echo "[INFO] Connecting to $MANUAL_IP:5555..."
    sleep 3
    adb connect "$MANUAL_IP:5555"
    echo ""
    adb devices -l
  fi
fi

echo ""
echo "================================================================"
echo "  Wireless ADB setup complete!"
echo "  You can now run ./install.sh without USB cable."
echo "================================================================"
echo ""
