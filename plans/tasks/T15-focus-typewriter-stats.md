# T15 — Focus Mode, typewriter scrolling, stats line

**Goal** Three iA-style writing aids you switch on from the overflow menu. **Focus Mode** (Off / Sentence / Paragraph)
dims everything outside the active sentence or paragraph. **Typewriter scrolling** keeps the caret line at 45 % of the
visible height while you type. The **stats line** (off by default) shows `1,204 words · 5 min` centred in the glyph
row, and a tap cycles what it counts. All three settings persist.

**Depends on** T13, which delivered `EditorChrome.kt` (the glyph row), `OverflowMenu.kt` (icon row plus entries),
`chromeVisible` typing detection, and the back ordering. This task also relies on earlier tasks:
- T05: `MarkdownEditText`, `EditorScrollView`, and the single place that calls `setPadding`.
- T06: `EditorStyle` and the place it is built from `WriterColors`.
- T07: `EditorController.install`, `edits`, `version`, and the `focusMode` / `typewriter` property stubs.
- T08: `onKeyShortcut` and the undo manager.
- T09: the `onSelectionChanged` hook and the scroll hook used by `SelectionUi`.
- T11: `Settings.focusMode`, `typewriter`, `wordCount`, and `SettingsRepository.update { }`.

Before starting, read the STATUS entries of T05, T07, T09, T11 and T13. Where STATUS spells a name differently from
this file, use the STATUS name.

**Read first**
- `plans/01-architecture.md` §4.4 (the EditText is `wrap_content` inside `EditorScrollView`), §6.1 (`TextStats`),
  §6.2 (controller), §6.4 (`EditorUiState.stats`), §7 (stats threading), §10 rules 2, 3 and 5.
- `plans/02-design-spec.md` §2 (the `focusDim` row and the overlay-alpha bullet), §5 (Stats, Focus Mode, Typewriter),
  §9 (overflow menu), §11 (typewriter re-centre: 150 ms, decelerate).
- `plans/research/editor-engine.md` §5.7 and §5.8 (lines 336–358), §6.7 and §6.8 (lines 868–951). These are
  **sketches**: they self-scroll the EditText, which the plan does not do (see the pitfalls).
- `plans/research/markdown.md` §9 (lines 813–870: stats rules and the harness table).
- `plans/research/factcheck.md` rows A15, A21 and C15.

## Scope — In / Out
**In**
- Pure focus-range math, the ICU sentence breaker, and the overlay drawn in `MarkdownEditText.onDraw`.
- Typewriter centring through `EditorScrollView`, and the 45 % / 55 % padding branch.
- The stats pipeline (400 ms debounce, compute on Default, stale results dropped), the stats line UI, and the tap
  cycle.
- Overflow entries: Focus ▸ Off/Sentence/Paragraph, Typewriter scrolling ☐, Word count ☐. Persisting them, and
  applying the settings to the controller.

**Out**
- The Settings sheet rows and runtime style changes belong to **T19**. This task only uses the `Settings` fields.
- Find, and hiding the stats line while find is open, belong to **T17**.
- The Ctrl+D shortcut for focus: not planned. Do not add it.
- Accessibility polish beyond the items listed here belongs to **T20**. Performance verification belongs to **T21**.
- Do not change the non-typewriter padding values T05 chose. Do not touch span classes or the restyler.

## Files to create / modify
(`M` = `app/src/main/kotlin/dev/mdwriter`, `U` = `app/src/test/kotlin/dev/mdwriter`,
`I` = `app/src/androidTest/kotlin/dev/mdwriter`)
- `M/editor/FocusMode.kt` — create or extend. Uses T11's `FocusModeKind` (`editor/FocusModeKind.kt`; do not redeclare it),
  `SentenceBreaker`, `IcuSentenceBreaker`, `FocusRange`, `FocusRanges`, `focusOverlayArgb()` and `FocusOverlay`.
- `M/editor/MarkdownEditText.kt` — modify. Adds the `focusMode` and `typewriter` properties, the overlay in
  `onDraw`, the range refresh in `onSelectionChanged` / `onTextChanged`, the `bringPointIntoView` override, the
  typewriter padding branch, and `onViewportChanged()`.
