# T13 — Swipe navigation, editor chrome, overflow menu, back handling, wide-screen pane

**Goal** A horizontal swipe anywhere in the editor opens the library (start→end). The reverse swipe (end→start) is reserved for the preview, which T16 wires up. The swipe never steals a selection, a cursor drag or a vertical scroll. The editor shows only two faded glyph buttons (library, overflow). They disappear on the first keystroke and come back when the writer pauses. Back closes things in a fixed order, and on windows ≥ 840 dp the library becomes a permanent pane. After this task the app is the first version worth installing on the phone (README milestone).

**Depends on**
- T12: `MdWriterRoot` with a hoisted `EditorController`, `ModalNavigationDrawer(gesturesEnabled = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open)`, `ModalDrawerSheet(drawerState)`, `LibraryDrawer(vm, openDoc)`, and the drawer IME hide/restore effect. Also `LibraryViewModel` (breadcrumb, search, new note, `LibraryEvent.CloseDrawer`) and the temporary library glyph in `EditorScreen`.
- T11: `EditorViewModel`, `EditorUiState` (01 §6.4), `SettingsRepository.settings: Flow<Settings>` + `update {}`.
- T08: `controller.undo()/redo()/canUndo/canRedo`, and `MarkdownEditText.onKeyShortcut` (editor shortcuts).
- T05: `EditorHost` (AndroidView over `controller.scrollView`), `EditorScrollView`, and `EditorGeometry` (padding recomputed on a width change only).
- T02: `WriterColors`/`LocalWriterColors`, `WriterMotion`, `WriterDimens`, `WidthClass`, icons `ic_left_panel_open`, `ic_left_panel_close`, `ic_more_vert`, `ic_undo`, `ic_redo`, `ic_edit_square`.

**Read first**
- `plans/01-architecture.md` §4.8, §6.2, §6.4, §7, §10 (rules 2, 12)
- `plans/02-design-spec.md` §5 (Chrome), §7 (first bullet), §9, §11, §12 (wireframes), §14
- `plans/research/platform.md` §4.1–§4.2 (`sed -n '384,470p'`), §5 (`sed -n '468,497p'`), §7 (`sed -n '560,588p'`), §10.1–§10.3 (`sed -n '683,703p'`)
- `plans/research/design.md` §4.7 (`sed -n '330,341p'`), §6.1–§6.2 and §6.8 (`sed -n '472,510p;608,627p'`)
- `plans/research/factcheck.md` rows A3–A9, A22 (`grep -n '^| A[3-9] \|^| A22' plans/research/factcheck.md`)

## Scope — In / Out
In:
- `Modifier.editorSwipeNav`, the pure `SwipeClassifier`, and `SwipeTuning`. The modifier goes on the Box that hosts the AndroidView in `EditorHost`, and is disabled when `settings.swipeNavigation == false`.
- `EditorChrome` (library + overflow glyphs, stats slot), the pure `ChromeVisibility` state machine, and `Modifier.observeTopTap`.
- `OverflowMenu` with the entries that exist now (undo/redo icon row, New note) plus typed extension points.
- Back ordering for selection, find, drawer (search clear → folder up → close) and the preview slot.
- Expanded layout (≥ 840 dp): permanent 320 dp `LibraryPane` + a 1 px divider, toggled by the glyph and by swipes.
- App keyboard shortcuts (Ctrl+N, Ctrl+O, Ctrl+L), published to the Keyboard Shortcuts Helper.
- The accessibility custom action "Open library" on the editor.
- `EditorController.scrollChanges` (contract addition, see Reference §F).

Out (owner):
- Preview overlay, Ctrl+R, the "Show preview" action and the preview `PredictiveBackHandler` → **T16**. T13 only leaves `onOpenPreview` as a no-op and sets `previewAvailable = false`.
- Focus / Typewriter / Word count rows and the stats line content → **T15**. T13 provides the `OverflowActions` fields and the `stats` slot.
- The Find row/icon, the find bar and Ctrl+F → **T17**. Share icon → **T18**. Settings row → **T19**.
- Linked-folder rows in the library → T14. Accessibility polish, font-scale tests and desktop windowing → T20.
- Do not change the drawer IME logic, auto-naming or file operations (all T12).

