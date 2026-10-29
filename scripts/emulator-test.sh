#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-results
finish() {
  status=$?
  if [ "$status" -ne 0 ]; then
    adb logcat -d -b crash > emulator-results/crash-log.txt || true
    adb shell dumpsys SurfaceFlinger > emulator-results/surfaceflinger.txt || true
  fi
  adb shell rm -f /data/local/tmp/panel-awake-test.apk || true
  exit "$status"
}
trap finish EXIT
app_apk="$(find test-apks -name app-debug.apk -print -quit)"
test_apk="$(find test-apks -name app-debug-androidTest.apk -print -quit)"
test -n "$app_apk" && test -n "$test_apk"
release_apk="$(find test-apks -name lucid-v0.2.0.apk -print -quit)"
if [ -n "$release_apk" ]; then
  adb install upgrade/panel-awake-v0.1.0.apk
  adb install -r "$release_apk"
  adb shell dumpsys package io.github.sh4peshifting.panelawake | grep 'versionName=0.2.0'
  echo 'UPGRADE_OK: signed v0.1.0 -> signed v0.2.0 without uninstall' | tee emulator-results/upgrade.txt
  adb uninstall io.github.sh4peshifting.panelawake
fi
adb install -r "$app_apk"
adb install -r "$test_apk"
adb shell am instrument -w io.github.sh4peshifting.panelawake.test/androidx.test.runner.AndroidJUnitRunner | tee emulator-results/instrumentation.txt
grep -q '^OK (' emulator-results/instrumentation.txt
adb pull /sdcard/Android/data/io.github.sh4peshifting.panelawake/files/lucid-light.png emulator-results/lucid-light.png
adb pull /sdcard/Android/data/io.github.sh4peshifting.panelawake/files/lucid-dark.png emulator-results/lucid-dark.png
adb shell am start -n io.github.sh4peshifting.panelawake/.MainActivity
sleep 2
adb exec-out screencap -p > emulator-results/main-screen.png
adb push "$app_apk" /data/local/tmp/panel-awake-test.apk
adb shell 'CLASSPATH=/data/local/tmp/panel-awake-test.apk app_process / io.github.sh4peshifting.panelawake.DeviceProbe --exercise' | tee emulator-results/power-probe.txt
grep -q 'EXERCISE_OK' emulator-results/power-probe.txt
adb shell am start -n io.github.sh4peshifting.panelawake/.MainActivity
sleep 2
adb exec-out screencap -p > emulator-results/main-screen.png
adb shell rm /data/local/tmp/panel-awake-test.apk
