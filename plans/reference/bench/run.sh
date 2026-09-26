#!/bin/bash
# usage: run.sh <donetag> <timeout_s> am-start-extras...
ADB="$HOME/Library/Android/sdk/platform-tools/adb -s emulator-5554"
DONE="$1"; shift; TO="$1"; shift
$ADB logcat -c
$ADB shell am start -S -W -n dev.bench.mdtext/.MainActivity "$@" >/dev/null 2>&1
for i in $(seq 1 $((TO/3))); do
  sleep 3
  if $ADB logcat -d -s MDBENCH:I | grep -q "DONE $DONE"; then break; fi
done
$ADB logcat -d -s MDBENCH:I | grep -E 'RESULT|doc size|spans=|lines=|START' | sed 's/.*MDBENCH: //'