- `M/editor/EditorScrollView.kt` — modify. Adds `childViewport(out)`, `animateScrollTo(y)`, `cancelTypewriter()`,
  `isUserScrolling`, the `onScrollChanged` hook, and `internal object TypewriterMath`.
- `M/editor/EditorController.kt` — modify. The `focusMode` and `typewriter` setters delegate to the EditText.
  Adds `data class StatsInput` and `fun statsInput(): StatsInput`.
- `M/editor/spans/EditorStyle.kt` (plus the builder that maps `WriterColors` to `EditorStyle`) — add
  `focusOverlayColor: Int`.
- `M/ui/editor/StatsPipeline.kt` — new. `StatsSource`, `DisplayStats`, `StatsPipeline`, `StatsDisplay`, `formatStats()`.
- `M/ui/editor/EditorChrome.kt` — modify. Adds the `StatsLine` composable in the centre slot of the glyph row.
- `M/ui/editor/OverflowMenu.kt` — modify. Adds the Focus sub-page and the two checkbox rows.
- `M/ui/editor/EditorScreen.kt` — modify. Pushes settings into the controller and runs the stats pipeline.
- `M/ui/editor/EditorViewModel.kt`, `EditorUiState.kt` — modify. Adds `setFocusMode`, `setTypewriter`,
  `setWordCount`, `onStats`, and the field `statsSelection: Boolean = false`.
- `app/src/main/res/values/strings.xml` — add menu labels and content descriptions (listed in step 9).
- Tests:
  - `U/editor/FocusRangesTest.kt`
  - `U/editor/FocusOverlayColorTest.kt`
  - `U/editor/TypewriterMathTest.kt`
  - `U/ui/editor/StatsPipelineTest.kt`
  - `U/ui/editor/StatsFormatTest.kt`
  - `U/ui/editor/StatsLineTest.kt` (Robolectric + Compose)
  - `U/ui/editor/OverflowFocusMenuTest.kt` (Robolectric + Compose)
  - `I/editor/FocusOverlayDeviceTest.kt`
  - `I/editor/TypewriterDeviceTest.kt`

## Steps
1. **Survey.** Find the existing code before you change anything:
   - `grep -rn "setPadding\|FocusModeKind\|typewriter\|onSelectionChanged\|onScrollChanged\|bringPointIntoView" app/src/main/kotlin/dev/mdwriter/editor`
   - `grep -rn "EditorStyle(" app/src/main`
   - `grep -n "focusMode\|typewriter\|wordCount" app/src/main/kotlin/dev/mdwriter/data/settings/*.kt`

   Note the answers in STATUS.
2. **Pure focus math** (`FocusMode.kt`, Reference §A). Write `FocusRangesTest` first. It runs on the JVM and uses a
   test-only `JavaSentenceBreaker` built on `java.text.BreakIterator.getSentenceInstance(Locale.US)`. The cases:

   | Mode | Text | Selection | Expected range |
   |---|---|---|---|
   | Sentence | `"One. Two. Three."` | caret 6 | [5,10) |
   | Sentence | same | caret 0 | [0,5) |
   | Sentence | same | caret 4 | [0,5) |
   | Sentence | same | caret 5 | [5,10) |
   | Sentence | same | caret 16 | [10,16) |
   | Sentence | same | select 2..7 | [0,10) |
   | Sentence | same | select 2..10 | [0,10) |
   | Sentence | `"A one. A two.\nB one. B two."` | select 2..17 | [0,21) |
   | Paragraph | `"a\nbb\nccc"` | caret 3 | [2,4) |
   | Paragraph | `"a\n\nb"` | caret 2 | [2,2) (empty) |
   | Paragraph | 25,000 × `x`, no newline | caret 12,500 | [2,500, 22,500) |
   | Paragraph | multi-line | selection | first line start to last line end |
   | Off | any | any | `FocusRange.NONE` |
3. **Overlay colour.** Add `focusOverlayArgb(bg, text, dim)` (Reference §A). Add `EditorStyle.focusOverlayColor`
   and fill it in the builder from `WriterColors` (`bg`, `text`, `focusDim` via `toArgb()`).
   `FocusOverlayColorTest` asserts:

   | Palette | Expected ARGB |
   |---|---|
   | light | `0xC0F7F7F7` |
   | dark | `0xA01A1A1A` |
   | black | `0xA1000000` |

   It also asserts that `bg·α + text·(1−α)` is within ±2 of `focusDim` on each channel.
