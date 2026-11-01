#!/usr/bin/env bash
set -eu
capture() {
  local result=$?
  mkdir -p artifacts/android-ui
  adb pull /sdcard/Download/sevil-ui/ui-home.png artifacts/android-ui/home.png || true
  adb pull /sdcard/Download/sevil-ui/ui-chat.png artifacts/android-ui/chat.png || true
  exit "$result"
}
trap capture EXIT
apps/android/gradlew -p apps/android connectedDebugAndroidTest --console=plain
