# T20 — Hardening: accessibility, large screens, config changes, huge files, IME, StrictMode

**Goal** The finished app (T01–T19) keeps working under TalkBack, 200 % font scale, tablets/split screen, forced RTL,
activity recreation, 300k-char and multi-MB files, real IME composition and a hardware keyboard. Debug builds run
StrictMode cleanly. Every scenario lives in `plans/QA-matrix.md`, which T22 re-runs.

**Depends on** T17 (find bar + shortcuts), T19 (all features in place). Also uses: T08 (editor shortcuts via
`MarkdownEditText.onKeyShortcut`), T11 (`EditorViewModel`, `EditorUiState.loading`, `StorageLimits` handling, restore
path), T13 (activity shortcuts, "Open library" a11y action, wide pane), T16 (preview). Read their STATUS entries first.

**Read first**
- `plans/01-architecture.md` §3, §6.2, §6.4, §8 (process death), §9 (1 MB/5 MB/binary), §10 rules 2, 4, 5, 8, 12.
- `plans/02-design-spec.md` §3 (breakpoints: gutter 0/4/6 chars, margins 24/32/48 dp), §6 (pill), §7 (drawer).
- `plans/research/platform.md` §7, §10.1, §10.2, §10.6, §10.7, §11 (`sed -n '560,587p;683,740p'`); `factcheck.md` §7
  items 5, 6, 8, 9, 10 (`sed -n '206,220p'`); `editor-engine.md` §7 items 2, 6, 8 (`sed -n '1193,1210p'`).

## Scope — In / Out
**In:** StrictMode (debug); `PerfLog` markers (T21 reads them); a11y audit and fixes (content descriptions, editor
custom actions, focus order, 48 dp targets, font scale, live region); large-screen/windowing verification and fixes;
recreation restore test; huge-file UX and the loading state; RTL sanity; IME composition test and manual Gboard
matrix; keyboard shortcuts helper; merged-manifest audit; lint zero errors; nav-mode swipe matrix;
`plans/QA-matrix.md`; `scripts/qa/` helpers.
**Out:**
- New features or shortcuts. List only the shortcuts that exist; record missing ones as follow-ups.
- Per-keystroke, open-time and cold-start numbers, and any perf fix → **T21**. T20 only adds markers.
- Baseline profile → **T21** (optional). README polish, data-safety scenarios, APK size → **T22**.
- Translations. Strings stay English in `strings.xml`.
- Changing `configChanges` (01 §8 is fixed). Adding dependencies (README rule 3). If a fix seems to need one,
  STOP-AND-ASK.

## Files to create / modify
- `app/src/main/kotlin/dev/mdwriter/MdWriterApp.kt`: modify. Enables StrictMode + `PerfLog` in debuggable builds.
- `app/src/main/kotlin/dev/mdwriter/util/PerfLog.kt`: new. Debug-only timing markers, logcat tag `MdPerf`.
- `app/src/main/kotlin/dev/mdwriter/util/StrictModeUtil.kt`: new. `permitDiskReads { }` for unavoidable platform reads.
- `app/src/main/kotlin/dev/mdwriter/ui/root/KeyboardShortcuts.kt`: new. `ShortcutCatalog` plus
  `Context.keyboardShortcutGroups()`.
- `app/src/main/kotlin/dev/mdwriter/MainActivity.kt`: modify. `onProvideKeyboardShortcuts`.
- `app/src/main/kotlin/dev/mdwriter/ui/editor/EditorAccessibility.kt`: new. `installEditorAccessibilityActions(...)`.
- `app/src/main/kotlin/dev/mdwriter/ui/editor/EditorHost.kt`: modify. Calls the above once in `factory`.
- `app/src/main/kotlin/dev/mdwriter/ui/editor/EditorScreen.kt`: modify. Loading placeholder, `PerfLog` marks.
- `app/src/main/kotlin/dev/mdwriter/ui/editor/EditorChrome.kt`, `ConflictBanner.kt`: modify. Polite live region on
  save errors and the conflict banner.
