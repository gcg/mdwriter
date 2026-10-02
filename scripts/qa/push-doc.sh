#!/usr/bin/env bash
# push-doc.sh <serial> <file> : copies a file into the DEBUG app's files/library/.
set -eu
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
serial="${1:?usage: push-doc.sh <serial> <file>}"; file="${2:?usage: push-doc.sh <serial> <file>}"
"$ADB" -s "$serial" shell "run-as dev.mdwriter.debug sh -c 'mkdir -p files/library && cat > files/library/$(basename "$file")'" < "$file"
