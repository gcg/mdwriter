# T19 — Settings sheet, runtime style changes, About & licences

**Goal** Overflow › Settings opens a flat bottom sheet with exactly the rows of 02 §10. Every change applies immediately, without recreating the activity: the note's text, caret, scroll position and undo history all survive a switch of theme, typeface, text size, line length or `==highlight==` syntax. About shows the version, the font credit, the OFL licence and the third-party notices, all bundled offline.

**Depends on**
- T15: `Settings.focusMode` / `typewriter` / `wordCount`, already consumed by the engine and the stats line, and its `OverflowActions.focus/typewriter/wordCount`.
- T16: `PreviewThemes.build(...)` keyed on colours/font/size/line length, which re-renders an open preview on change.
- T18: the "Export all notes…" entry point (a zip via `ActivityResultContracts.CreateDocument("application/zip")` + `ExportAllNotes`). T18 names it `rememberExportAllNotes(exporter, session): () -> Unit` (`ui/library/ExportAllNotesAction.kt`), plus `ExportAllNotes.status` and `ExportProgress(status)`; confirm with `grep -rn "ExportAll\|exportAll" app/src/main/kotlin`.
- T13: `OverflowActions.onSettings`, the `MdWriterRoot` structure, `EditorUiState.settingsOpen`, and the swipe gated by `settings.swipeNavigation`.
- T11/T12: `SettingsRepository.settings: Flow<Settings>` + `suspend fun update(transform: (Settings) -> Settings)`, and the fields `sortOrder`, `newNoteExtension`, `showExtensions`.
- T06/T07: `EditorStyle`, `SpanFactory`, `Restyler`, `EditorController.setStyle`. T02: `ThemeMode`, `writerColorsFor`, `WriterFont.fontFamily`, `EditorMetrics`, `SystemBarsAppearance`, `WriterDimens.sheetPadding`, and `assets/licenses/iA-Writer-fonts-OFL.txt`.

**Read first**
- `plans/01-architecture.md` §6.1 (highlighter `enableHighlight`), §6.2 (`setStyle`), §8, §10 (rules 2, 3, 4, 5)
- `plans/02-design-spec.md` §1, §2, §3, §10, §11
- `plans/research/design.md` §4.9 (`sed -n '346,365p'`), §6.6 (`sed -n '565,589p'`)
- `plans/research/platform.md` §10.2 (font scale, `sed -n '692,699p'`)
- `plans/research/factcheck.md` A13, A14, A20 (`grep -n '^| A1[34] \|^| A20' plans/research/factcheck.md`)
- `plans/tasks/T13-swipe-and-chrome.md` Reference §E

## Scope — In / Out
In:
- `SettingsSheet` + `SettingsContent` (stateless rows), `AboutSheet` + `AboutContent` (licence viewer), and the overflow Settings entry.
- Settings fields that are still missing, and runtime application of theme, pure black, typeface, text size, line length and highlight syntax.
- The licence texts in `assets/licenses/`.

Out (owner):
- Focus, typewriter, word count and swipe *behaviour* → already done by T13/T15. T19 only writes the settings.
- Export implementation → T18 (T19 only calls it). Sort order UI → stays in the library's sort menu (T12).
- Activity-recreating changes (font scale, locale) and their restore path → **T20**.
- Per-app language, dynamic colour, a line-height slider → never (02 §10 "Deliberately absent").

