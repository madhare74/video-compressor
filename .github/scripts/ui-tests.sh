#!/usr/bin/env bash
# Запускается внутри эмулятора: инструментальные тесты и вытягивание скриншотов.
set -uo pipefail
mkdir -p ui-out
gradle --no-daemon connectedDebugAndroidTest 2>&1 | tee ui-out/ui-test.log
status=${PIPESTATUS[0]}
adb pull /sdcard/Android/data/ru.madhare.videocompressor/files/shots ui-out/shots >/dev/null 2>&1 || true
cp -r app/build/reports/androidTests ui-out/reports 2>/dev/null || true
cp -r app/build/outputs/androidTest-results ui-out/results 2>/dev/null || true
echo "$status" > ui-out/EXIT_CODE
exit 0
