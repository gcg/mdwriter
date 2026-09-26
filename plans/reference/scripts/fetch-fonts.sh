#!/usr/bin/env bash
# Downloads the 12 iA Writer static TTFs + OFL licence into the app module. Idempotent. Run from the repo root.
set -euo pipefail
base='https://raw.githubusercontent.com/iaolo/iA-Fonts/master'
dst=app/src/main/res/font; lic=app/src/main/assets/licenses
mkdir -p "$dst" "$lic"
fetch() { [ -s "$2" ] || curl -fsSL --retry 3 -o "$2" "$1"; }
for fam in Duo Quattro Mono; do
  low=$(echo "$fam" | tr '[:upper:]' '[:lower:]')
  for style in Regular:regular Bold:bold Italic:italic BoldItalic:bold_italic; do
    src=${style%%:*}; name=${style##*:}
    fetch "$base/iA%20Writer%20$fam/Static/iAWriter${fam}S-$src.ttf" "$dst/${low}_${name}.ttf"
  done
done
fetch "$base/iA%20Writer%20Duo/LICENSE.md" "$lic/iA-Writer-fonts-OFL.txt"
total=$(cat "$dst"/*.ttf | wc -c | tr -d ' ')
echo "fonts: $(ls "$dst"/*.ttf | wc -l | tr -d ' ') files, $total bytes (expected 12 files, 1303688 bytes)"
[ "$total" = "1303688" ] || { echo "SIZE MISMATCH - check the downloads" >&2; exit 1; }
