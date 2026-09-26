#!/usr/bin/env bash
# Downloads Material Symbols (Rounded, wght 400, fill 0, 24dp) Android vector drawables into res/drawable/ic_<name>.xml
# and strips the AppCompat-only android:tint="?attr/colorControlNormal" attribute. Idempotent. Run from the repo root.
set -euo pipefail
names=(left_panel_open left_panel_close more_vert more_horiz arrow_back share undo redo search edit_square preview
  center_focus_strong tune format_bold format_italic format_h1 format_h2 format_h3 format_h4 format_h5 format_h6 notes
  link code code_blocks content_copy content_paste content_cut select_all format_quote format_list_bulleted
  format_list_numbered checklist format_strikethrough ink_highlighter format_clear close phone_android folder
  folder_open create_new_folder sort check chevron_right link_off drive_file_rename_outline drive_folder_upload delete
  check_box check_box_outline_blank info expand_less expand_more match_case find_replace)
dst=app/src/main/res/drawable; mkdir -p "$dst"
base='https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android'
for n in "${names[@]}"; do
  f="$dst/ic_$n.xml"
  [ -s "$f" ] || curl -fsSL --retry 3 -o "$f" "$base/$n/materialsymbolsrounded/${n}_24px.xml"
  # Remove ONLY the attribute (the same line also carries the '>' that closes <vector>).
  perl -0pi -e 's/\s*android:tint="\?attr\/colorControlNormal"//g' "$f"
done
if grep -l 'colorControlNormal' "$dst"/ic_*.xml >/dev/null 2>&1; then echo "tint attr still present" >&2; exit 1; fi
echo "icons: ${#names[@]} requested, $(ls "$dst"/ic_*.xml | wc -l | tr -d ' ') present"
