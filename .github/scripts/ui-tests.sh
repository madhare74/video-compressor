#!/usr/bin/env bash
# Запускается внутри эмулятора: инструментальные тесты и вытягивание скриншотов.
set -uo pipefail
mkdir -p ui-out/shots
gradle --no-daemon installDebug installDebugAndroidTest 2>&1 | tee ui-out/ui-install.log
adb shell am instrument -w -r ru.madhare.videocompressor.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee ui-out/ui-test.log
status=0
grep -q "FAILURES!!!" ui-out/ui-test.log && status=1
grep -q "INSTRUMENTATION_CODE: -1" ui-out/ui-test.log || status=1
for f in $(adb shell run-as ru.madhare.videocompressor ls files/shots | tr -d '\r'); do
  adb exec-out run-as ru.madhare.videocompressor cat "files/shots/$f" > "ui-out/shots/$f" 2>>ui-out/adb-pull.txt
done
echo "$status" > ui-out/EXIT_CODE
exit 0
