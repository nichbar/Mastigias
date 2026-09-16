#!/bin/bash
set -e

cd "$(dirname "$0")"

DEVICE=$(adb devices 2>/dev/null | grep -m1 'device$' | awk '{print $1}')
if [ -z "$DEVICE" ]; then
  echo "No Android device connected"
  exit 1
fi

echo "Device: $DEVICE"
echo "Building and installing..."
ANDROID_SERIAL="$DEVICE" ./gradlew installDebug

echo "Launching Mastigias..."
adb -s "$DEVICE" shell am start -n now.link.mastigias.debug/now.link.mastigias.MainActivity
