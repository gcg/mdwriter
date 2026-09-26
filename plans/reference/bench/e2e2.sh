#!/bin/bash
# AOT-compiled rerun of the full matrix (release APK, compiled with -m speed).
cd "$(dirname "$0")"
ADB="$HOME/Library/Android/sdk/platform-tools/adb -s emulator-5554"
$ADB shell cmd package compile -m speed -f dev.bench.mdtext
OUT=e2e-results-aot.txt
: > $OUT
for size in 20000 100000 300000; do
  for mode in edittext-plain edittext-fast compose-plain compose-state compose-ot edittext; do
    edits=16; TO=150
    if [ "$size" = "300000" ]; then case $mode in compose-*|edittext) edits=8; TO=240;; esac; fi
    echo "== $mode $size" >> $OUT
    ./run.sh "e2e|$size|$mode" $TO --es mode $mode --ei size $size --ei edits $edits | grep -E 'RESULT|editable class|FATAL|Exception' >> $OUT
  done
done
echo ALLDONE >> $OUT
