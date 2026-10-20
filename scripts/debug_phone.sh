#!/usr/bin/env bash
# Rebuild and update one USB-connected phone without downloading an APK manually.
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -d /home/muss/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2 ]]; then
 export JAVA_HOME=/home/muss/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2
fi
adb_bin="${ADB:-adb}"
if ! command -v "$adb_bin" >/dev/null 2>&1 && [[ -x /home/muss/.local/android-sdk/platform-tools/adb ]]; then
 adb_bin=/home/muss/.local/android-sdk/platform-tools/adb
fi
"$adb_bin" -d get-state
./gradlew :app:assembleDebug
"$adb_bin" -d install -r app/build/outputs/apk/debug/app-debug.apk
"$adb_bin" -d shell am start -n org.vasaigaam.fmb/.MainActivity