4. **Overlay drawing** (`MarkdownEditText`, Reference §B):
   - Hold `var focusMode: FocusModeKind` (setter: refresh the range, then `invalidate()`), a `FocusOverlay`, and
     `IcuSentenceBreaker()` (created once, reused).
   - In `onSelectionChanged`, refresh the range and `invalidate()` only if it changed. Keep T09's existing call.
   - In `onTextChanged(...)`, set `focusStale = true` and do nothing else. Stale ranges are recomputed lazily at
     the top of the overlay step in `onDraw`.
   - `onDraw`: call `super.onDraw(canvas)` first, then draw the overlay. The band to dim is the scroll viewport
     **± one viewport height**, in EditText coordinates, from `(parent as EditorScrollView).childViewport(tmpRect)`.
   - `onViewportChanged()` (called from `EditorScrollView.onScrollChanged`): `invalidate()` only when the viewport
     has left the band drawn last time. This keeps "invalidate on selection change only" true while scrolling.
5. **Scroll view additions** (`EditorScrollView`, Reference §C):
   - `childViewport(out: Rect)`: `top = scrollY − child.top`, `bottom = top + height − paddingTop − paddingBottom`.
     If T09 already has an equivalent, reuse it.
   - `isUserScrolling`: true while dragging (after touch slop) and while a fling settles. `fling(velocityY)` sets
     `settling`. `onScrollChanged` re-posts a 100 ms quiet check that clears `settling`.
   - `dispatchTouchEvent(ACTION_DOWN)` calls `cancelTypewriter()`.
   - `animateScrollTo(y)`: a 150 ms `ValueAnimator` with `DecelerateInterpolator` that calls `scrollTo(0, v)`.
     If `!ValueAnimator.areAnimatorsEnabled()`, it jumps straight to `y`.
   - `TypewriterMath` is pure and has a JVM test.
6. **Typewriter** (`MarkdownEditText`, Reference §B):
   - `var typewriter: Boolean` setter: only if the value changed, re-run T05's padding computation. That code path
     calls `setPadding` once (allowed by hard rule 2: this is a settings change).
   - The typewriter branch sets `top = round(0.45 × winH)` and `bottom = round(0.55 × winH)`, where
     `winH = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height()`.
   - After the layout pass (`doOnNextLayout`): when turned on, call `bringPointIntoView(selectionEnd, true)`. When
     turned off, `scrollView.scrollTo(0, (oldScrollY − Δtop).coerceAtLeast(0))` so the text does not jump.
   - Override `bringPointIntoView(offset, requestRectWithoutFocus)` exactly as in Reference §B.
7. **Stats.**
   - Add `StatsInput(text, spans, selStart, selEnd, version)` and `statsInput()` to the controller. It runs on the
     main thread: `snapshot()` plus `highlighter.spans().toList()`.
   - Write `StatsPipeline` (Reference §D) and `formatStats`, and test both.
   - In `EditorScreen`, run `LaunchedEffect(controller, wordCountOn) { if (!wordCountOn) vm.onStats(null) else
     StatsPipeline(source).flow().collect(vm::onStats) }`, where `source` adapts the controller (Reference §D).
   - `vm.onStats(d)` sets `uiState.stats = d?.stats` and `statsSelection = d?.isSelection ?: false`.
