# T22 — Final QA, README polish, handoff to the human

**Goal** One full regression pass on the emulator:
- the iA-isms checklist;
- the whole `plans/QA-matrix.md`;
- a spot-check of every T05–T19 acceptance criterion;
- data-safety scenarios, including a refused signature-mismatch install with notes kept.

The README becomes the user's manual (features, gestures, shortcuts, install and update, key backup, where notes
live, export, privacy). STATUS.md ends with a "HANDOFF TO THE HUMAN" section with the exact steps for the user to
install the app on their phone themselves. **The agent never installs to the phone.**

**Depends on** T21: perf results recorded, budgets met or escalated, the release app uninstalled from the emulator.
T20: `plans/QA-matrix.md`, `scripts/qa/*`, the `ShortcutCatalog`, and the test tags `findBar` and `editorLoading`.
T18: Export all notes. T14: linked folders plus unlink. T01: the README install section and the Makefile.

**Read first**
- `plans/README.md` "Rules for agents" (rules 5, 6 and 13 especially) and the milestone table.
- `plans/STATUS.md`: every entry, in particular "Known issues / follow-ups" and "Deviations".
- `plans/02-design-spec.md` §15 (the iA-isms checklist) and §12 (wireframes).
- `plans/QA-matrix.md` and `plans/perf-results.md`.
- `plans/01-architecture.md` §8 (state and process death), §10 rules 13 and 15, §12.
- `plans/research/build.md` §7 (README install text, lines 383–452), §8 (install error codes), §9 (backing up notes).
- `plans/tasks/T05…T19`: only the "Acceptance criteria" sections. Use
  `awk '/^## Acceptance criteria/,/^## Verification/' plans/tasks/T0{5..9}-*.md plans/tasks/T1*-*.md`.

## Scope — In / Out
**In:**
- the regression runs;
- small bug fixes found during them (each with a test where possible);
- the data-safety scenarios with **throwaway keys on the emulator only**;
- the APK size check;
- README sections;
- the QA-matrix T22 column plus new sections;
- the STATUS entry and the HANDOFF section.

**Out:**
- New features or new settings. Log requests as follow-ups.
- Performance work. T21 owns it. If you see a regression, record it and do not tune.
- Dependency or version changes. Changes to the verified build files or the Makefile need STOP-AND-ASK.
- Anything on a physical phone (HARD RULE 13). Anything touching `~/.config/mdwriter` (README rule 6).
- Rewriting the README "Install on your phone" section. It must stay byte-identical to build.md §7 (the T01
  acceptance diff).

**Fix policy:**
- Data loss or corruption must be fixed, or STOP-AND-ASK. Never hand it off as a known issue.
- Other bugs that are ≤ ~50 lines in one owner file: fix with a test, and put the commit hash in the matrix.
- Anything larger: list it in STATUS "Known issues" and in the README's "Known limitations".

## Files to create / modify
- `plans/QA-matrix.md`: fill in the T22 column. Add three sections: "iA-isms (02 §15)", "Data safety (DS-*)" and
  "T05–T19 acceptance spot-check".
- `README.md`: add or refresh the sections listed in step 9. Keep the install section untouched.
- `plans/STATUS.md`: the T22 entry, then `## HANDOFF TO THE HUMAN` as the **last** section of the file.
- Whatever files small fixes touch, plus their tests (list each in STATUS).