- Modify only for audit fixes (labels, `heightIn(min=)` instead of fixed heights, 48 dp):
  `app/src/main/kotlin/dev/mdwriter/ui/toolbar/FormatToolbar.kt`, `ui/library/*.kt`.
- Modify only if the RTL audit finds a `drawLeadingMargin` that ignores `dir`:
  `app/src/main/kotlin/dev/mdwriter/editor/spans/Spans.kt`.
- `app/src/main/res/values/strings.xml`: add a11y action labels, shortcut labels and groups, `editor_opening`.
- `app/lint.xml`: create or modify. Suppressions only, each with an XML comment giving the reason.
- `app/src/test/kotlin/dev/mdwriter/ui/root/ShortcutCatalogTest.kt`: JVM test.
- `app/src/androidTest/kotlin/dev/mdwriter/hardening/`: `TestViews.kt` (`Activity.findEditor()`, `runShell()`),
  `RecreateRestoreTest.kt`, `ImeCompositionTest.kt`, `KeyboardShortcutsTest.kt`, `TouchTargetsTest.kt`.
- `scripts/qa/gen-doc.sh`: `gen-doc.sh <chars>` writes synthetic Markdown of exactly N chars to stdout (T21 and T22
  reuse it).
- `scripts/qa/push-doc.sh`: `push-doc.sh <serial> <file>` copies a file into the **debug** app's `files/library/`.
- `plans/QA-matrix.md`: new. The table T22 re-runs.
- `plans/STATUS.md`: append the T20 entry.

## Steps
1. **Preflight.** `make emulator`, `make devices` (note the serial), `make install-debug DEVICE=emulator-5554`.
   Read the T08/T11/T13/T16/T17 STATUS entries. Note the shortcut handlers, test tags, and how
   `loading`/read-only/notice work.
2. **StrictMode + PerfLog** (Reference A, B). Gate them on the same debug check `util/Log.kt` uses (T01). Launch, then
   open a note, type for 30 s, switch notes, open preview, find, settings and the drawer, and export. Then run
   `adb logcat -d | grep -E 'StrictMode|MdStrict'`. **Fix every violation in our code** by moving I/O to
   `Dispatchers.IO` (01 §7). Wrap only framework-internal reads (WebView provider init, first font load) in
   `permitDiskReads {}`, with a comment. List each wrapped call in STATUS.
3. **Accessibility: labels and targets.** Run `adb shell uiautomator dump`, then the two checks in Verification
   (clickable nodes with no label, and clickable nodes under 48 dp). Check the editor, the pill with a selection, the
   drawer (open and search), preview, find, settings, and about. Fix with `contentDescription`/`semantics`,
   `Modifier.minimumInteractiveComponentSize()`, and `heightIn(min = …)`. Glyph buttons use the 02 §14 names:
   "Open library", "More options", "New note", and so on.
4. **Accessibility: editor custom actions** (Reference C). The actions are "Open library", "Show preview", "Find",
   plus one per formatting `ToolbarAction` the pill offers, labelled with the pill's own contentDescription strings.
   If T13 already added "Open library", move it into `EditorAccessibility.kt` so it is not registered twice.
5. **Focus order.** Enable TalkBack: `adb shell settings put secure enabled_accessibility_services
   com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`. If TalkBack is not
   installed on the AVD, write the manual steps in QA rows A11Y-02/03 and mark them "user". The expected order is the
   top chrome glyphs, then the editor. In the drawer: search, new note, folders, files. The pill's buttons follow the
   02 §6 order. Fix the order with `Modifier.semantics { traversalIndex = … }` or `isTraversalGroup`.
6. **Live region.** Save-error text (`SaveState.Error`) and `ConflictBanner` get
   `semantics { liveRegion = LiveRegionMode.Polite }`. `grep -rn announceForAccessibility app/src` must be empty; it
   is deprecated in Android 16 (platform §10.2).
