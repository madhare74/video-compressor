#!/usr/bin/env bash
# Запускается внутри эмулятора: инструментальные тесты и вытягивание скриншотов.
set -uo pipefail
mkdir -p ui-out
gradle --no-daemon connectedDebugAndroidTest 2>&1 | tee ui-out/ui-test.log
status=${PIPESTATUS[0]}
mkdir -p ui-out/shots
for f in $(adb shell run-as ru.madhare.videocompressor ls files/shots | tr -d '\r'); do
  adb exec-out run-as ru.madhare.videocompressor cat "files/shots/$f" > "ui-out/shots/$f" 2>>ui-out/adb-pull.txt
done
cp -r app/build/reports/androidTests ui-out/reports 2>/dev/null || true
cp -r app/build/outputs/androidTest-results ui-out/results 2>/dev/null || true
echo "$status" > ui-out/EXIT_CODE
exit 0