## Steps
1. **Clean baseline.**
   - `make emulator`, `make devices`.
   - Confirm no release app: `adb -s emulator-5554 shell pm list packages dev.mdwriter` shows only `.debug`, or
     nothing.
   - `make uninstall-debug DEVICE=emulator-5554` (the emulator's debug notes only), then
     `make install-debug DEVICE=emulator-5554`. This is a first-run experience: the welcome note.
   - Reset the emulator settings: font_scale 1.0, `wm` reset, force_rtl 0, always_finish_activities 0,
     TalkBack off, gestural nav.
2. **Gates.** Run `make check` and `make test-device DEVICE=emulator-5554`. Record the test counts (JVM `:core:markdown`,
   JVM `:app`, instrumented). Both must be green before continuing. Fix anything red first.
3. **iA-isms checklist** (02 §15, 16 items). For each item, make a screenshot or run a command that proves it, and
   write `pass`/`fail` plus one line of evidence in QA-matrix "iA-isms". Colours are checked by sampling pixels with
   T02's `tools/PngPixel.java`:
   - page `#F7F7F7` and text `#1A1A1A` in light;
   - `#1A1A1A` and `#D0D0D0` in dark (`cmd uimode night yes`);
   - caret blue, 2 dp, spanning the full line pitch.
   Large-screen items use `wm density 320/240` as in T20.
4. **QA-matrix re-run.** Every row from T20 gets a T22 result. Rows marked `user` stay `user`; they are copied into
   the handoff checklist. Use the commands in each row's "How" column.
5. **T05–T19 acceptance spot-check.**
   - Criteria backed by a test: cite the test class, which is green in step 2.
   - Screenshot or manual criteria: re-verify **all** of T05, T07, T09, T11 and T13, and at least 2 per other task.
   - Record `Task | AC# | method | result` in QA-matrix.
6. **Data safety on the debug app** (rows DS-01…DS-06, Reference A):
   - DS-01: kill -9 while typing.
   - DS-02: rename the open note while it has unsaved edits.
   - DS-03: delete + undo, and delete + let the undo window expire.
   - DS-04: a linked SAF folder disappears while a note from it is open.
   - DS-05: unlink the folder while its note is open.
   - DS-06: external change while dirty, which must show the conflict banner (Reload · Keep mine · Save both).
7. **Data safety on the release app, throwaway keys** (DS-07…DS-09, Reference B):
   - DS-07: an install with key B over key A is refused, and the notes survive.
   - DS-08: an update with key A keeps the notes.
   - DS-09: Export all notes produces a zip whose entries match the library.
   Afterwards: `make uninstall CONFIRM=yes DEVICE=emulator-5554` and `rm -rf /tmp/mdwriter-qa-keyA /tmp/mdwriter-qa-keyB`.
8. **Release APK size.** Run `make apk KEYSTORE_DIR=/tmp/mdwriter-qa-keyA`; do it before deleting the key, or make a
   new throwaway one. Target **< 6 MiB (6,291,456 bytes)** including the 1.3 MB of fonts.
   - 6–8 MiB: record the top 15 entries of `unzip -lv` and log a follow-up.
   - > 8 MiB: find the cause (fonts duplicated in `assets/`, factcheck C13; R8 or resource shrinking off;
     debuggable) and STOP-AND-ASK before editing the build files.
9. **README polish** (Reference C). Add the sections in the given order. Generate the shortcut table from
   `ShortcutCatalog`. Check every claim against the code: permissions from `aapt2 dump permissions`, Auto Backup
   from the manifest's `allowBackup`/`dataExtractionRules`, and the export flow from T18.
10. **Final cleanup.** No release app on the emulator, `/tmp/mdwriter-qa-*` removed, emulator settings reset, and
    `git status` shows no stray files (no screenshots, notes backups, or keys).
11. **STATUS.** Write the T22 entry: test counts, QA summary (pass/fail/user counts), APK size, perf headline from T21,
    fixes made, and known issues. Then append the HANDOFF section (Reference D) **verbatim**, filling in
    the `<…>` blanks.
12. Run `make check` one last time, then commit `T22: final QA, README, handoff`. Do not push. Do not tag; that is
    the human's call.

## Reference code
**A. Debug-app data-safety commands** (sketch; `S=emulator-5554`, `P=dev.mdwriter.debug`)
```sh
# DS-01 kill -9 during typing/saving, x5 with different delays
for d in 2 3 5 7 11; do
  ( for i in $(seq 1 40); do $ADB -s $S shell input text "line_$i"; $ADB -s $S shell input keyevent KEYCODE_ENTER; done ) &
  sleep $d; $ADB -s $S shell "run-as $P sh -c 'kill -9 \$(pidof $P)'"; wait
  $ADB -s $S shell monkey -p $P -c android.intent.category.LAUNCHER 1 >/dev/null; sleep 3
  $ADB -s $S shell "run-as $P ls -la files/library"; $ADB -s $S exec-out run-as $P cat "files/library/<note>.md" | tail -3
done
```
The expected result for DS-01:
- The file on disk is never empty or truncated mid-UTF-8.
- Its last lines are complete `line_N` lines, apart from possibly a partial final one.
- The editor shows at least the file's text; the recovery copy may add the last seconds.
- No AtomicWriter temp files remain after the next successful save.
- Logcat has no `FATAL EXCEPTION`.

The other debug rows:
- **DS-02:** type, then rename from the drawer within 1 s. Exactly one file with the new name, containing all the
  text; the old name is gone.
- **DS-03:** delete, undo within 5 s (`LibraryViewModel.UNDO_WINDOW_MS`): the content is byte-identical. Delete and
  wait 6 s: the note is gone from the list and the editor switched away. No crash.
- **DS-04:** link `Documents/Notes` through the picker and open a note there. Then
  `adb shell mv /sdcard/Documents/Notes /sdcard/Documents/Notes-moved` and type. Expect the PermissionLost/NotFound
  error UI, the text kept (the recovery copy exists), and no crash. Move the folder back afterwards. A true grant
  revocation cannot be scripted; T14's Robolectric tests cover the SecurityException mapping.
- **DS-05:** unlink the folder in-app while its note is open. The editor leaves the note cleanly and does not write
  to the tree after the unlink.
- **DS-06:** while dirty, `adb push changed.md /sdcard/Documents/Notes/<note>.md`. The conflict banner appears, and
  each of the three choices gives the documented result.

**B. Signature-mismatch scenario** (release app, emulator only; `make run` launches the release app)
```sh
make install KEYSTORE_DIR=/tmp/mdwriter-qa-keyA DEVICE=emulator-5554        # creates key A, installs, launches
$ADB -s $S shell input text "DS07_canary_note"; sleep 3                      # autosave (1 s idle)
make install KEYSTORE_DIR=/tmp/mdwriter-qa-keyB DEVICE=emulator-5554; echo "exit=$?"   # MUST fail, explain, not uninstall
$ADB -s $S shell pm list packages dev.mdwriter                               # still "package:dev.mdwriter"
make run DEVICE=emulator-5554; sleep 2; $ADB -s $S exec-out screencap -p > /tmp/ds07.png   # canary visible
make install KEYSTORE_DIR=/tmp/mdwriter-qa-keyA DEVICE=emulator-5554        # DS-08: update OK, canary still there
# DS-09: Overflow › Export all notes › save to Downloads, then:
$ADB -s $S shell ls -la /sdcard/Download/ ; $ADB -s $S pull /sdcard/Download/<export>.zip /tmp/ && unzip -l /tmp/<export>.zip
make uninstall CONFIRM=yes DEVICE=emulator-5554 && rm -rf /tmp/mdwriter-qa-keyA /tmp/mdwriter-qa-keyB
```
The expected result for DS-07:
- The second install exits non-zero.
- Its output explains `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (build.md §8).
- The Makefile does not uninstall (HARD RULE 13).
- The canary note is intact.

**C. README section order.** Keep the existing sections, add the missing ones, and stay short and factual:
1. **mdwriter**: one paragraph ("a quiet Markdown editor for Android 16/17, in the spirit of iA Writer"). Font credit
   as in T02. No iA trademarks in the UI (factcheck C14).
2. **Features**: live-styled Markdown; focus mode; typewriter; preview; find and replace; linked folders; share;
   export; themes, fonts, sizes; stats.
3. **Gestures**:
   - swipe start→end: library;
   - swipe end→start: preview;
   - select text: formatting pill;
   - tap a task box: toggle;
   - Back order (as in T13);
   - the setting that turns swipes off.
4. **Keyboard shortcuts**: a table generated from `ShortcutCatalog` (`Ctrl+B`, …), plus "Meta+/ shows all".
5. **Install on your phone**: the existing section, untouched.
6. **Updating**: `git pull && make install`. Notes are kept. Never uninstall; it deletes in-app notes.
7. **Back up your signing key**: `make keystore-info`. Copy `~/.config/mdwriter/` to an encrypted backup. Without it,
   future updates are impossible without uninstalling.
8. **Where your notes live**:
   - In-app library = app-private storage. Invisible to other apps. Removed on uninstall. Auto Backup as the
     manifest configures it; state exactly what it does.
   - Linked folders: the files stay in your folder.
   - The debug app `dev.mdwriter.debug` has separate notes. `make backup-notes` reads debug notes only (build.md §9).
9. **Export**: Export all notes as a zip through the system file picker; share one note; open `.md` files from
   other apps.
10. **Privacy**:
    - No permissions at all: `aapt2 dump permissions` shows none except the androidx signature-only receiver
      permission.
    - No network: no INTERNET permission, and Preview is offline.
    - No accounts, analytics or crash reporting.
11. **Known limitations**: from STATUS, for example RTL paragraphs offset by the gutter on wide screens (T20).
12. **Troubleshooting**: the existing section, plus `make logcat` and `make doctor`.
13. **Development**: the make targets (`make help`), and `plans/` for the implementation plan.

**D. HANDOFF section** (append to STATUS.md verbatim; fill in the `<…>` blanks)
```markdown
## HANDOFF TO THE HUMAN
State: all 22 tasks done. Tests: <n> JVM + <n> instrumented green. APK <x.x> MB. Perf (emulator): keystroke 100k
<m>/<p90> ms, open 100k <n> ms, cold start <n> ms. Known issues: <bullets or "none">.

The agent never installed anything on your phone. To install it yourself:
1. On the Mac: `make doctor`, and fix anything it reports (JDK 21, Android SDK).
2. On the phone: Settings › About phone › tap "Build number" 7× → Settings › System › Developer options →
   turn on **USB debugging** (or **Wireless debugging**, then `make pair HOST=<ip:port> CODE=<code>` and
   `make connect HOST=<ip:port>`).
3. Connect the phone and accept the "Allow USB debugging" prompt. `make devices` must list it.
4. `make install` (add `DEVICE=<serial>` if an emulator is also running). The first run creates your signing key in
   `~/.config/mdwriter/`, installs the release build + baseline profile, and launches mdwriter.
5. **Back up the key now:** `make keystore-info` (shows path + SHA-256), then copy the whole `~/.config/mdwriter/`
   folder to an encrypted backup. Without it you cannot update the app without uninstalling it, which deletes the
   notes stored inside the app.
6. First-run checklist on the phone:
   - [ ] The welcome note opens. Create a new note (pencil glyph in the library), type `# Test`, and the heading grows.
   - [ ] Swipe across the text start→end to open the library; swipe or Back to close it.
   - [ ] Select a word to see the pill; tap Bold, and `**…**` appears.
   - [ ] Swipe end→start to open the preview; Back closes it.
   - [ ] Library › "Use a folder…" › create/pick `Documents/Notes`, then create a note there and see it in the Files app.
   - [ ] Overflow › "Export all notes" › save the zip to Downloads, then open it in Files.
   - [ ] Settings sheet: switch theme, font and size. Rotate the phone; the text and caret are unchanged.
   - [ ] Items the agent could not test on the emulator: <copy the QA-matrix rows marked `user`: voice input, TalkBack, …>
7. Updating later: `git pull && make install`. Notes are kept. Do not run `make uninstall` unless you exported your notes.
8. Optional: measure on your phone with the protocol in `plans/perf-results.md` ("Run it on your phone").
9. If something breaks: run `make logcat` while reproducing it, and keep the output.
```

## Acceptance criteria
1. `make check` and `make test-device DEVICE=emulator-5554` pass. The test counts are in STATUS.
2. `plans/QA-matrix.md`:
   - every T20 row has a T22 value;
   - all 16 iA-isms rows are present with evidence;
   - DS-01…DS-09 are present;
   - the spot-check table covers T05–T19;
   - `grep -c '| fail' plans/QA-matrix.md` equals the number of known issues listed in STATUS, and none of them is
     data loss.
3. DS-07: the second `make install` exits non-zero and its output names the signature mismatch.
   `pm list packages dev.mdwriter` still lists the app, and `/tmp/ds07.png` shows the canary text. DS-08: the canary
   is still present after the key-A update.
4. DS-01: 5 kill runs with no empty or corrupted file and no `FATAL EXCEPTION`.
5. The release APK is < 6,291,456 bytes, or its size and top entries are recorded with a follow-up (≤ 8 MiB).
6. `diff <(sed -n '386,445p' plans/research/build.md) <(sed -n '/^## Install on your phone/,/no JDK 17-26 found/p' README.md)`
   prints nothing. `grep -cE '^## (Features|Gestures|Keyboard shortcuts|Updating|Back up your signing key|Where your notes live|Export|Privacy|Known limitations)' README.md`
   prints `9`.
7. `plans/STATUS.md` ends with `## HANDOFF TO THE HUMAN`: `tail -n 40 plans/STATUS.md | grep -c 'HANDOFF TO THE HUMAN'`
   prints `1`, and there are no `<…>` placeholders left (`grep -c '<n>\|<x.x>' plans/STATUS.md` prints `0`).
8. The emulator is clean: `pm list packages dev.mdwriter` shows no release app, `ls /tmp | grep mdwriter-qa` is
   empty, and `settings get system font_scale` prints `1.0`.
9. Every install or uninstall command in the STATUS evidence targets `DEVICE=emulator-*`. `~/.config/mdwriter` was
   never referenced.

## Verification commands
```sh
make check && make test-device DEVICE=emulator-5554
ADB=~/Library/Android/sdk/platform-tools/adb; S=emulator-5554
$ADB -s $S shell pm list packages dev.mdwriter
make apk KEYSTORE_DIR=/tmp/mdwriter-qa-keyA; APK=$(ls app/build/outputs/apk/release/*.apk | head -1); stat -f %z "$APK"
unzip -lv "$APK" | sort -k1 -n -r | head -15
~/Library/Android/sdk/build-tools/36.0.0/aapt2 dump permissions "$APK"
diff <(sed -n '386,445p' plans/research/build.md) <(sed -n '/^## Install on your phone/,/no JDK 17-26 found/p' README.md)
grep -c '| fail' plans/QA-matrix.md; tail -n 40 plans/STATUS.md
$ADB -s $S shell settings get system font_scale; $ADB -s $S shell wm density
git status --short
```

## Pitfalls
- HARD RULE 13 and README rules 5 and 6: emulator only. Throwaway keys go under `/tmp/mdwriter-qa-*`. Never run
  `make install`, `make apk` or `make keystore` without `KEYSTORE_DIR=`. If a phone is plugged in, `DEVICE=` must
  still name the emulator; check with `make devices` before every install.
- `make uninstall` deletes the app's notes. Use it only on the emulator, only with `CONFIRM=yes`, and only after the
  release scenarios.
- The release app is not debuggable, so `run-as` fails on it. Verify release data through the UI (screenshots) and
  the export zip.
- DS-04 moves a real folder on the emulator's shared storage. Always move it back.
- `input text` cannot type spaces or `#`. Use `%s` for a space, and `input keyevent KEYCODE_POUND` for `#`.
- The README install section is checked byte-for-byte (T01). Edit around it, never inside it.
- If the release build was installed from Android Studio at any point, it is debug-signed (build.md §12.2), and DS-07
  will look different. Always use `make install`.
- Do not "fix" failures by weakening tests or deleting QA rows. Record the failure honestly.

## Definition of done
- [ ] Steps 1–12 done. QA-matrix is complete (T22 column, iA-isms, DS-*, spot-check).
- [ ] All data-safety rows pass (a data-loss failure means BLOCKED + STOP-AND-ASK, not done).
- [ ] README sections are added, and the install section is unchanged.
- [ ] `make check` and `make test-device DEVICE=emulator-5554` are green.
- [ ] The emulator is clean, throwaway keys are deleted, and no stray files are in git.
- [ ] STATUS has the T22 entry, then `## HANDOFF TO THE HUMAN` as the last section, with no placeholders.
- [ ] Commit `T22: final QA, README, handoff`. Not pushed, not tagged.