7. **Font scale.** Run `adb shell settings put system font_scale 1.3`, then 2.0 (the activity recreates). Check that
   headings wrap and do not clip, the pill fits inside the window, drawer rows grow with no clipped text, and the
   editor body grew. The editor size must come from `TypedValue.applyDimension(COMPLEX_UNIT_SP, …)` or the
   equivalent (nonlinear scaling); never from `scaledDensity`. Reset to 1.0.
8. **Large screens.** Use `adb shell wm density 320` (the 448 dp AVD becomes 672 dp, the medium class), then
   `wm density 240` (896 dp, expanded), then landscape. Check the 02 §3 table: gutter 4 then 6 chars with `#`
   hanging, margins 32/48 dp, a 64-char centred column, and a permanent library pane at ≥ 840 dp. The activity must
   not recreate (density and screenSize are in configChanges); caret and undo survive. Then try split screen
   (Recents › app icon › split) and the `UNIVERSAL_RESIZABLE_BY_DEFAULT` compat flag. The merged manifest must have
   no `screenOrientation`, `resizeableActivity="false"` or `maxAspectRatio`. Finish with
   `wm density reset; wm size reset`.
9. **Recreation restore** (Reference D). Write `RecreateRestoreTest`. After `recreate()`, text, caret, scrollY (±1 line
   pitch) and the open find bar are identical. After a font-scale change the same holds. If an overlay does not come
   back, fix the `SavedStateHandle` wiring (01 §8) in the owning ViewModel.
10. **Don't keep activities and process kill** (manual, QA CFG-05/06). Run
    `adb shell settings put global always_finish_activities 1`, go Home, return: same doc, caret, scroll. Set it back
    to 0. For the kill: type, go Home within 1 s, `adb shell am kill dev.mdwriter.debug`, relaunch from Recents. The
    unsaved text must be back (recovery copy).
11. **Huge files.** Generate with `scripts/qa/gen-doc.sh`, push with `push-doc.sh`: 300k chars, 1.2 MB, 6 MB, a
    binary file (`head -c 4096 /dev/urandom`), 17 MB. Force-stop, relaunch, open each from the drawer. Expected:
    300k shows the loading placeholder ≤ 100 ms after `open.request` (`adb logcat -s MdPerf`), no ANR; 1.2 MB shows the
    "Large document" notice; 6 MB opens read-only plain (no styling, no IME); binary and 17 MB are refused with a
    message (`TooLarge` for 17 MB). Implement the placeholder (Reference E). If T11 left a threshold unimplemented,
    implement the minimal version and record a Deviation.
12. **RTL.** Push the Reference F note. At 448 dp and 672 dp: list/quote indents and the quote bar are on the right;
    an RTL heading's `#` hangs on the right at ≥ 600 dp; nothing clipped; caret moves correctly; no crash. Force RTL:
    `adb shell settings put global debug.force_rtl 1`, confirm with `settings get global debug.force_rtl` (prints `1`),
    `am force-stop`, relaunch: the drawer comes from the right and swipe directions flip. If nothing mirrors, use
    Developer options › "Force RTL layout direction" and record which worked. CJK paragraph wraps. Reset to 0.
    Accepted limitation: RTL paragraphs in an LTR column are offset by the gutter. Record it; do not re-architect.
13. **IME.** Write `ImeCompositionTest` (Reference G). Manual Gboard rows IME-01…05: tap keys at screenshot
    coordinates; glide via `input motionevent DOWN/MOVE…/UP`. Suggestion popup: type a misspelling, edit two lines
    *above* it, tap the underlined word; the popup must appear for the right word (SpanController vs MdEditable,
    factcheck §7.5). Voice input is "user/phone" unless the AVD has a mic.