## Files to create / modify
- `app/src/main/kotlin/dev/mdwriter/ui/settings/SettingsSheet.kt` — `SettingsSheet` (ModalBottomSheet) + `SettingsContent` (stateless)
- `app/src/main/kotlin/dev/mdwriter/ui/settings/SettingsRows.kt` — `SettingsGroupHeader`, `SwitchRow`, `SegmentedRow`, `TextSizeRow`, `ActionRow`
- `app/src/main/kotlin/dev/mdwriter/ui/settings/AboutSheet.kt` — `AboutSheet` + `AboutContent` + `LicenseViewer`
- `app/src/main/kotlin/dev/mdwriter/ui/settings/LicenseNotices.kt` — `data class LicenseNotice(titleRes, component, assetPath)` + `val LicenseNotices`
- `app/src/main/assets/licenses/commonmark-java-BSD-2-Clause.txt`, `autolink-java-MIT.txt`, `Apache-2.0.txt` — notice texts (OFL already exists)
- `app/src/main/kotlin/dev/mdwriter/data/settings/Settings.kt`, `SettingsRepository.kt` (modify) — add only the fields that are missing (Reference §A)
- `app/src/main/kotlin/dev/mdwriter/ui/root/MdWriterRoot.kt` (modify) — sheet state, style application effects, `onSettings`
- `app/src/main/kotlin/dev/mdwriter/ui/editor/EditorViewModel.kt` (modify) — `sheet: StateFlow<RootSheet>`, `showSheet()`, and `settingsOpen = sheet != None`
- `app/src/main/kotlin/dev/mdwriter/editor/EditorController.kt`, `EditorScrollView.kt`, `Restyler.kt`, `spans/*` (modify as needed) — `setStyle` policy + `setHighlightSyntax` (§C)
- `app/src/main/res/values/strings.xml` (modify) — every row label and value of 02 §10
- `app/src/test/kotlin/dev/mdwriter/ui/settings/SettingsContentTest.kt`, `AboutContentTest.kt` — Robolectric Compose
- `app/src/test/kotlin/dev/mdwriter/ui/settings/LicenseAssetsTest.kt` — JVM (asset files exist + key phrases)
- `app/src/test/kotlin/dev/mdwriter/testing/FakeSettingsRepository.kt` — reuse T11/T12's fake if one exists
- `app/src/androidTest/kotlin/dev/mdwriter/editor/StyleSwitchTest.kt`, `HighlightToggleTest.kt` — device

## Steps
1. Read the STATUS entries of T06, T07, T11, T15, T16 and T18. Then `grep -n "val \|fun " app/src/main/kotlin/dev/mdwriter/data/settings/Settings.kt app/src/main/kotlin/dev/mdwriter/editor/EditorController.kt app/src/main/kotlin/dev/mdwriter/editor/spans/EditorStyle.kt`. Add only the missing settings fields (§A). Keep existing names; if a name differs from §A, use the existing one everywhere below.
2. Get the licence texts:
   - Search for bundled copies: `for j in $(find ~/.gradle/caches/modules-2/files-2.1 -name '*.jar' | grep -E 'commonmark-0.30|autolink-0.12|kotlin-stdlib-2'); do unzip -l "$j" | grep -iE 'licen|notice' && echo "  ^ $j"; done`, then extract with `unzip -p`.
   - If none are bundled, write the canonical SPDX texts. Take the copyright line from the POM (`find ~/.gradle/caches -name 'commonmark-0.30.0.pom' -o -name 'autolink-0.12.0.pom'`). Expected: commonmark-java "Copyright (c) 2015, Atlassian Pty Ltd"; autolink-java "Copyright (c) 2015 Robin Stocker". Verify both.
   - `Apache-2.0.txt` starts with a header listing: AndroidX (Compose, Activity, Lifecycle, Core, DataStore, WebKit, ProfileInstaller), Kotlin standard library, kotlinx.coroutines.
   - Record where each text came from in STATUS.
3. Write `SettingsRows.kt` and `SettingsContent` (Reference §B) with rows exactly in 02 §10 order and wording. Write `SettingsContentTest` and run `make test`.
4. Write `SettingsSheet`:
   - `ModalBottomSheet(onDismissRequest, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface, contentColor = colors.text, tonalElevation = 0.dp, scrimColor = colors.scrim, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), dragHandle = { BottomSheetDefaults.DragHandle(color = colors.divider) })`.
   - The content is `verticalScroll`, with horizontal padding `WriterDimens.sheetPadding(widthClass)`. In landscape phones it is effectively full-screen.
5. Write `AboutSheet` / `AboutContent`:
   - App name, `"Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"`, and the font credit sentence of 02 §10 verbatim.
   - Rows for "SIL Open Font License 1.1" and each `LicenseNotice`. Tapping one shows `LicenseViewer`: Mono 12 sp text read from assets with `produceState` on `Dispatchers.IO`, plus an `arrow_back` glyph and `BackHandler(enabled = shown != null) { shown = null }`.
   - If `BackHandler` inside the sheet does not win over the sheet's own back (check on device), keep the glyph and record it in STATUS.
6. Root wiring:
   - `enum class RootSheet { None, Settings, About }`, held in `EditorViewModel` and saved in `SavedStateHandle`.
   - `OverflowActions(onSettings = { editorVm.showSheet(RootSheet.Settings) })`.
   - About's dismiss → `Settings`, and Settings' dismiss → `None`.
   - `onExportAll` → T18's launcher. `showLineLength` = `currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)`.