## Files to create / modify
- `app/src/main/kotlin/dev/mdwriter/ui/gesture/SwipeTuning.kt` — `object SwipeTuning` (every threshold)
- `app/src/main/kotlin/dev/mdwriter/ui/gesture/SwipeClassifier.kt` — `SwipeDir`, `SwipeDecision`, pure `SwipeClassifier`
- `app/src/main/kotlin/dev/mdwriter/ui/gesture/EditorSwipeNav.kt` — `fun Modifier.editorSwipeNav(...)`
- `app/src/main/kotlin/dev/mdwriter/ui/editor/ChromeVisibility.kt` — pure show/hide state machine (virtual-time tested)
- `app/src/main/kotlin/dev/mdwriter/ui/editor/EditorChrome.kt` — `EditorChrome(...)` and `Modifier.observeTopTap(...)`
- `app/src/main/kotlin/dev/mdwriter/ui/editor/OverflowMenu.kt` — `OverflowActions`, `OverflowToggle`, `OverflowChoice`, `OverflowMenu(...)`
- `app/src/main/kotlin/dev/mdwriter/ui/library/LibraryPane.kt` — permanent pane (T12's `LibraryDrawer` content in a 320 dp column)
- `app/src/main/kotlin/dev/mdwriter/ui/root/AppCommands.kt` — `enum class AppCommand`, `object AppShortcuts` (pure key map)
- `app/src/main/kotlin/dev/mdwriter/ui/root/MdWriterRoot.kt` (modify) — adaptive layout, swipe/command wiring, back handlers
- `app/src/main/kotlin/dev/mdwriter/ui/editor/EditorScreen.kt` (modify) — remove T12's temporary glyph; host `EditorChrome`
- `app/src/main/kotlin/dev/mdwriter/ui/editor/EditorHost.kt` (modify) — swipe + top-tap modifiers, a11y action
- `app/src/main/kotlin/dev/mdwriter/ui/editor/EditorViewModel.kt` (modify) — `val chrome: ChromeVisibility`, `closeFind()`; `chromeVisible` in the UI state
- `app/src/main/kotlin/dev/mdwriter/editor/EditorController.kt`, `EditorScrollView.kt` (modify) — `scrollChanges`
- `app/src/main/kotlin/dev/mdwriter/MainActivity.kt` (modify) — `onKeyShortcut`, `onProvideKeyboardShortcuts`, `commands` flow
- `app/src/main/kotlin/dev/mdwriter/data/settings/Settings.kt` (+ repository) (modify only if missing) — `swipeNavigation: Boolean = true`, key `swipe_navigation`
- `app/src/main/res/values/strings.xml` (modify) — all new strings
- `app/src/test/kotlin/dev/mdwriter/ui/gesture/SwipeClassifierTest.kt`, `ui/editor/ChromeVisibilityTest.kt`, `ui/root/AppShortcutsTest.kt` — JVM
- `app/src/test/kotlin/dev/mdwriter/ui/editor/OverflowMenuTest.kt` — Robolectric Compose
- `app/src/androidTest/kotlin/dev/mdwriter/ui/gesture/SwipeNavTest.kt` + `androidTest/.../testing/TouchInjector.kt` — device swipe matrix

## Steps
1. Read the STATUS entries of T05, T08, T11 and T12. Then check the current names: `grep -rn "fun \|val " app/src/main/kotlin/dev/mdwriter/ui/library/LibraryViewModel.kt app/src/main/kotlin/dev/mdwriter/ui/root/MdWriterRoot.kt`. You need T12's names for: new note (the + button handler), go up one folder, clear search, whether the view is at root, and whether search is active. If folder-up or clear-search is missing, add `fun navigateUp()` and `fun clearSearch()` to `LibraryViewModel`.
2. Write `SwipeTuning` and `SwipeClassifier` (Reference §A), then `SwipeClassifierTest` (cases listed in Acceptance 1). Run `make test`.
3. Write `Modifier.editorSwipeNav` (Reference §B). Apply it in `EditorHost` to the Box around the `AndroidView`, together with `Modifier.observeTopTap` (§C). Call sites pass lambdas wrapped in `rememberUpdatedState` (the modifier uses `pointerInput(Unit)`).
   - `enabled()` is true only when all of these hold: `settings.swipeNavigation`, `controller.selection.value.start == controller.selection.value.end`, `!ui.drawerOpen`, `!ui.previewOpen`, `!ui.findOpen`, `!ui.settingsOpen`.
   - `onArmedDown` snapshots `controller.editText.selectionStart/End`.
   - On a swipe: restore the snapshot with `controller.editText.setSelection(s, e)`, then perform haptic `HapticFeedbackType.GestureThresholdActivate` (`LocalHapticFeedback`), then route it (step 7).
   - Do **not** call `hideIme()` for TowardEnd. T12's drawer effect records `restoreIme` from the still-visible IME and hides it; hiding first would lose the restore.
4. Add `scrollChanges` to the engine (§F). Write `ChromeVisibility` (§D) and hold it in `EditorViewModel` as `val chrome = ChromeVisibility(viewModelScope)`. `uiState.chromeVisible` combines `chrome.visible`. Wire the signals in `EditorScreen`:
   - `controller.edits` → `onEdit()`
   - `WindowInsets.isImeVisible` → `onImeVisibility()`
   - `controller.scrollChanges` → `onScroll(dy, 24.dp.toPx())`
   - top tap → `onTopTap()`
5. Write `EditorChrome` (§C). Remove T12's temporary glyph. The glyph row sits under `WindowInsets.safeDrawing.only(Top + Horizontal)` and is 56 dp tall. Glyph insets come from `WriterDimens.chromeGlyphInset(widthClass)`. Each icon is 24 dp in a 48 dp target, tinted `textSecondary`. Visibility: `AnimatedVisibility(visible || overflowExpanded, enter = fadeIn(tween(CHROME_FADE_IN_MS, easing = chromeFadeInEasing)), exit = fadeOut(tween(CHROME_FADE_OUT_MS, easing = chromeFadeOutEasing)))`. While hidden, the glyphs are out of composition, so they are not clickable. The `stats` slot sits **outside** that AnimatedVisibility and receives `chromeVisible` (T15 renders it at 60 % alpha while typing).
6. Write `OverflowMenu` (§E) and anchor it under the overflow glyph.
   - Style: `DropdownMenu(shape = RoundedCornerShape(12.dp), containerColor = colors.surface, tonalElevation = 0.dp, shadowElevation = 0.dp, border = BorderStroke(hairline, colors.divider), modifier = Modifier.width(240.dp))`, where `hairline = (1f / LocalDensity.current.density).dp`.
   - Row order: icon row (undo, redo, [find], [share]) → New note → [Preview] → [Focus ▸] → [Typewriter] → [Word count] → [Settings]. A `null` field hides its row.
   - Every item dismisses the menu before running its action.
7. Wire everything in `MdWriterRoot` (§G):
   - `openLibrary()`: collapse the selection; close the preview and find if they are open; then `drawerState.open()` (compact) or `paneVisible = true` (expanded).
   - `toggleLibrary()`.
   - `onOpenPreview: () -> Unit = {}` and `previewAvailable = false` (T16 replaces both).
   - Routing: TowardEnd → `openLibrary()`. TowardStart → in expanded mode with the pane visible, hide the pane; otherwise call `onOpenPreview()`.
   - `accepts(dir)`: TowardEnd = `!(expanded && paneVisible)`; TowardStart = `(expanded && paneVisible) || previewAvailable`. A rejected direction is not consumed, so the EditText keeps the gesture.
8. Build the expanded layout (§G) with `currentWindowAdaptiveInfoV2()`. Keep the editor subtree in `movableContentOf` so switching compact ↔ expanded never re-creates the `AndroidView` (the scroll view has one parent). On entering expanded mode, `drawerState.snapTo(Closed)`. The glyph icon is `ic_left_panel_close` when the pane is visible, else `ic_left_panel_open`. Showing or hiding the pane is instant (no width animation).
9. Back handlers (§H): add the search-clear and folder-up handlers **inside** T12's drawer content, enabled only when `drawerState.isOpen` (never in pane mode), and the root handlers last in `MdWriterRoot`. Add `EditorViewModel.closeFind()` (sets `findOpen = false`; T17 extends it).
10. Shortcuts (§I):
    - Write `AppCommand` and `AppShortcuts.map(...)`. `MainActivity.onKeyShortcut` emits into `val commands = MutableSharedFlow<AppCommand>(extraBufferCapacity = 8)`, which is passed to `MdWriterRoot(container, commands)` and collected there.
    - `onProvideKeyboardShortcuts` publishes N/O/L.
    - Make sure `MarkdownEditText.onKeyShortcut` returns `super.onKeyShortcut(...)` (false) for keys it does not own, so Ctrl+N/O/L reach the activity.
11. Add the accessibility action in `EditorHost`: `DisposableEffect(controller) { val id = ViewCompat.addAccessibilityAction(controller.editText, label) { _, _ -> openLibrary(); true }; onDispose { ViewCompat.removeAccessibilityAction(controller.editText, id) } }`.
12. Write the tests (Acceptance). Run `make check` and the device tests, take the screenshots, update 01 §6.2 (`scrollChanges`) in the same commit, write STATUS, and commit.

## Reference code
§A `SwipeTuning.kt` + `SwipeClassifier.kt` — **copy (verified logic from platform §4.2, extracted to be pure)**
```kotlin
object SwipeTuning { // empirical starting points (platform §4.2); tune on device, record changes in STATUS
    const val COMMIT_DP = 56f; const val RATIO = 2.5f; const val MAX_COMMIT_MS = 600L
    const val FLICK_DP_PER_S = 1000f; const val MIN_FLICK_DP = 24f
}
enum class SwipeDir { TowardEnd, TowardStart } // TowardEnd = left→right in LTR
sealed interface SwipeDecision {
    data object Undecided : SwipeDecision; data object Abort : SwipeDecision
    data class Commit(val dir: SwipeDir) : SwipeDecision
}
class SwipeClassifier(private val touchSlopPx: Float, private val longPressMs: Long, density: Float, private val rtl: Boolean) {
    private val commitPx = SwipeTuning.COMMIT_DP * density
    private val minFlickPx = SwipeTuning.MIN_FLICK_DP * density
    private val flickPxPerS = SwipeTuning.FLICK_DP_PER_S * density
    private var x0 = 0f; private var y0 = 0f; private var t0 = 0L
    fun down(x: Float, y: Float, timeMs: Long) { x0 = x; y0 = y; t0 = timeMs }
    fun move(x: Float, y: Float, timeMs: Long, pressedPointers: Int): SwipeDecision {
        if (pressedPointers > 1) return SwipeDecision.Abort                       // pinch / two-finger
        val dx = x - x0; val dy = y - y0; val elapsed = timeMs - t0
        val horizontal = abs(dx) >= SwipeTuning.RATIO * abs(dy)
        return when {
            elapsed > longPressMs && hypot(dx, dy) < touchSlopPx -> SwipeDecision.Abort   // long-press → selection
            abs(dy) > touchSlopPx && !horizontal -> SwipeDecision.Abort                    // vertical scroll
            abs(dx) >= commitPx && horizontal && elapsed < SwipeTuning.MAX_COMMIT_MS -> SwipeDecision.Commit(dir(dx))
            else -> SwipeDecision.Undecided
        }
    }
    fun up(x: Float, y: Float, vx: Float, vy: Float): SwipeDecision {
        val dx = x - x0
        val flick = abs(vx) >= flickPxPerS && abs(vx) >= SwipeTuning.RATIO * abs(vy) &&
            abs(dx) >= minFlickPx && sign(vx) == sign(dx)
        return if (flick) SwipeDecision.Commit(dir(dx)) else SwipeDecision.Abort
    }
    private fun dir(dx: Float) = if ((dx > 0) != rtl) SwipeDir.TowardEnd else SwipeDir.TowardStart
}
```
§B `EditorSwipeNav.kt` — **sketch (APIs verified: factcheck A5, A9)**. Imports: `androidx.compose.foundation.gestures.awaitEachGesture` / `awaitFirstDown`, `androidx.compose.ui.input.pointer.util.VelocityTracker`.
```kotlin
fun Modifier.editorSwipeNav(
    enabled: () -> Boolean, accepts: (SwipeDir) -> Boolean = { true },
    onArmedDown: () -> Unit, onSwipe: (SwipeDir) -> Unit,
): Modifier = pointerInput(Unit) {
    val rtl = layoutDirection == LayoutDirection.Rtl
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.type != PointerType.Touch || !enabled()) return@awaitEachGesture   // mouse/stylus: never
        onArmedDown()
        val c = SwipeClassifier(viewConfiguration.touchSlop, viewConfiguration.longPressTimeoutMillis, density, rtl)
        c.down(down.position.x, down.position.y, down.uptimeMillis)
        val vt = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            vt.addPosition(ch.uptimeMillis, ch.position)
            if (!ch.pressed) {
                val v = vt.calculateVelocity()
                val d = c.up(ch.position.x, ch.position.y, v.x, v.y)
                if (d is SwipeDecision.Commit && accepts(d.dir)) { ch.consume(); onSwipe(d.dir) } // View gets CANCEL
                return@awaitEachGesture
            }
            when (val d = c.move(ch.position.x, ch.position.y, ch.uptimeMillis, ev.changes.count { it.pressed })) {
                SwipeDecision.Abort -> return@awaitEachGesture
                SwipeDecision.Undecided -> Unit
                is SwipeDecision.Commit -> {
                    if (!accepts(d.dir)) return@awaitEachGesture
                    ev.changes.forEach { it.consume() }               // interop → ACTION_CANCEL to the EditText
                    onSwipe(d.dir)
                    do { val e = awaitPointerEvent(PointerEventPass.Initial); e.changes.forEach { it.consume() } }
                    while (e.changes.any { it.pressed })
                    return@awaitEachGesture
                }
            }
        }
    }
}
```
§C `EditorChrome.kt` — **contract (signatures); body is yours**
```kotlin
@Composable fun EditorChrome(
    visible: Boolean, widthClass: WidthClass, @DrawableRes libraryIcon: Int, onLibrary: () -> Unit,
    overflowExpanded: Boolean, onOverflow: () -> Unit, onOverflowDismiss: () -> Unit, overflow: OverflowActions,
    modifier: Modifier = Modifier, stats: @Composable (chromeVisible: Boolean) -> Unit = {},
)
/** Observes (never consumes) a tap whose down is within [topPx] of the top; Initial pass, slop-bounded, < longPress. */
fun Modifier.observeTopTap(topPx: () -> Float, onTap: () -> Unit): Modifier
```
`topPx` = 56 dp + the status-bar inset. Content descriptions: `R.string.cd_library` "Library", `R.string.cd_more` "More options".

§D `ChromeVisibility.kt` — **sketch**
```kotlin
class ChromeVisibility(private val scope: CoroutineScope, private val idleShowMs: Long = WriterMotion.CHROME_IDLE_SHOW_DELAY_MS) {
    private val _visible = MutableStateFlow(true); val visible: StateFlow<Boolean> = _visible
    private var imeVisible = false; private var upPx = 0f; private var idle: Job? = null
    fun onEdit() { _visible.value = false; upPx = 0f; idle?.cancel()
        if (!imeVisible) idle = scope.launch { delay(idleShowMs); show() } }        // hardware keyboard pause
    fun onImeVisibility(v: Boolean) { val was = imeVisible; imeVisible = v; if (v) idle?.cancel() else if (was) show() }
    fun onScroll(dyPx: Float, thresholdPx: Float) { if (dyPx < 0) { upPx -= dyPx; if (upPx >= thresholdPx) show() } else upPx = 0f }
    fun onTopTap() = show()
    private fun show() { idle?.cancel(); upPx = 0f; _visible.value = true }
}
```
§E `OverflowMenu.kt` — **contract (copy)**. Later tasks only fill fields in `MdWriterRoot`.
```kotlin
data class OverflowToggle(val checked: Boolean, val onChange: (Boolean) -> Unit)
data class OverflowChoice(val options: List<String>, val selected: Int, val onSelect: (Int) -> Unit)
data class OverflowActions(
    val canUndo: Boolean = false, val canRedo: Boolean = false, val onUndo: () -> Unit = {}, val onRedo: () -> Unit = {},
    val onFind: (() -> Unit)? = null,        // T17 (search icon in the icon row)
    val onShare: (() -> Unit)? = null,       // T18 (share icon in the icon row)
    val onNewNote: () -> Unit = {},
    val onPreview: (() -> Unit)? = null,     // T16
    val focus: OverflowChoice? = null,       // T15: Off / Sentence / Paragraph, inline radio sub-rows under "Focus ▸"
    val typewriter: OverflowToggle? = null,  // T15
    val wordCount: OverflowToggle? = null,   // T15
    val onSettings: (() -> Unit)? = null,    // T19
)
@Composable fun OverflowMenu(expanded: Boolean, onDismiss: () -> Unit, actions: OverflowActions)
```
§F Contract addition to 01 §6.2 (engine): `val scrollChanges: SharedFlow<ScrollChange>` with `data class ScrollChange(val y: Int, val dy: Int)`. The flow is `MutableSharedFlow(extraBufferCapacity = 64, onBufferOverflow = DROP_OLDEST)`, emitted from `EditorScrollView.onScrollChanged` (call `super` first). Emit only; never touch padding there (HARD RULE 2).

§G Root layout — **sketch**
```kotlin
val expanded = currentWindowAdaptiveInfoV2().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
var paneVisible by rememberSaveable { mutableStateOf(true) }
val editor = remember { movableContentOf { EditorScreen(/* controller, ui, chrome, overflow, swipe routing */) } }
LaunchedEffect(expanded) { if (expanded) drawerState.snapTo(DrawerValue.Closed) }
if (expanded) Row(Modifier.fillMaxSize()) {
    if (paneVisible) { LibraryPane(libraryVm, openDoc, Modifier.width(320.dp).fillMaxHeight())
        VerticalDivider(thickness = hairline, color = colors.divider) }
    Box(Modifier.weight(1f)) { editor() }
} else ModalNavigationDrawer(/* T12's parameters, unchanged */) { editor() }
PreviewSlot()          // T16 composes PreviewOverlay here (before the back handlers below)
RootBackHandlers(...)  // §H — must stay the LAST composable in MdWriterRoot
```
If the adaptive dependency is missing from `app/build.gradle.kts`, add `androidx.compose.material3.adaptive:adaptive` from the catalog (`grep -n adaptive gradle/libs.versions.toml`). If you have to add a catalog entry, record it as a deviation.

§H Back ordering — **copy the comment block into `MdWriterRoot`**
```kotlin
// Back priority (highest first): IME (system) > selection > find > drawer (search clear > folder up > close)
// > preview (T16) > system back-to-home. activity-compose 1.13: LAST COMPOSED WINS, so compose the lowest first,
// always composed, gated only by `enabled` (an enabled root handler disables the back-to-home animation).
// Drawer, preview and find are mutually exclusive (openLibrary/openPreview close the others), and opening the
// drawer or preview collapses the selection, so only selection+find can be enabled together → selection last.
BackHandler(enabled = ui.findOpen) { editorVm.closeFind() }
BackHandler(enabled = selection.start != selection.end) { controller.collapseSelection() }
// inside T12's drawer content, after its children (composed after ModalDrawerSheet's own handler):
BackHandler(enabled = drawerState.isOpen && !lib.atRoot && !lib.searchActive) { libraryVm.navigateUp() }
BackHandler(enabled = drawerState.isOpen && lib.searchActive) { libraryVm.clearSearch() }
```
§I Shortcuts — **copy**
```kotlin
enum class AppCommand { NewNote, ToggleLibrary } // T16 adds Preview (Ctrl+R), T17 adds Find (Ctrl+F)
object AppShortcuts {
    fun map(keyCode: Int, ctrl: Boolean, shift: Boolean, alt: Boolean): AppCommand? {
        if (!ctrl || alt) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_N -> if (shift) null else AppCommand.NewNote
            KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_L -> if (shift) null else AppCommand.ToggleLibrary
            else -> null
        }
    }
}
// MainActivity: called only when no view consumed the shortcut (focused EditText first, then the activity)
override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean =
    AppShortcuts.map(keyCode, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed)
        ?.let { commands.tryEmit(it) } ?: super.onKeyShortcut(keyCode, event)
```
§J `TouchInjector` (androidTest) — **sketch**. It uses `InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(ev, true)`, with MotionEvents obtained at real `SystemClock.uptimeMillis()` and `Thread.sleep` between steps, so that EditText long-press timers really fire. Helpers:
- `swipe(from, to, durationMs, steps = 12)`
- `longPressThenDrag(at, dx, holdMs = 800)`
- `twoFingerSwipe(from, dx)`: `ACTION_POINTER_DOWN` with `pointerCount = 2` via `MotionEvent.obtain(downTime, t, action, 2, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)`

Coordinates are screen px from `editText.getLocationOnScreen`. Stay within x 20–80 % (clear of the Back-gesture edges).

## Acceptance criteria
1. `SwipeClassifierTest` (density 3, slop 24 px, long-press 400 ms) passes all of these:
   - fast LTR commits TowardEnd, and the same input in RTL commits TowardStart
   - dx = 168 px, dy = 67.2 px, t < 600 → Commit
   - dx 200 at t = 700 → Undecided, then a slow up → Abort
   - vertical drift (dx 20, dy 60) → Abort
   - 30° diagonal (dx 170, dy 100) → Abort
   - still for 450 ms → Abort
   - second pointer → Abort
   - flick vx 4000 px/s with dx 90 → Commit
   - flick with dx 50 → Abort
   - vx opposite to dx → Abort
2. `ChromeVisibilityTest` (runTest, virtual time): an edit hides the chrome.
   - With the IME hidden, it is visible again at 1500 ms and not at 1499 ms.
   - With the IME visible, it stays hidden after 10 s. An IME hide shows it.
   - Scrolling up 23 dp keeps it hidden, and 24 dp shows it. A downward scroll resets the accumulation.
   - A top tap shows it.
3. `AppShortcutsTest`: Ctrl+N → NewNote; Ctrl+O and Ctrl+L → ToggleLibrary; N without Ctrl, Ctrl+Alt+N and Ctrl+Shift+N → null.
4. `OverflowMenuTest`: with only the default fields, it shows Undo, Redo and New note, and does not show "Preview", "Settings", "Find" or "Share". With `canUndo = false`, Undo `assertIsNotEnabled()`. Clicking New note calls `onNewNote` once and calls `onDismiss`.
5. `SwipeNavTest` (device):
   - Unfocused editor with 40+ lines: a start→end swipe (60 % width, 200 ms) opens the drawer ("Library" header displayed).
   - Focused editor with the IME up: the same swipe opens the drawer, the IME is hidden, and the caret after closing equals the caret before the swipe.
   - With `setSelection(5, 15)`: the swipe does NOT open the drawer.
   - Long-press 800 ms then drag 150 dp: the drawer stays closed and `editText.hasSelection()` is true.
   - Vertical drag of 400 dp with 30 dp horizontal drift: the drawer stays closed and `scrollView.scrollY > 0`.
   - Two-finger horizontal swipe: nothing happens.
   - End→start swipe: the drawer stays closed (no preview yet).
   - With `swipeNavigation = false`: case 1 does nothing.
   - Ctrl+L (`sendKeySync`) opens the drawer, with the editor both focused and unfocused.
6. Screenshot `idle.png`: two glyphs visible, top-start and top-end, and nothing else but text. Screenshot `typing.png` (after `adb shell input text hello`): no glyphs.
7. With the drawer open in a subfolder, Back goes up one level. A second Back closes the drawer. With nothing open, Back sends the app home (back-to-home animation, `ON_STOP` flush).
8. Tablet (`wm size 2560x1600`, `wm density 320` → 1280 dp):
   - The screenshot shows a 320 dp pane + hairline + a centred column.
   - The glyph toggles the pane. A start→end swipe with the pane hidden shows it; an end→start swipe with the pane visible hides it.
   - The typed text and the undo history survive the toggle and the compact↔expanded switch (`wm size reset`).
9. `make check` is green.

## Verification commands
```sh
make test && make check
make install-debug DEVICE=emulator-5554
make test-device DEVICE=emulator-5554
adb -s emulator-5554 exec-out screencap -p > /tmp/t13-idle.png
adb -s emulator-5554 shell input text hello && adb -s emulator-5554 exec-out screencap -p > /tmp/t13-typing.png
adb -s emulator-5554 shell cmd overlay enable com.android.internal.systemui.navbar.threebutton   # 3-button check
adb -s emulator-5554 shell cmd overlay enable com.android.internal.systemui.navbar.gestural      # restore
adb -s emulator-5554 shell wm size 2560x1600 && adb -s emulator-5554 shell wm density 320
adb -s emulator-5554 exec-out screencap -p > /tmp/t13-tablet.png
adb -s emulator-5554 shell wm size reset && adb -s emulator-5554 shell wm density reset
```
Test manually under both nav modes: swipes near the screen edges are the system Back gesture, and a swipe from 20 % width opens the library.

## Pitfalls
- Do not enable M3's drawer drag while it is closed, and never use `systemGestureExclusion` (HARD RULE 12; platform §4.1 #3–#5).
- Detect in `PointerEventPass.Initial` and consume **only on commit**. Consuming earlier breaks taps, cursor drags and scrolling. Selection handles live in separate windows and are unaffected (A22).
- `pointerInput(Unit)` captures its lambdas once, so pass `rememberUpdatedState` values. Otherwise the swipe sees a stale `enabled` state.
- Restore the selection snapshot. The EditText's "cursor drag from anywhere" moves the caret during the first 56 dp (platform §4.1 #4).
- Never animate the pane width. Every width change calls `setPadding` → a full reflow per frame (HARD RULE 2, A14).
- Without `movableContentOf`, the compact↔expanded switch re-attaches `controller.scrollView` to a second parent → `IllegalStateException`.
- Back handlers: enable them only while something is open (an enabled root handler kills back-to-home, platform §5). Last composed wins, so keep `RootBackHandlers` last.
- Chrome signals must not touch the EditText (no padding or insets on IME changes; HARD RULE 2).

## Definition of done
- [ ] Every Acceptance criterion is met; `make check` is green; `make test-device DEVICE=emulator-5554` is green.
- [ ] 01 §6.2 updated with `scrollChanges`, and the `SwipeTuning` values that were finally used are listed in STATUS.
- [ ] Screenshots `idle`, `typing` and `tablet` are referenced in the STATUS entry, with the manual 3-button/gesture-nav results.
- [ ] Commit `T13: swipe navigation, fading chrome, overflow menu, back ordering, wide-screen pane`.