14. **Hardware keyboard and shortcuts helper** (Reference H). Build `ShortcutCatalog` from the real handlers (grep
    `onKeyShortcut`/`KEYCODE_` in `editor/` and `MainActivity.kt`, `onPreviewKeyEvent` in `ui/`). Implement
    `onProvideKeyboardShortcuts`, write `KeyboardShortcutsTest`, open the helper with
    `adb shell input keycombination KEYCODE_META_LEFT KEYCODE_SLASH` and take a screenshot.
15. **Manifest audit.** `make apk KEYSTORE_DIR=/tmp/mdwriter-agent-key`, then `aapt2 dump permissions`/`xmltree`
    (Verification). Allowed: uses-permission only `dev.mdwriter.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (androidx);
    exported only `.MainActivity` and profileinstaller's `ProfileInstallReceiver` (guarded by
    `android.permission.DUMP`); `FileProvider` `exported="false"`; release not `debuggable`.
16. **Lint.** Run `make lint`. Fix all errors. Fix warnings or triage them into `app/lint.xml` with a reason; list the
    triaged IDs in STATUS.
17. **Navigation modes.** Test gestural, then three-button:
    `adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton`, and
    `...navbar.gestural` to switch back. If that fails, use Settings › System › Navigation mode. Re-run the swipe
    matrix from platform §11 (rows NAV-*). On three-button nav, long-press Back must show the predictive-back preview.
18. **Write `plans/QA-matrix.md`** with every row ID below, columns `ID | Area | Scenario | How | Expected | T20 | T22`.
    Fill in the `T20` column (`pass` / `fail → fixed in <commit>` / `user` / `n/a (why)`). Leave the `T22` column empty.
19. Run `make check` and `make test-device DEVICE=emulator-5554`, append the STATUS entry, and commit.

**QA-matrix row IDs** (one row each):
- **A11Y** 01 labels on every clickable node (dump check) · 02 editor custom actions in TalkBack · 03 focus order ·
  04 targets ≥ 48 dp · 05 font 1.3 · 06 font 2.0 · 07 save error announced (`run-as … chmod 500 files/library`,
  type, expect Error + announce; then `chmod 700`).
- **LS** 01 448 dp baseline · 02 672 dp · 03 896 dp + permanent pane · 04 rotation keeps undo/IME · 05 split screen /
  tiny window · 06 compat flag · 07 no orientation locks.
- **CFG** 01 `recreate()` · 02 fontScale · 03 layoutDirection · 04 `cmd uimode night yes/no` (no recreation, colours
  update) · 05 don't keep activities · 06 process kill.
- **BIG** 01 300k loading ≤ 100 ms · 02 1 MB notice · 03 5 MB read-only · 04 binary refused · 05 > 16 MB refused.
- **RTL** 01 448 dp · 02 672 dp hang · 03 force RTL + swipe flip · 04 CJK.
- **IME** 01 fast typing · 02 autocorrect above styled text · 03 glide · 04 voice (user) · 05 suggestion popup after
  edits above · 06 `ImeCompositionTest`.
- **KB** 01 host keyboard + Ctrl shortcuts · 02 Meta+/ helper.
- **NAV** 01 gestural swipe matrix · 02 three-button + predictive back.
- **Other** SM-01 StrictMode clean · MAN-01 manifest · LINT-01 lint.

## Reference code
**A. StrictMode** (sketch; in `MdWriterApp.onCreate`, before creating `AppContainer`)
```kotlin
if (isDebugBuild) {                       // same gate as util/Log.kt (BuildConfig.DEBUG or FLAG_DEBUGGABLE)
    PerfLog.enabled = true
    val ex = mainExecutor
    StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
        .detectDiskReads().detectDiskWrites().detectNetwork().detectCustomSlowCalls()
        .detectResourceMismatches().detectUnbufferedIo()
        .penaltyLog().penaltyListener(ex) { v -> Log.w("MdStrict", "thread", v) }.build())
    StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())   // keep T18's detectImplicitUriPermissionGrant
        .detectLeakedClosableObjects().detectLeakedRegistrationObjects().detectActivityLeaks()
        .detectContentUriWithoutPermission().detectFileUriExposure().detectUnsafeIntentLaunch()
        .detectIncorrectContextUse()
        .penaltyLog().penaltyListener(ex) { v -> Log.w("MdStrict", "vm", v) }.build())
}
```
Never `penaltyDeath` (it would crash the user's debug app). `util/StrictModeUtil.kt`:
`inline fun <T> permitDiskReads(block: () -> T): T { val old = StrictMode.allowThreadDiskReads(); try { return block() } finally { StrictMode.setThreadPolicy(old) } }`

**B. PerfLog** (write it as shown; T21 depends on these event names)
```kotlin
object PerfLog {
    const val TAG = "MdPerf"
    @Volatile var enabled: Boolean = false
    private val marks = java.util.concurrent.ConcurrentHashMap<String, Long>()
    fun mark(event: String) { if (enabled) marks[event] = SystemClock.elapsedRealtimeNanos() }
    /** Logs "span <from>-><event> ms=<x.y>". No-op if disabled or <from> was never marked. */
    fun since(from: String, event: String) {
        if (!enabled) return
        val t0 = marks[from] ?: return
        android.util.Log.i(TAG, String.format(Locale.ROOT, "span %s->%s ms=%.1f", from, event,
            (SystemClock.elapsedRealtimeNanos() - t0) / 1e6))
    }
}
```
Events: `open.request` (`mark` in `EditorViewModel` when any open starts); then, each logged with
`since("open.request", …)`: `open.loadingShown` (placeholder's first composition), `open.installed` (end of
`EditorController.install`), `open.firstFrame` (`editText.doOnPreDraw {}` registered right after install).

**C. Editor accessibility actions** (sketch)
```kotlin
class EditorA11yCallbacks(val openLibrary: () -> Unit, val showPreview: () -> Unit, val openFind: () -> Unit,
                          val format: (ToolbarAction) -> Unit)
