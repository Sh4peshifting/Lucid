#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-results
app_apk="$(find test-apks -name app-debug.apk -print -quit)"
test_apk="$(find test-apks -name app-debug-androidTest.apk -print -quit)"
test -n "$app_apk" && test -n "$test_apk"
adb install -r "$app_apk"
adb install -r "$test_apk"
adb shell am instrument -w io.github.sh4peshifting.panelawake.test/androidx.test.runner.AndroidJUnitRunner | tee emulator-results/instrumentation.txt
grep -q '^OK (' emulator-results/instrumentation.txt
adb push "$app_apk" /data/local/tmp/panel-awake-test.apk
adb shell 'CLASSPATH=/data/local/tmp/panel-awake-test.apk app_process / io.github.sh4peshifting.panelawake.DeviceProbe --exercise' | tee emulator-results/power-probe.txt
grep -q 'EXERCISE_OK' emulator-results/power-probe.txt
adb shell am start -n io.github.sh4peshifting.panelawake/.MainActivity
sleep 2
adb exec-out screencap -p > emulator-results/main-screen.png
adb shell rm /data/local/tmp/panel-awake-test.apk
