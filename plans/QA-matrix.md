# QA matrix (T20)

T22 re-runs this table. `user` = needs a human, a phone or TalkBack; the T22 column is left empty.

| ID | Area | Scenario | How | Expected | T20 | T22 |
|---|---|---|---|---|---|---|
| A11Y-01 | Accessibility | Labels on every clickable node | uiautomator dump + perl check | No unlabeled clickable node | not run (user) | user |
| A11Y-02 | Accessibility | Editor custom actions in TalkBack | TalkBack actions menu | Open library, Show preview, Find, formatting actions | implemented (Find added in T20); TalkBack check: user | implemented; TalkBack: user |
| A11Y-03 | Accessibility | Focus order | TalkBack swipe | Chrome glyphs, then editor; drawer: search, new note, folders, files | user | user |
| A11Y-04 | Accessibility | Targets >= 48 dp | dump check / TouchTargetsTest | None smaller than 48 dp | not run (TouchTargetsTest not written) (user) | user |
| A11Y-05 | Accessibility | Font scale 1.3 | settings put system font_scale 1.3 | Headings wrap, pill fits, drawer rows grow | automated restore only (RecreateRestoreTest); visual: user | user |
| A11Y-06 | Accessibility | Font scale 2.0 | settings put system font_scale 2.0 | Same, body pitch >= 1.5x | user | user |
| A11Y-07 | Accessibility | Save error announced | chmod 500 files/library, type | Error text shown in a polite live region | implemented (saveError tag, live region); on-device: user | implemented; on-device: user |
| LS-01 | Large screens | 448 dp baseline | default density | No hang, 24 dp margins | user | user |
| LS-02 | Large screens | 672 dp | wm density 320 | Hang, 32 dp margins | user | user |
| LS-03 | Large screens | 896 dp + permanent pane | wm density 240 | Pane permanent | user | user |
| LS-04 | Large screens | Rotation keeps undo/IME | rotate | Undo and IME survive | user | user |
| LS-05 | Large screens | Split screen / tiny window | Recents split | No crash, layout adapts | user | user |
| LS-06 | Large screens | Compat flag | am compat enable UNIVERSAL_RESIZABLE_BY_DEFAULT | Resizable | user | user |
| LS-07 | Large screens | No orientation locks | manifest | No screenOrientation/resizeableActivity=false/maxAspectRatio | pass (merged manifest checked, see MAN-01) | pass (manifest re-checked in T22: no orientation locks) |
| CFG-01 | Config | recreate() | RecreateRestoreTest.recreateRestoresDocument | Text, caret, scroll restored | pass after a fix (ViewModel now re-installs the document into a new editor on rebind) | pass (RecreateRestoreTest green in the final device run) |
| CFG-02 | Config | fontScale | RecreateRestoreTest.fontScaleChangeRestoresDocument | Text and caret restored, caret visible | pass | pass (RecreateRestoreTest green) |
| CFG-03 | Config | layoutDirection | settings put global debug.force_rtl 1 | Mirrors | user | user |
| CFG-04 | Config | cmd uimode night yes/no | adb | No recreation, colours update | user | user |
| CFG-05 | Config | Don't keep activities | always_finish_activities 1 | Same doc, caret, scroll | user | user |
| CFG-06 | Config | Process kill | am kill after Home | Unsaved text recovered | user | user |
| BIG-01 | Huge files | 300k loading placeholder <= 100 ms | gen-doc.sh + push-doc.sh, adb logcat -s MdPerf | open.loadingShown <= 100 ms | partial: 76 ms measured for the 1.2 MB open; clean 300k run not obtained (UI automation flaky); open took ~4.6 s (T21) | partial (see T21: placeholder shown, 300k opens in ~3.5 s); user |
| BIG-02 | Huge files | 1 MB notice | open 1.2 MB | Large document notice | partial: opened, install took ~12 s (T21); notice not confirmed | partial; user |
| BIG-03 | Huge files | 5 MB read-only | open 6 MB | Read-only, plain | not run (user) | user |
| BIG-04 | Huge files | Binary refused | open binary.md | Message | not run (user) | user |
| BIG-05 | Huge files | > 16 MB refused | open 17 MB | TooLarge message | not run (user) | user |
| RTL-01 | RTL | 448 dp | push Reference F note | Indents/quote bar on the right | user | user |
| RTL-02 | RTL | 672 dp hang | same | # hangs right | user | user |
| RTL-03 | RTL | Force RTL + swipe flip | debug.force_rtl | Drawer from the right | user | user |
| RTL-04 | RTL | CJK wrap | same | Wraps | user | user |
| IME-01 | IME | Fast typing | manual Gboard | No lost characters | user | user |
| IME-02 | IME | Autocorrect above styled text | ImeCompositionTest.autocorrectAboveStyledTextDoesNotShiftSpans | Spans unchanged | pass | pass (ImeCompositionTest green) |
| IME-03 | IME | Glide typing | manual | Works | user | user |
| IME-04 | IME | Voice | manual | Works | user | user |
| IME-05 | IME | Suggestion popup after edits above | ImeCompositionTest.suggestionSpanSurvivesAnEditAbove (automated part); popup: manual | Right word | automated part pass; popup: user | automated part pass; popup: user |
| IME-06 | IME | ImeCompositionTest | make test-device | 5 tests green | pass | pass (5/5 green) |
| KB-01 | Keyboard | Host keyboard Ctrl shortcuts | KeyboardShortcutsTest | All catalog editor shortcuts handled | pass | pass (KeyboardShortcutsTest green) |
| KB-02 | Keyboard | Meta+/ helper | KeyboardShortcutsTest.providedShortcutsContainEveryCatalogEntry | Every catalog entry listed | pass (screenshot: user) | pass (test); screenshot: user |
| NAV-01 | Navigation | Gestural swipe matrix | manual | No conflicts | user | user |
| NAV-02 | Navigation | Three-button + predictive back | manual | Works | user | user |
| SM-01 | Other | StrictMode clean | logcat grep StrictMode|MdStrict after launch, typing, new note, preview, find, drawer | 0 violations | pass (export and settings sheet not exercised) | pass in T20; not re-run in T22 |
| MAN-01 | Other | Manifest audit | aapt2 dump | Only allowed permission / exported components | pass | pass (aapt2 re-run in T22: only the androidx signature-only receiver permission) |
| LINT-01 | Other | Lint | make check | 0 errors | pass | pass (make check) |