fun installEditorAccessibilityActions(editText: MarkdownEditText, formatting: List<Pair<String, ToolbarAction>>,
                                      cb: () -> EditorA11yCallbacks) {   // cb reads rememberUpdatedState values
    val res = editText.resources
    ViewCompat.addAccessibilityAction(editText, res.getString(R.string.a11y_open_library)) { _, _ -> cb().openLibrary(); true }
    ViewCompat.addAccessibilityAction(editText, res.getString(R.string.a11y_show_preview)) { _, _ -> cb().showPreview(); true }
    ViewCompat.addAccessibilityAction(editText, res.getString(R.string.a11y_find)) { _, _ -> cb().openFind(); true }
    formatting.forEach { (label, action) -> ViewCompat.addAccessibilityAction(editText, label) { _, _ -> cb().format(action); true } }
}
```
Call it once from `EditorHost`'s `factory`, not from `update`. Pass `format = controller::perform`.

**D. RecreateRestoreTest** (sketch; v2 rule `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule<MainActivity>()`)
- Make a fresh note via `onActivity { it.onKeyShortcut(KEYCODE_N, ctrlDown(KEYCODE_N)) }` (T13's Ctrl+N), then
  `waitUntil(5_000) { editor text is empty }`.
- On main: `editText.text.insert(0, BODY)`, where BODY is 200 lines
  (`"# Restore\n\n" + (1..200).joinToString("\n") { "Line $it with *em*" }`). Then `editText.setSelection(420)` and
  `(editText.parent as EditorScrollView).scrollTo(0, 3000)`. Open find with Ctrl+F.
- Test 1: `rule.activityRule.scenario.recreate()`, then `waitUntil(5_000) { findEditor()?.text?.toString() == BODY }`.
  Assert caret == 420, |scrollY − 3000| ≤ one line pitch, and `onNodeWithTag("findBar").assertExists()`. Add
  `Modifier.testTag("findBar")` to T17's FindBar if it is missing.
- Test 2 (`fontScaleChangeRestoresDocument`): `runShell("settings put system font_scale 1.3")` via
  `uiAutomation.executeShellCommand`; read and close the `ParcelFileDescriptor`. Wait until the RESUMED activity
  (`ActivityLifecycleMonitorRegistry`) is a new instance, then run the same assertions except scrollY (the layout
  changed; assert the caret line is visible). Reset `font_scale 1.0` in `@After`.

**E. Loading placeholder** (sketch, `EditorScreen`)
```kotlin
var showOpening by remember { mutableStateOf(false) }
LaunchedEffect(ui.loading) { showOpening = false; if (ui.loading) { delay(50); showOpening = true } }
EditorHost(…, modifier = Modifier.graphicsLayer { alpha = if (showOpening) 0f else 1f })
if (showOpening) {
    SideEffect { PerfLog.since("open.request", "open.loadingShown") }
    Text(stringResource(R.string.editor_opening), color = /* T02 textSecondary */, style = /* T02 small */,
        modifier = Modifier.testTag("editorLoading").semantics { liveRegion = LiveRegionMode.Polite })
}
```
The 50 ms delay stops fast opens from flickering. `EditorUiState.loading` must be true for every open, not just the
first. Fix that in `EditorViewModel` if needed.

**F. RTL sample** (write it to a file and push it)
```
# שלום עולם כותרת
פסקה בעברית עם **הדגשה** וטקסט ארוך מספיק כדי לגלוש לשורה נוספת במסך הטלפון הצר.
- פריט רשימה ארוך שגולש לשורה שנייה כדי לבדוק הזחה תלויה
> ציטוט בעברית שגולש לשורה שנייה ובודק את פס הציטוט בצד הנכון
- [ ] משימה
## مرحبا بالعالم
فقرة عربية مع *تأكيد* ونص طويل بما يكفي للالتفاف إلى سطر آخر.
日本語の段落は折り返しを確認するための長い文章です。日本語の段落は折り返しを確認するための長い文章です。
```
Every custom `LeadingMarginSpan.drawLeadingMargin(c, p, x, dir, …)` must draw relative to `x` with `dir`
(`dir == -1` means `x` is the right edge).

**G. ImeCompositionTest** (sketch). On the main thread, `val ic = editText.onCreateInputConnection(EditorInfo())!!`.
This is T08's `SmartInput` wrapper, which is the real path. Cases:
1. `setComposingText("h",1)`, `"he"`, `"hel"`, `"hello"`, then `finishComposingText()` and `commitText(" ",1)`.
   Expect the text to end with `"hello "` and the heading span on line 0 to be unchanged.
2. Autocorrect above styled text. Doc `"teh cat\n\nSome **bold** text\n"`: `setComposingRegion(0,3)`, then
   `commitText("the",1)`. The strong span covers the same characters (shift +0), with no duplicate `MdStyleSpan`s on
   line 2.
3. SuggestionSpan shift. `commitText` a `SpannableString("wrold")` carrying
   `SuggestionSpan(ctx, arrayOf("world"), FLAG_EASY_CORRECT)` at line 3. Then `setSelection(0)` and
   `commitText("New line\n",1)`. Exactly one `SuggestionSpan` remains, still over `"wrold"`, with start shifted by 9
   (HARD RULE 5).
4. While composing `"**bo"`, `waitForIdleSync()` lets a restyle frame run. `BaseInputConnection.getComposingSpanStart`
   is still ≥ 0 and the composing text is intact.
5. `beginBatchEdit(); commitText("a",1); deleteSurroundingText(1,0); commitText("b",1); endBatchEdit()`. Text and
   spans equal a fresh full restyle: use T07's equality helper if it exists, otherwise compare
   `getSpans(0,len,MdStyleSpan)` kinds and ranges before and after an `EditorController.setStyle(sameStyle)` reflow.

**H. Shortcuts** (sketch)
```kotlin
enum class ShortcutGroup(@StringRes val title: Int) { File(R.string.kbd_file), Edit(R.string.kbd_edit),
    Format(R.string.kbd_format), View(R.string.kbd_view) }