7. Runtime application (§C) in `MdWriterRoot`:
   - `colors = writerColorsFor(s.themeMode, s.pureBlack, isSystemInDarkTheme())` feeds `MdWriterTheme`, `SystemBarsAppearance(darkIcons = !colors.isDark)` and `PreviewThemes`.
   - `style = remember(colors, s.typeface, s.highlightSyntax, s.textSizeStep, s.lineLength, widthClass, fontScale) { EditorStyle.create(...) }` (T05's factory; field names are T05's: `font`, `fonts`, `textSizeStep`, `measureChars`, `highlightSyntax`), then `LaunchedEffect(style) { controller.setStyle(style) }`.
   - `LaunchedEffect(s.highlightSyntax) { controller.setHighlightSyntax(s.highlightSyntax) }`.
   - Compose the editor only after the **first real** settings emission (T11 may already gate this). A default-then-real double emission would reflow twice at startup.
8. Engine:
   - Make `setStyle` follow the policy of §C. Add `EditorController.setHighlightSyntax(enabled)` (contract addition) and the scroll anchor helpers in `EditorScrollView`.
   - Keep `setStyle` idempotent: `if (style == current) return`.
9. Write `StyleSwitchTest` and `HighlightToggleTest` (Acceptance 5–6) and run them. Take the screenshots. Update 01 §6.2 with `setHighlightSyntax`, write STATUS, and commit.

## Reference code
§A Settings fields — **contract**. Add a field only if it is missing. DataStore key in parentheses; default in bold.
```kotlin
val themeMode: ThemeMode = ThemeMode.System      // theme_mode (enum name)       T02 enum
val pureBlack: Boolean = false                   // pure_black
val typeface: WriterFont = WriterFont.Duo       // typeface (enum name)   T02 enum, T11 name
val textSizeStep: Int = EditorMetrics.DEFAULT_TEXT_SIZE_STEP // text_size_step, 0..5 = XS S M L XL XXL
val lineLength: Int = 64                         // line_length, one of 64 / 72 / 80
val focusMode /* T15's type */                   // focus_mode (T15)
val typewriter: Boolean = false; val wordCount: Boolean = false            // typewriter, word_count (T15)
val swipeNavigation: Boolean = true              // swipe_navigation (T13)
val highlightSyntax: Boolean = false             // highlight_syntax (01 §6.1)
val newNoteExtension: String = "md"; val showExtensions: Boolean = false  // T12
```
Unknown or corrupt stored values fall back to the default (`runCatching { enumValueOf<…>(it) }`). Add a `SettingsRepository` Robolectric round-trip case for every field you add.

§B `SettingsContent` — **contract + sketch**
```kotlin
@Composable fun SettingsContent(
    settings: Settings, showLineLength: Boolean,
    onUpdate: ((Settings) -> Settings) -> Unit, onExportAll: () -> Unit, onAbout: () -> Unit,
    modifier: Modifier = Modifier,
)
```
Group headers are 13 sp `textSecondary`. Rows are ≥ 56 dp, with a 16 sp label and a 13 sp `textSecondary` value.
- **Appearance:** Theme = `SegmentedRow(System, Light, Dark)`; "Pure black in dark" = `SwitchRow`.
- **Text:**
  - Typeface = `SegmentedRow(Duo, Quattro, Mono)`. Each label is `Text(name, fontFamily = WriterFont.X.fontFamily)`.
  - "Text size" = `TextSizeRow`: `Slider(value, valueRange = 0f..5f, steps = 4)`, with a local `pending` step for the live sample "The quick brown fox" (in the chosen face, at `EditorMetrics.bodyTextSizeSp(pending, widthClass).sp`). The size is **persisted only in `onValueChangeFinished`**.
  - "Line length" = `SegmentedRow(64, 72, 80)`, shown only if `showLineLength`.
- **Writing:** Focus = `SegmentedRow(Off, Sentence, Paragraph)`; switches for Typewriter scrolling, Word count, "Swipe to library & preview" and "`==highlight==` syntax".
- **Files:** "New note extension" = `SegmentedRow(.md, .txt)`; "Show file extensions" = switch; "Export all notes…" = `ActionRow` → `onExportAll()`.
- **About:** "About mdwriter" = `ActionRow` → `onAbout()`.

