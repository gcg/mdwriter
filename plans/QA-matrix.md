# QA matrix (T20)

T22 re-runs this table. `user` = needs a human, a phone or TalkBack; the T22 column is left empty.

| ID | Area | Scenario | How | Expected | T20 | T22 |
|---|---|---|---|---|---|---|
| A11Y-01 | Accessibility | Labels on every clickable node | uiautomator dump + perl check | No unlabeled clickable node | not run (user) | |
| A11Y-02 | Accessibility | Editor custom actions in TalkBack | TalkBack actions menu | Open library, Show preview, Find, formatting actions | implemented (Find added in T20); TalkBack check: user | |
| A11Y-03 | Accessibility | Focus order | TalkBack swipe | Chrome glyphs, then editor; drawer: search, new note, folders, files | user | |
| A11Y-04 | Accessibility | Targets >= 48 dp | dump check / TouchTargetsTest | None smaller than 48 dp | not run (TouchTargetsTest not written) (user) | |
| A11Y-05 | Accessibility | Font scale 1.3 | settings put system font_scale 1.3 | Headings wrap, pill fits, drawer rows grow | automated restore only (RecreateRestoreTest); visual: user | |
| A11Y-06 | Accessibility | Font scale 2.0 | settings put system font_scale 2.0 | Same, body pitch >= 1.5x | user | |
| A11Y-07 | Accessibility | Save error announced | chmod 500 files/library, type | Error text shown in a polite live region | implemented (saveError tag, live region); on-device: user | |
| LS-01 | Large screens | 448 dp baseline | default density | No hang, 24 dp margins | user | |
| LS-02 | Large screens | 672 dp | wm density 320 | Hang, 32 dp margins | user | |
| LS-03 | Large screens | 896 dp + permanent pane | wm density 240 | Pane permanent | user | |
| LS-04 | Large screens | Rotation keeps undo/IME | rotate | Undo and IME survive | user | |
| LS-05 | Large screens | Split screen / tiny window | Recents split | No crash, layout adapts | user | |
| LS-06 | Large screens | Compat flag | am compat enable UNIVERSAL_RESIZABLE_BY_DEFAULT | Resizable | user | |
| LS-07 | Large screens | No orientation locks | manifest | No screenOrientation/resizeableActivity=false/maxAspectRatio | pass (merged manifest checked, see MAN-01) | |
| CFG-01 | Config | recreate() | RecreateRestoreTest.recreateRestoresDocument | Text, caret, scroll restored | fail -> fixed (ViewModel re-installs the document into a new editor on rebind) | |
| CFG-02 | Config | fontScale | RecreateRestoreTest.fontScaleChangeRestoresDocument | Text and caret restored, caret visible | pass | |
| CFG-03 | Config | layoutDirection | settings put global debug.force_rtl 1 | Mirrors | user | |
| CFG-04 | Config | cmd uimode night yes/no | adb | No recreation, colours update | user | |
| CFG-05 | Config | Don't keep activities | always_finish_activities 1 | Same doc, caret, scroll | user | |
| CFG-06 | Config | Process kill | am kill after Home | Unsaved text recovered | user | |
| BIG-01 | Huge files | 300k loading placeholder <= 100 ms | gen-doc.sh + push-doc.sh, adb logcat -s MdPerf | open.loadingShown <= 100 ms | partial: 76 ms measured for the 1.2 MB open; clean 300k run not obtained (UI automation flaky); open took ~4.6 s (T21) | |
| BIG-02 | Huge files | 1 MB notice | open 1.2 MB | Large document notice | partial: opened, install took ~12 s (T21); notice not confirmed | |
| BIG-03 | Huge files | 5 MB read-only | open 6 MB | Read-only, plain | not run (user) | |
| BIG-04 | Huge files | Binary refused | open binary.md | Message | not run (user) | |
| BIG-05 | Huge files | > 16 MB refused | open 17 MB | TooLarge message | not run (user) | |
| RTL-01 | RTL | 448 dp | push Reference F note | Indents/quote bar on the right | user | |
| RTL-02 | RTL | 672 dp hang | same | # hangs right | user | |
| RTL-03 | RTL | Force RTL + swipe flip | debug.force_rtl | Drawer from the right | user | |
| RTL-04 | RTL | CJK wrap | same | Wraps | user | |
| IME-01 | IME | Fast typing | manual Gboard | No lost characters | user | |
| IME-02 | IME | Autocorrect above styled text | ImeCompositionTest.autocorrectAboveStyledTextDoesNotShiftSpans | Spans unchanged | pass | |
| IME-03 | IME | Glide typing | manual | Works | user | |
| IME-04 | IME | Voice | manual | Works | user | |
| IME-05 | IME | Suggestion popup after edits above | ImeCompositionTest.suggestionSpanSurvivesAnEditAbove (automated part); popup: manual | Right word | automated part pass; popup: user | |
| IME-06 | IME | ImeCompositionTest | make test-device | 5 tests green | pass | |
| KB-01 | Keyboard | Host keyboard Ctrl shortcuts | KeyboardShortcutsTest | All catalog editor shortcuts handled | pass | |
| KB-02 | Keyboard | Meta+/ helper | KeyboardShortcutsTest.providedShortcutsContainEveryCatalogEntry | Every catalog entry listed | pass (screenshot: user) | |
| NAV-01 | Navigation | Gestural swipe matrix | manual | No conflicts | user | |
| NAV-02 | Navigation | Three-button + predictive back | manual | Works | user | |
| SM-01 | Other | StrictMode clean | logcat grep StrictMode|MdStrict after launch, typing, new note, preview, find, drawer | 0 violations | pass (export and settings sheet not exercised) | |
| MAN-01 | Other | Manifest audit | aapt2 dump | Only allowed permission / exported components | pass | |
| LINT-01 | Other | Lint | make check | 0 errors | pass | |