enum class ShortcutTarget { Editor, Activity }   // who handles it: MarkdownEditText.onKeyShortcut vs MainActivity
data class ShortcutSpec(@StringRes val label: Int, val keyCode: Int, val meta: Int, val group: ShortcutGroup,
                        val target: ShortcutTarget)
object ShortcutCatalog { val all: List<ShortcutSpec> = listOf(/* exactly the implemented ones */) }
fun Context.keyboardShortcutGroups(): List<KeyboardShortcutGroup> = ShortcutGroup.entries.mapNotNull { g ->
    val items = ShortcutCatalog.all.filter { it.group == g }.map { KeyboardShortcutInfo(getString(it.label), it.keyCode, it.meta) }
    if (items.isEmpty()) null else KeyboardShortcutGroup(getString(g.title), items) }
// MainActivity (use the exact override signature Studio generates for API 37):
override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
    super.onProvideKeyboardShortcuts(data, menu, deviceId); data.addAll(keyboardShortcutGroups()) }
```
- `ShortcutCatalogTest` (JVM): no duplicate `(keyCode, meta)` pair; each `meta` includes `META_CTRL_ON`; every group
  is used.
- `KeyboardShortcutsTest`: for each spec, send `KeyEvent(0, 0, ACTION_DOWN, keyCode, 0, meta)` to the target's
  `onKeyShortcut` and assert `true` (run the formatting ones on a scratch note). Also assert that
  `onProvideKeyboardShortcuts` output contains every spec.

## Acceptance criteria
1. `adb logcat -d | grep -cE 'StrictMode|MdStrict'` prints `0` after the step 2 flow. Only documented
   `permitDiskReads` sites remain.
2. The uiautomator dump checks print nothing for the editor, pill, drawer, preview, find and settings: no clickable
   node without a label, and none smaller than 48 dp.
3. `RecreateRestoreTest` (2 tests), `ImeCompositionTest` (5), `KeyboardShortcutsTest`, and `TouchTargetsTest` pass.
   `TouchTargetsTest` checks that every `hasClickAction()` node in the pill and drawer is ≥ 48 dp via `getBoundsInRoot`.
4. `ShortcutCatalogTest` passes. The Meta+/ screenshot shows the mdwriter groups with every catalog entry.
5. At font_scale 2.0: a 40-char H1 wraps onto ≥ 2 lines with no glyph cut off; the body line pitch is ≥ 1.5× the pitch
   at 1.0 (measured in the screenshot); the pill bounds are inside the window (dump); drawer row text is not clipped.
6. At 672 dp and 896 dp, `#` hangs left of the text edge. At 896 dp the library pane is permanent. At 448 dp there is
   no hang (screenshots).
