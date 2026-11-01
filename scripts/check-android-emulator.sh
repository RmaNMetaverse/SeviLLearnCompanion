#!/usr/bin/env bash
set -eu
capture() {
  local result=$?
  mkdir -p artifacts/android-ui
  adb pull /sdcard/Android/data/com.sevil.learncompanion/files/ui-home.png artifacts/android-ui/home.png || true
  adb pull /sdcard/Android/data/com.sevil.learncompanion/files/ui-chat.png artifacts/android-ui/chat.png || true
  exit "$result"
}
trap capture EXIT
apps/android/gradlew -p apps/android connectedDebugAndroidTest --console=plain