## iA-isms (02 §15)
| # | Item | T22 | Evidence |
|---|---|---|---|
| 1 | `#F7F7F7` page, `#1A1A1A` text; dark `#1A1A1A`/`#D0D0D0` | pass (page colours) | `tools/PngPixel.java` on release-app screenshots: light page `#F7F7F7`, dark page `#1A1A1A` (live switch with `cmd uimode night`). Text colours and pure black: not sampled. |
| 2 | Electric-blue 2 dp caret across the full line | partial | caret sampled `#00B2FF`; width/line span not measured. |
| 3 | Duo default; Quattro/Mono selectable; code always Mono | partial | Settings sheet shows Duo/Quattro/Mono (release screenshot, T19); code font not re-checked. |
| 4 | Line pitch 1.65x / 1.75x | user | covered by T05 `EditorGeometry` tests. |
| 5 | Large screens: centred 64/72/80 column, `#` hanging | user | T20 LS rows. |
| 6 | Markdown visible, live restyle, grey URLs/brackets | partial | seen in release screenshot of the Welcome note (T22 first run); not re-run per item. |
| 7 | Wrapped list/quote lines indent under the text | user | |
| 8 | No chrome while typing; glyphs return when you stop | pass | typing on the release app: corner glyphs hidden while typing (screenshots `ds07-before`), back after the pause (`ds08`). |
| 9 | No keyboard bar / FAB / app bar / bottom bar | pass | screenshots show only the two corner glyphs. |
| 10 | Swipe right -> library, left -> preview, one setting turns it off | pass | `SwipeNavTest` green (incl. `swipeNavigationOffDisablesCase1`). |
| 11 | Focus mode + typewriter | pass | device tests `FocusOverlayDeviceTest`, `TypewriterDeviceTest` green. |
| 12 | Stats opt-in, tiny, top, selection-aware | pass | T15 tests green. |
| 13 | One settings sheet; no accounts/cloud/analytics | pass | settings sheet opened on the release app; no INTERNET permission. |
| 14 | Reopening restores caret + scroll; autosave, no Save button | pass | `RecreateRestoreTest`; relaunch restored the canary note. |
| 15 | Flat surfaces, hairline dividers, no shadows | partial | sheet is flat in the release screenshot; not audited everywhere. |
| 16 | Completed tasks faded + struck; `==highlight==` soft yellow | partial | `HighlightToggleTest` / T06 tests; visual not re-checked. |