7. `adb logcat -s MdPerf` shows `open.request->open.loadingShown ms≤100` for the 300k doc. The 1.2 MB, 6 MB, binary
   and 17 MB files behave as in step 11.
8. RTL screenshots show indents, the quote bar and the heading hang on the right side for RTL paragraphs.
   `adb logcat -d | grep -c 'FATAL EXCEPTION'` prints `0`.
9. The manifest audit prints only the allowed permission and the two allowed exported components.
10. `make lint` reports 0 errors. `grep -rn announceForAccessibility app/src` is empty.
11. `plans/QA-matrix.md` has every row ID above, with the T20 column filled in.
12. `make check` and `make test-device DEVICE=emulator-5554` are green.

## Verification commands
```sh
make install-debug DEVICE=emulator-5554
ADB=~/Library/Android/sdk/platform-tools/adb; S=emulator-5554
$ADB -s $S shell uiautomator dump /sdcard/ui.xml >/dev/null && $ADB -s $S exec-out cat /sdcard/ui.xml > /tmp/ui.xml
D=$($ADB -s $S shell wm density | tail -1 | grep -o '[0-9]*$'); MIN=$((48*D/160))
perl -ne 'while(/<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?clickable="true"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/g){
  print "NOLABEL $3,$4\n" if $1 eq "" && $2 eq ""; print "SMALL $1$2 ".($5-$3)."x".($6-$4)."\n" if ($5-$3)<'$MIN' || ($6-$4)<'$MIN';}' /tmp/ui.xml
$ADB -s $S shell settings put system font_scale 2.0   # ...then 1.0
$ADB -s $S shell wm density 320; $ADB -s $S shell wm density 240; $ADB -s $S shell wm density reset
$ADB -s $S shell am compat enable UNIVERSAL_RESIZABLE_BY_DEFAULT dev.mdwriter.debug
scripts/qa/gen-doc.sh 300000 > /tmp/big-300k.md && scripts/qa/push-doc.sh $S /tmp/big-300k.md
$ADB -s $S logcat -s MdPerf
$ADB -s $S shell input keycombination KEYCODE_META_LEFT KEYCODE_SLASH
$ADB -s $S exec-out screencap -p > /tmp/shot.png
make apk KEYSTORE_DIR=/tmp/mdwriter-agent-key
AAPT=~/Library/Android/sdk/build-tools/36.0.0/aapt2; APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
$AAPT dump permissions $APK
$AAPT dump xmltree --file AndroidManifest.xml $APK | grep -E 'E: (activity|receiver|provider|service)|exported|A: android:name|permission'
make lint && make check && make test-device DEVICE=emulator-5554
```
`push-doc.sh`: `adb -s "$1" shell "run-as dev.mdwriter.debug sh -c 'mkdir -p files/library && cat > files/library/$(basename "$2")'" < "$2"`
(fallback, UNVERIFIED: push to `/data/local/tmp/` + `run-as … cp`). `gen-doc.sh`: repeat a fixed ~600-char block
(H1/H2, `*em*`, `**strong**`, `` `code` ``, `[link](https://example.com)`, list, quote, fence), `head -c "$1"`.

