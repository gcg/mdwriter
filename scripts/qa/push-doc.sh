#!/usr/bin/env bash
# push-doc.sh <serial> <file> : copies a file into the DEBUG app's files/library/ (file names may contain spaces).
set -eu
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
serial="${1:?usage: push-doc.sh <serial> <file>}"; file="${2:?usage: push-doc.sh <serial> <file>}"
name=$(basename "$file")
"$ADB" -s "$serial" shell "run-as me.gcg.mdwriter.debug sh -c 'mkdir -p files/library && cat > \"files/library/$name\"'" < "$file"
