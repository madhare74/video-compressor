#!/usr/bin/env bash
# Запускается внутри эмулятора: инструментальные тесты и вытягивание скриншотов.
set -uo pipefail
mkdir -p ui-out
gradle --no-daemon connectedDebugAndroidTest 2>&1 | tee ui-out/ui-test.log
status=${PIPESTATUS[0]}
adb shell ls -R /sdcard/Android/data/ru.madhare.videocompressor/ > ui-out/adb-ls.txt 2>&1 || true
adb pull /data/local/tmp/none ui-out/shots > ui-out/adb-pull.txt 2>&1 || true
if [ ! -d ui-out/shots ]; then
  mkdir -p ui-out/shots
  adb exec-out run-as ru.madhare.videocompressor tar -c -C files shots 2>>ui-out/adb-pull.txt | tar -x -C ui-out 2>>ui-out/adb-pull.txt || true
fi
cp -r app/build/reports/androidTests ui-out/reports 2>/dev/null || true
cp -r app/build/outputs/androidTest-results ui-out/results 2>/dev/null || true
echo "$status" > ui-out/EXIT_CODE
exit 0