## Data safety (DS-*)
| ID | Scenario | T22 | Evidence |
|---|---|---|---|
| DS-01 | kill -9 while typing x5 | partial | 10 kill runs on the debug app: file never empty/corrupt, valid UTF-8, no temp files, no `FATAL EXCEPTION`. `adb shell input text` could not reliably inject typing into the editor, so the "typing at the moment of the kill" case was not truly exercised. Covered by `DocumentRepositoryTest.recoveryWrittenBeforeWriteAndDeletedAfter`, `EditorViewModelTest.processDeathRestoresFromHandleAndRecovery`, `AutosaveCoordinatorTest`. |
| DS-02 | rename the open note with unsaved edits | not run on device | `EditorViewModelTest` (rename/onCurrentRefChanged) |
| DS-03 | delete + undo / expire | not run on device | `LibraryViewModelTest` |
| DS-04 | linked folder disappears while open | not run on device | `SafTreeStoreTest.permissionLossMapsToPermissionLost`, `ExternalDocStoreTest.refreshThrowsPermissionLostWhenGone` |
| DS-05 | unlink while open | not run on device | `LibraryRepositoryTreeTest.unlinkReleasesGrantAndKeepsFiles` |
| DS-06 | external change while dirty -> conflict banner | not run on device | `DocumentRepositoryTest.olderDifferentRecoveryRaisesConflict`, `EditorViewModelTest` conflict cases, `ConflictBanner` |
| DS-07 | install with key B over key A refused, notes survive | pass | second `make install` exit=2, "NOT INSTALLED: ... DIFFERENT key. Your notes ... are untouched", `pm list packages` still lists `dev.mdwriter`, canary note visible after relaunch (`/tmp/ds07.png`). |
| DS-08 | update with key A keeps notes | pass | reinstall with key A: versionCode 395368, canary still there (`/tmp/ds08.png`). |
| DS-09 | Export all notes zip matches the library | pass | real flow on the release app: Settings > Export all notes... > system picker > `mdwriter-notes-2026-10-02.zip` containing `mdwriter-notes/Welcome.md` and `mdwriter-notes/DS07_canary_note.md` = the library. |

## T05-T19 acceptance spot-check
Criteria backed by tests are cited by class; all are green in the final run (JVM: 250 `:core:markdown` + 537 `:app`; instrumented: 84).
| Task | Method | Result |
|---|---|---|
| T05-T09 (editor engine, spans, restyle, behaviours, pill) | `EditorScrollDeviceTest`, `InstallStylingDeviceTest`, `RestyleCorrectnessTest`, `IncrementalLayoutEqualsFullReflowTest`, `SmartEditingTest`, `UndoRedoTest`, `TaskToggleTest`, `SelectionToolbarTest` (+ JVM tests) | pass (tests); visual-only criteria not re-verified: user |
| T10-T12 (storage, session, library) | JVM `Internal/SafTree/Document/Library*` tests, `LibraryUiTest` | pass (tests) |
| T13 (swipe, chrome, back order) | `SwipeNavTest` (9 cases) | pass |
| T14 (linked folders) | `SafTreeStoreTest`, `LibraryRepositoryTreeTest`, `DocumentRepositorySafTest` | pass (tests); picker flow: user |
| T15 (focus/typewriter/stats) | `FocusOverlayDeviceTest`, `TypewriterDeviceTest`, stats tests | pass |
| T16 (preview) | JVM preview tests | pass (tests); visual: user |
| T17 (find & replace) | `FindDeviceTest` | pass |
| T18 (intents, share, export) | `IntentRouterTest`, `ShareOutTest`, `ExportAllNotesTest`; DS-09 real flow | pass |
| T19 (settings) | `SettingsContentTest`, `AboutContentTest`, `LicenseAssetsTest`, `StyleSwitchTest`, `HighlightToggleTest`; settings sheet opened on the release app | pass |