8. **Stats line UI** (`EditorChrome.kt`):
   - `StatsLine(stats, isSelection, display, typing, onCycle)` sits in the centre slot of T13's glyph row. It is
     vertically centred on the glyph buttons and hidden when `stats == null`.
   - Text: 12 sp (T02's 12 sp `WriterTypography` role; otherwise `fontSize = 12.sp`), colour `textSecondary`,
     `maxLines = 1`.
   - `Modifier.clickable(role = Role.Button, onClickLabel = "Change statistic")` with a 48 dp minimum height.
   - `display` lives in `rememberSaveable { mutableStateOf(StatsDisplay.Words) }`.
   - Alpha: `animateFloatAsState(if (typing) 0.6f else 1f, tween(150))`, where `typing = !uiState.chromeVisible`.
     The stats line never fades fully (02 §5).
9. **Overflow menu** (`OverflowMenu.kt`, Reference §E):
   - A "Focus" row (`ic_center_focus_strong`, trailing `ic_chevron_right`) switches the same `DropdownMenu` to a
     sub-page: a back row plus three radio rows (`ic_check` on the selected one). Picking one calls
     `vm.setFocusMode(kind)` and closes the menu.
   - "Typewriter scrolling" and "Word count" rows show a trailing `Checkbox` and toggle
     `vm.setTypewriter(!on)` / `vm.setWordCount(!on)`. The menu stays open.
   - The VM persists through `settings.update { it.copy(...) }`.
   - Deliver these through T13's existing `OverflowActions.focus: OverflowChoice?`, `typewriter`/`wordCount: OverflowToggle?`
     fields (set them in MdWriterRoot/EditorScreen); change `OverflowMenu.kt` only for the sub-page presentation.
   - `EditorScreen` pushes settings into the controller with `LaunchedEffect(s.focusMode) {
     controller.focusMode = s.focusMode.toKind() }` and the same pattern for `typewriter`. If T11 stored the value
     as a String or its own enum, map it in one `toKind()` function in `EditorScreen.kt`.
   - Strings: `focus_mode`, `focus_off`, `focus_sentence`, `focus_paragraph`, `typewriter_scrolling`, `word_count`,
     `change_statistic`.
10. **Device tests** (`I/`). Reuse the harness the T05–T08 instrumented tests use (`grep -rln
    "createAndroidComposeRule\|EditorStyle(" app/src/androidTest`). Import `createAndroidComposeRule` from
    `androidx.compose.ui.test.junit4.v2` if that is where it lives.
    - `FocusOverlayDeviceTest`: on the main thread, `measure`/`layout` `controller.scrollView` at 1080 × 1600 px.
      Install `"One. Two. Three."`, select caret 6, set `focusMode = Sentence`, and draw the EditText into an
      ARGB_8888 bitmap. Take the glyph boxes from `layout.getPrimaryHorizontal` plus paddings.
      - Light palette: the darkest pixel in "One" is in `0xB4..0xCC` (grey), the darkest pixel in "Two" is
        `≤ 0x40`, and the darkest pixel in "Three" is in `0xB4..0xCC`.
      - Dark palette: the brightest pixel in "One" is in `0x52..0x6A`, and the brightest pixel in "Two" is
        `≥ 0xB8`.
      - Paragraph mode on `"a\nbb"` with the caret in line 2: line 1 is dimmed and line 2 is not.
    - `TypewriterDeviceTest`: host `controller.scrollView` in the compose rule and install 60 short lines with the
      caret at the end. Set `typewriter = true` and focus the EditText. Then, 30 times:
      `onCreateInputConnection(EditorInfo()).commitText("Line $i\n", 1)` and `waitForIdle()` + 200 ms.
      After each commit, assert that `centre = child.top + totalPaddingTop + lineCentre(caretLine) −
      scrollView.scrollY` satisfies `|centre − 0.45·viewportH| ≤ linePitch`.
11. **Manual emulator checks** (see Verification): screenshots of Sentence and Paragraph focus in light and dark;
    the adb typing script with typewriter on; the stats line on the known sample.
12. **Finish.** Run `make format`, then `make check`. Add the STATUS entry. Update `01-architecture.md`:
    - §6.2: `StatsInput` and `statsInput()`.
    - §6.4: `statsSelection`.
    - §3: `ui/editor/StatsPipeline.kt`.
    - Under "Deviations": 02 §2 says the black overlay alpha is ≈0.61, but the formula gives 0.63.

## Reference code
**§A FocusMode.kt — copy the logic verbatim; the file layout is up to you.**
```kotlin
// enum class FocusModeKind { Off, Sentence, Paragraph }  — already in editor/FocusModeKind.kt (T11); do not redeclare
/** Ascending boundaries of [paragraph], always including 0 and paragraph.length. */
fun interface SentenceBreaker { fun boundaries(paragraph: String): IntArray }
class IcuSentenceBreaker : SentenceBreaker {                   // android.icu — app only, never in :core:markdown
    private val bi = android.icu.text.BreakIterator.getSentenceInstance(android.icu.util.ULocale.getDefault())
    override fun boundaries(paragraph: String): IntArray {
        bi.setText(paragraph); val out = ArrayList<Int>(); var b = bi.first()
        while (b != android.icu.text.BreakIterator.DONE) { out += b; b = bi.next() }
        return out.toIntArray()
    }
}
data class FocusRange(val start: Int, val end: Int) { companion object { val NONE = FocusRange(-1, -1) } }
object FocusRanges {
    const val MAX_SCAN = 10_000
    fun paragraphStart(t: CharSequence, off: Int): Int { var i = off; val lim = maxOf(0, off - MAX_SCAN); while (i > lim && t[i - 1] != '\n') i--; return i }
    fun paragraphEnd(t: CharSequence, off: Int): Int { var i = off; val lim = minOf(t.length, off + MAX_SCAN); while (i < lim && t[i] != '\n') i++; return i }
    fun compute(t: CharSequence, selStart: Int, selEnd: Int, mode: FocusModeKind, br: SentenceBreaker): FocusRange {
        if (mode == FocusModeKind.Off || selStart < 0) return FocusRange.NONE
        val lo = minOf(selStart, selEnd).coerceIn(0, t.length); val hi = maxOf(selStart, selEnd).coerceIn(0, t.length)
        val p1s = paragraphStart(t, lo); val p2e = paragraphEnd(t, hi)
        if (mode == FocusModeKind.Paragraph) return FocusRange(p1s, p2e)
        val p1e = paragraphEnd(t, lo); val p2s = paragraphStart(t, hi)
        val a = p1s + sentenceStart(br.boundaries(t.substring(p1s, p1e)), lo - p1s, p1e - p1s)
        val b = p2s + sentenceEnd(br.boundaries(t.substring(p2s, p2e)), hi - p2s, p2e - p2s, hi > lo)
        return FocusRange(a, maxOf(a, b))
    }
    /** Largest boundary <= pos; at the paragraph end the start of the LAST sentence (keeps it lit after ". "). */
    internal fun sentenceStart(b: IntArray, pos: Int, len: Int): Int {
        if (len == 0) return 0
        val p = if (pos >= len) len - 1 else pos
        var r = 0; for (x in b) { if (x <= p) r = x else break }; return r
    }
    /** A non-empty selection ending exactly on a boundary ends there; otherwise the next boundary > pos, else len. */
    internal fun sentenceEnd(b: IntArray, pos: Int, len: Int, nonEmpty: Boolean): Int {
        if (nonEmpty && b.contains(pos)) return pos
        for (x in b) if (x > pos) return x
        return len
    }
}
/** 02 §2: alpha = (dim − text) / (bg − text) on the red channel; colour = bg with that alpha. */
fun focusOverlayArgb(bg: Int, text: Int, dim: Int): Int {
    val r = { c: Int -> (c shr 16) and 0xFF }
    val a = ((r(dim) - r(text)).toFloat() / (r(bg) - r(text))).coerceIn(0f, 1f)
    return ((a * 255f + 0.5f).toInt() shl 24) or (bg and 0x00FFFFFF)
}
```

**§B MarkdownEditText additions — sketch; merge into T05's class.**
```kotlin
private val overlay = FocusOverlay()          // holds Path, Paint(color = style.focusOverlayColor), drawnTop/Bottom
private val breaker = IcuSentenceBreaker(); private var focus = FocusRange.NONE; private var focusStale = false
private val vp = Rect()
private fun refreshFocus(): Boolean {
    val n = FocusRanges.compute(text, selectionStart, selectionEnd, focusMode, breaker)
    focusStale = false; if (n == focus) return false; focus = n; return true
}
override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (focusMode == FocusModeKind.Off) return
    val l = layout ?: return; val sv = parent as? EditorScrollView ?: return
    if (focusStale) refreshFocus()
    sv.childViewport(vp); val h = vp.height()
    val top = (vp.top - h).coerceAtLeast(0); val bottom = (vp.bottom + h).coerceAtMost(height)
    overlay.draw(canvas, this, l, focus, caretOffset = if (selectionStart == selectionEnd) selectionStart else -1, top, bottom)
}
fun onViewportChanged() {                    // called by EditorScrollView.onScrollChanged
    if (focusMode == FocusModeKind.Off) return
    (parent as EditorScrollView).childViewport(vp)
    if (vp.top < overlay.drawnTop || vp.bottom > overlay.drawnBottom) invalidate()
}
override fun bringPointIntoView(offset: Int, requestRectWithoutFocus: Boolean): Boolean {
    val l = layout; val sv = parent as? EditorScrollView
    if (!typewriter || l == null || sv == null || isLayoutRequested || !(isFocused || requestRectWithoutFocus)) {
        return super.bringPointIntoView(offset, requestRectWithoutFocus)
    }
    if (sv.isUserScrolling) return false                               // never fight a drag/fling
    val line = l.getLineForOffset(offset.coerceIn(0, length()))
    val center = totalPaddingTop + (l.getLineTop(line) + l.getLineBottom(line, false)) / 2
    val target = TypewriterMath.targetScrollY(top, center, sv.viewportHeight(), sv.maxScrollY())
    if (target == sv.scrollY) return false
    sv.animateScrollTo(target); return true                            // do NOT call super (it would scroll too)
}
```
`FocusOverlay.draw`, sketch:
1. Reset the path.
2. If `end > start`, call `layout.getSelectionPath(start, end, path)`.
3. If `caretOffset >= 0`, also add the caret rect: `x = getPrimaryHorizontal(caretOffset)`, `x−2dp .. x+2dp`
   over that line's top and bottom. This keeps the 2 dp caret undimmed.
4. `canvas.withSave { translate(totalPaddingLeft, totalPaddingTop); clipOutPath(path); drawRect(-totalPaddingLeft,
   top − totalPaddingTop, width − totalPaddingLeft, bottom − totalPaddingTop, paint) }`.
5. Store `drawnTop = top` and `drawnBottom = bottom`.

The path is rebuilt on every draw. That is fine: the range is a paragraph of at most 20k chars, and the rebuild
costs microseconds.

**§C EditorScrollView — copy the math verbatim; the rest is a sketch.**
```kotlin
internal object TypewriterMath {
    const val FRACTION = 0.45f; const val ANIM_MS = 150L            // = WriterMotion.TYPEWRITER_RECENTER_MS (no Compose import in editor/)
    fun targetScrollY(childTop: Int, lineCenterInChild: Int, viewportH: Int, maxScrollY: Int): Int =
        (childTop + lineCenterInChild - FRACTION * viewportH).roundToInt().coerceIn(0, maxOf(0, maxScrollY))
    fun paddings(windowH: Int): Pair<Int, Int> = (windowH * 0.45f).roundToInt() to (windowH * 0.55f).roundToInt()
}
fun viewportHeight() = height - paddingTop - paddingBottom
fun maxScrollY() = maxOf(0, (getChildAt(0)?.height ?: 0) - viewportHeight())
override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {   // EXTEND T13's override (it emits scrollChanges); one override only
    super.onScrollChanged(l, t, oldl, oldt)
    (getChildAt(0) as? MarkdownEditText)?.onViewportChanged()
    if (settling) { removeCallbacks(settleCheck); postDelayed(settleCheck, 100) }
}
```
`TypewriterMathTest` asserts:
- `targetScrollY(0, 1000, 800, 5000) == 640`
- a negative result clamps to 0
- a result above `maxScrollY` clamps to `maxScrollY`
- `paddings(2000) == 900 to 1100`

**§D StatsPipeline.kt — sketch; the behaviour is fixed by the tests.**
```kotlin
interface StatsSource { val triggers: Flow<Unit>; val version: Long; val selection: Pair<Int, Int>; fun snapshot(): StatsInput }
data class DisplayStats(val stats: Stats, val isSelection: Boolean)
enum class StatsDisplay { Words, Characters, Sentences, ReadingTime; fun next() = entries[(ordinal + 1) % entries.size] }
class StatsPipeline(private val src: StatsSource, private val compute: CoroutineDispatcher = Dispatchers.Default,
                    private val debounceMs: Long = 400L) {
    private var cacheVersion = -1L; private var cacheDoc: Stats? = null
    fun flow(): Flow<DisplayStats> = channelFlow {
        src.triggers.onStart { emit(Unit) }.collectLatest {                 // collectLatest = debounce + cancel stale
            delay(debounceMs)
            val (s, e) = src.selection
            if (s == e && src.version == cacheVersion) { cacheDoc?.let { send(DisplayStats(it, false)) }; return@collectLatest }
            val input = src.snapshot()                                     // main thread in production
            val stats = withContext(compute) {
                if (s == e) TextStats.compute(input.text, 0, input.text.length, input.spans)
                else TextStats.compute(input.text, minOf(s, e), maxOf(s, e), input.spans)
            }
            if (input.version != src.version) return@collectLatest         // stale: a newer edit re-triggers
            if (s == e) { cacheVersion = input.version; cacheDoc = stats }
            send(DisplayStats(stats, s != e))
        }
    }
}
```
- Adapter in `EditorScreen`: `triggers = merge(controller.edits.map { }, controller.selection.map { it.start to
  it.end }.distinctUntilChanged().map { })`, `version = controller.version`, and `selection` taken from
  `controller.selection.value`.
- `StatsPipelineTest` (`runTest`, a fake source with `MutableSharedFlow`, `StandardTestDispatcher` as `compute`):
  - nothing is emitted before 400 ms;
  - three triggers 100 ms apart produce exactly one emission;
  - a version bump during compute drops that result;
  - moving a collapsed caret with the same version does not call `snapshot()` (count the calls);
  - a selection gives `isSelection = true` with selection-only numbers.
- `formatStats(stats, display, isSelection, locale)` (words mode uses `stats.readingMinutesRounded()`):

  | Display | Document | Selection |
  |---|---|---|
  | Words | `"1,204 words · 6 min"` (`"0 words"` when there are no words; the "5 min" in 02 §5 is only an illustration) | `"Selected: 42 words"` |
  | Characters | `"7,113 characters"` (`chars`) | `"Selected: 7,113 characters"` |
  | Sentences | `"86 sentences"` | `"Selected: 86 sentences"` |
  | ReadingTime | `"5 min read"` | `"Selected: 5 min read"` |

  Singular forms: `"1 word"`, `"1 character"`, `"1 sentence"`.
- `StatsFormatTest` (`Locale.US`):
  - `TextStats` on `don't stop e-mail 3.14 1,000 snake_case` gives `"6 words · 1 min"`, then `"39 characters"`,
    then `"1 sentence"`;
  - `Two sentences. Here! Right?` gives `"3 sentences"`;
  - `Stats(1204, …)` gives `"1,204 words · 6 min"` (1204/238 = 5.06, rounded up);
  - the selection prefix.

**§E Overflow sub-page — sketch.**
```kotlin
var page by remember(expanded) { mutableStateOf(MenuPage.Main) }
DropdownMenu(expanded, onDismiss, shape = RoundedCornerShape(12.dp), containerColor = colors.surface,
             border = BorderStroke(hairline(), colors.divider), tonalElevation = 0.dp, shadowElevation = 0.dp) {
    when (page) {
        MenuPage.Main -> { /* T13 rows … */ FocusRow(onClick = { page = MenuPage.Focus }); CheckRow(R.string.typewriter_scrolling, typewriter, onToggle); CheckRow(R.string.word_count, wordCount, onToggle) }
        MenuPage.Focus -> { BackRow(R.string.focus_mode) { page = MenuPage.Main }
            FocusModeKind.entries.forEach { k -> RadioRow(label(k), selected = k == focusMode) { onFocusMode(k); onDismiss() } } }
    }
}
```
- Match T13's actual `DropdownMenu` parameters.
- Radio rows use `Modifier.selectable(selected, role = Role.RadioButton)`. Checkbox rows use
  `Modifier.toggleable(value, role = Role.Checkbox)`.
- `OverflowFocusMenuTest`:
  - clicking "Focus" and then "Sentence" calls `onFocusMode(Sentence)` once and dismisses;
  - clicking "Word count" calls the toggle and does not dismiss;
  - the checked state is exposed through semantics (`assertIsOn` / `assertIsOff`).

## Acceptance criteria
1. `make test` passes, including all 7 JVM/Robolectric test classes above with the exact cases listed.
2. `make test-device DEVICE=emulator-5554` passes `FocusOverlayDeviceTest` (light and dark pixel bounds, paragraph
   case) and `TypewriterDeviceTest`: every one of the 30 commits keeps the caret-line centre within ±1 line pitch
   of 45 % of the viewport.
3. Emulator screenshots `t15-sentence-light.png`, `t15-paragraph-light.png`, `t15-sentence-dark.png` and
   `t15-paragraph-dark.png`, with the welcome note in focus mode:
   - text outside the focus is visibly lighter (light) or darker (dark);
   - the focused sentence or paragraph is at full text colour;
   - the caret is undimmed;
   - after scrolling one full screen with a fling there are no undimmed "holes".
4. Typewriter scripted test (Verification): after 30 `adb input` lines at the end of a document, the caret line in
   the final screenshot sits between 40 % and 50 % of the editor viewport height. When the IME opens or closes,
   the caret re-centres and the EditText padding is unchanged.
5. `grep -rn "setPadding" app/src/main/kotlin/dev/mdwriter/editor` shows no new call site. The only call is T05's
   padding function, and it is reached only on width change, style change or typewriter toggle.
6. With Word count on and the sample `Two sentences. Here! Right?` as the whole note:
   - the stats line shows `4 words · 1 min`;
   - taps cycle to `27 characters`, then `3 sentences`, then `1 min read`, then back to words;
   - selecting `Two sentences.` shows `Selected: 2 words`;
   - while typing, the line stays visible at about 60 % alpha.
7. Overflow → Focus → Paragraph, Typewriter ☑ and Word count ☑ all survive a `make run-debug DEVICE=…` restart
   (read back from DataStore).
8. `make check` is green (ktlint, lint, JVM tests).

## Verification commands
```sh
make test && make check
make test-device DEVICE=emulator-5554
make install-debug DEVICE=emulator-5554 && make run-debug DEVICE=emulator-5554
adb -s emulator-5554 shell cmd uimode night no;  adb -s emulator-5554 exec-out screencap -p > /tmp/t15-sentence-light.png
adb -s emulator-5554 shell cmd uimode night yes; adb -s emulator-5554 exec-out screencap -p > /tmp/t15-sentence-dark.png
# typewriter: open a long note, tap at the very end, enable Typewriter scrolling, then:
for i in $(seq 1 30); do adb -s emulator-5554 shell input text "Line%s$i"; adb -s emulator-5554 shell input keyevent 66; done
adb -s emulator-5554 exec-out screencap -p > /tmp/t15-typewriter.png
make logcat DEVICE=emulator-5554   # no StrictMode violations from BreakIterator / stats
```

## Pitfalls
- **Not self-scrolling.** The research sketches use `scrollY` / `height` of the EditText and `scrollTo` on it.
  Here the EditText's `scrollY` is always 0 and it is as tall as the document (01 §4.4, factcheck A15). All
  viewport math goes through `EditorScrollView` (`childViewport`, `viewportHeight`, `maxScrollY`).
- Hard rule 2: never change padding on IME show/hide or scroll. Typewriter padding changes only on the toggle.
  Insets stay on the Compose container.
- Hard rule 3: the overlay and the scroll animation are not text edits. Never call `beginBatchEdit` here.
- Hard rule 5: Focus Mode uses **no spans**. It is only a draw-time overlay.
- Do not call `super.bringPointIntoView` when typewriter is on. Super would `requestRectangleOnScreen`, and the
  ScrollView would fight the animator.
- The one-argument `bringPointIntoView(int)` delegates to the two-argument one. Override only the two-argument form.
- `android.icu` must never appear in `:core:markdown` (01 §2). JVM tests use the `java.text` breaker.
- A ScrollView scroll does not redraw the EditText (its RenderNode is reused). That is why the overlay band is the
  viewport ± one screen, and `onViewportChanged()` invalidates only when the band is left.
- `highlighter.spans()` may return live internal state. Copy it (`toList()`) on the main thread before handing it
  to Default. The highlighter is main-thread only (01 §6.1).
- Stats compute must never run on main. The pipeline is cancelled when Word count is turned off, and the result is
  `vm.onStats(null)`.
- Pure-black overlay alpha is 0.63 by the formula (02 says ≈0.61). The formula wins. Record this in STATUS.

## Definition of done
- [ ] All files above exist or are modified; no hard rule is broken; no new `setPadding` call sites.
- [ ] `make check` is green, and `make test-device DEVICE=emulator-5554` passes the two new device tests.
- [ ] Screenshots from acceptance items 3 and 4 were taken and described in STATUS.
- [ ] `01-architecture.md` is updated (§3, §6.2 `statsInput`, §6.4 `statsSelection`, deviation note).
- [ ] The STATUS.md entry is appended, including the survey results from step 1.
- [ ] One commit: `T15: focus mode overlay, typewriter scrolling, stats line`.