Styling:
- `SwitchRow`: the whole row is `Modifier.toggleable(value, role = Role.Switch, onValueChange = …)`, and the `Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White, uncheckedTrackColor = surfaceHover, uncheckedThumbColor = textSecondary, uncheckedBorderColor = divider))`.
- `SegmentedRow`: `SingleChoiceSegmentedButtonRow`, with `SegmentedButton(shape = SegmentedButtonDefaults.itemShape(i, n), icon = {}, colors = SegmentedButtonDefaults.colors(activeContainerColor = surfaceHover, activeContentColor = text, inactiveContainerColor = surface, inactiveContentColor = textSecondary, activeBorderColor = divider, inactiveBorderColor = divider))`. `icon = {}` removes the default check mark.
- Slider colours: thumb and active track `accent`, inactive track `divider`.

§C Runtime style policy (engine) — **sketch; honours HARD RULES 2–5**
```kotlin
fun setStyle(style: EditorStyle) {                      // main thread; settings changes only (HARD RULE 2)
    if (style == current) return
    val old = current; current = style
    val anchor = scrollView.captureAnchor()           // first visible line's start offset + pixel delta
    val metrics = old.typeface != style.typeface || old.textSizeSp != style.textSizeSp ||
        old.lineSpacingExtraPx != style.lineSpacingExtraPx || old.geometry != style.geometry
    if (old.typeface != style.typeface) editText.typeface = style.typeface
    if (old.textSizeSp != style.textSizeSp) editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp)
    if (old.lineSpacingExtraPx != style.lineSpacingExtraPx) editText.setLineSpacing(style.lineSpacingExtraPx, 1f)
    editText.setTextColor(style.textColor); editText.highlightColor = style.selectionColor
    caretDrawable.update(style)                       // colour + extraPx/2 above/below (02 §3)
    geometry.apply(editText, style)                   // setPadding only if the computed values changed
    // Restyle every span ONCE, in place: each MdStyleSpan gets restyle(style) (add to the interface, default no-op).
    // metrics → the setters above already nulled the layout, so in-place metric changes cost one rebuild;
    // colour-only → no layout change: in-place + invalidate(). Never remove+re-add all spans with a live layout.
    editText.text.getSpans(0, editText.text.length, MdStyleSpan::class.java).forEach { it.restyle(style) }
    spanFactory.style = style                          // spans created from now on use the new style
    if (!metrics) editText.invalidate()
    scrollView.restoreAnchorAfterLayout(anchor)        // doOnPreDraw → scrollTo(lineTop(offset) - delta)
}
fun setHighlightSyntax(enabled: Boolean) {            // contract addition (01 §6.2)
    if (enabled == highlightEnabled) return
    // T07 already implements this branch inside setStyle (EditorStyle.highlightSyntax changed -> new highlighter +
    // fullScan of the live editable on main + restyler.markAllDirty()). REUSE it: setStyle(style.copy(highlightSyntax =
    // enabled)) or extract T07's branch into one private fun both call. Never keep two copies.
    refreshAccessibilityActions()                     // T09: More/Highlight visibility follows highlightEnabled
    // NOT touched: text, selection, MdUndoManager, scroll, IME, composing spans (HARD RULE 5)
}
```
If T06's spans already read their colours or sizes from a shared style holder at draw/measure time, `restyle` can be a no-op for those spans. Keep the "metrics → layout nulled first" ordering either way.

## Acceptance criteria
1. `SettingsContentTest` (Robolectric, `createComposeRule` from `…junit4.v2`, inside `MdWriterTheme`, with `FakeSettingsRepository`):
   - a) The group headers Appearance, Text, Writing, Files and About exist, and all 14 row labels of 02 §10 exist.
   - b) With `showLineLength = false`, "Line length" `assertDoesNotExist()`.
   - c) Clicking "Dark" → `themeMode == Dark` and "Dark" `assertIsSelected()`.
   - d) Clicking the "Pure black in dark" row → `pureBlack == true`.
   - e) "Mono" → `typeface == WriterFont.Mono`.
   - f) `performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }` on the slider → `textSizeStep == 5`, persisted exactly once.
   - g) ".txt" → `newNoteExtension == "txt"`.
   - h) "Export all notes…" → `onExportAll` called once; "About mdwriter" → `onAbout` called once.