## Pitfalls
- HARD RULE 2: font-scale/density fixes never call `setTextSize`/`setPadding` from IME or scroll callbacks, only from
  width or settings changes. HARD RULE 8: no view ID, `isSaveEnabled=false`; restore comes from disk/recovery, never
  the Bundle. HARD RULE 12: no `systemGestureExclusion` as a fix for nav-mode swipe conflicts.
- HARD RULE 5 and factcheck §7.5: never remove `SuggestionSpan`/`SpellCheckSpan` while fixing IME issues. If
  suggestion popups break, report it (the MdEditable suppression rule is T06's design) and STOP-AND-ASK.
- `wm density` and `font_scale` stick across app restarts: always reset them. `am kill` only kills a backgrounded
  process: press Home first. `announceForAccessibility` is deprecated (Android 16): use live regions.
- The `testTag`s you add (`findBar`, `editorLoading`) are a contract for T22. Keep them.
- The accessibility services setting persists. Clear it (`adb shell settings delete secure
  enabled_accessibility_services`) before T21's perf work: accessibility copies the whole text on every keystroke
  (editor-engine §7.6).

## Definition of done
- [ ] Steps 1–19 done; every QA-matrix row has a T20 result; criteria 1–12 met with evidence in STATUS.
- [ ] Emulator reset: font_scale 1.0, `wm` reset, force_rtl 0, always_finish_activities 0, TalkBack off, gestural nav.
- [ ] `make check` and `make test-device DEVICE=emulator-5554` green.
- [ ] STATUS entry lists: StrictMode allowlist, lint triage, the RTL limitation, missing shortcuts, PerfLog events.
- [ ] Commit `T20: hardening (a11y, large screens, restore, huge files, IME, StrictMode, QA matrix)`.