2. `AboutContentTest`: it shows the text `BuildConfig.VERSION_NAME` and the substring "SIL Open Font License 1.1". Tapping "commonmark-java" shows text containing "Redistribution and use in source and binary forms".
3. `LicenseAssetsTest` (JVM, reads `src/main/assets/licenses/`): all 4 files exist and are longer than 500 bytes.
   - The OFL contains "SIL OPEN FONT LICENSE".
   - BSD contains "Redistribution and use".
   - MIT contains "Permission is hereby granted, free of charge".
   - Apache contains "Apache License" and "Version 2.0".
4. `make test` and `make check` are green.
5. `StyleSwitchTest` (device): a note with 300 lines, the caret at line 150, the scroll at line 140, and "abc" typed with `sendStringSync`. Then apply in turn, via `container.settings.update`, with idle waits of `waitForIdleSync` + 500 ms: themeMode Dark, pureBlack, font Mono, textSizeStep 5, textSizeStep 0.
   - After each step: the text is unchanged; `selectionStart`/`End` are unchanged; the first visible line's start offset is within ±1 line of before; `editText.textSize` equals the expected px for step 5 and step 0.
   - Finally, Ctrl+Z (`sendKeySync`) removes the typed "abc", and the text equals the pre-typing text.
6. `HighlightToggleTest`: a text containing `==mark==` gets highlight-kind spans when `highlightSyntax` is set true (use `MdStyleSpan`'s kind id API from T06), and gets none when it is set false. Selection and undo are as in 5.
7. Emulator screenshots:
   - The sheet in light and in dark (rows exactly as 02 §10; flat, no shadow; the typeface labels drawn in their own faces).
   - The editor in Quattro XS and in Mono XXL: body glyph height XXL ≈ 1.6× XS (24/15 sp, ±10 %).
   - After switching to Dark, the status-bar icons are light.
8. No activity recreation: `adb logcat -d | grep -c "MainActivity.onCreate"` does not change across switching theme, font and size (log line is debug-only via `dev.mdwriter.util.Log`, added in this task if absent).

## Verification commands
```sh
make test && make check
make install-debug DEVICE=emulator-5554
make test-device DEVICE=emulator-5554
adb -s emulator-5554 exec-out screencap -p > /tmp/t19-sheet-light.png
adb -s emulator-5554 shell cmd uimode night yes && adb -s emulator-5554 exec-out screencap -p > /tmp/t19-sheet-dark.png
adb -s emulator-5554 shell cmd uimode night no
adb -s emulator-5554 exec-out screencap -p > /tmp/t19-quattro-xs.png   # after choosing Quattro + XS in the sheet
adb -s emulator-5554 exec-out screencap -p > /tmp/t19-mono-xxl.png
adb -s emulator-5554 logcat -d | grep -c "MainActivity.onCreate"
```

## Pitfalls
- HARD RULE 2: `setTypeface`, `setTextSize`, `setLineSpacing` and `setPadding` run **only** in `setStyle` (a settings change or a width change), never from IME, scroll or insets code.
- HARD RULE 5: never `setText` or `clearSpans` to restyle. That resets undo, selection, IME composition and scroll. Remove or restyle only `MdStyleSpan`s.
- HARD RULE 3: no `beginBatchEdit` around restyling (A20: `endBatchEdit` scrolls to the caret).
- HARD RULE 4: do not add a document-wide `UpdateLayout` span to force a reflow; nulling the layout via the setters is the mechanism (A13, A14).
- Removing and re-adding every span with a live layout = per-span `DynamicLayout` reflows (seconds at 300k chars). Restyle in place.
- Slider: persisting on every `onValueChange` tick = one full reflow per tick. Persist in `onValueChangeFinished` only.
- A new `MarkdownHighlighter` must scan the **live** editable on main (it keeps a text reference; 01 §6.1). Never reset `MdUndoManager`.
- No `recreate()`, no `AppCompatDelegate` (HARD RULE 1: no AppCompat). The theme is state, not an activity theme.
- Licence files must contain the full texts, not URLs: the app has no network.
- `SegmentedButton` shows a check icon by default; pass `icon = {}`. The sheet must be flat: `tonalElevation = 0.dp`, colours from tokens (02 §2).

## Definition of done
- [ ] Every Acceptance criterion is met; `make check` and `make test-device DEVICE=emulator-5554` are green.
- [ ] 01 §6.2 updated (`setHighlightSyntax`; `setStyle` in-place policy). Settings fields added are listed under STATUS "Deviations" if they differ from §A.
- [ ] The licence text sources are recorded in STATUS. The screenshots are referenced in the STATUS entry.
- [ ] Commit `T19: settings sheet, live style switching, About and licences`.
