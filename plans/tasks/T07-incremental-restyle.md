# T07 — Incremental restyle: Restyler, DirtyRange, perf harness, layout-equality test

**Goal** Styling follows typing live: type `# ` then `Title` and the line grows in the very frame the `T` appears;
`**b**` turns bold as the closing `*` lands; opening a fence bands the following lines. Per-keystroke main-thread work
stays within the 01 §9 budgets on 100k/300k documents, and incremental layout provably equals a full reflow.
Milestone: after this task the user can `make install-debug` and write Markdown in a live-styled editor.

**Depends on** T06 (`MdStyleSpan`, `SpanKind`, `SpanSpec`, `SpanFactory.specsForLines`, `SpanMaterializer`,
`PaintTextMeasurer`, `buildStyledDocument`, `MdEditable`, `MarkdownEditText.reflowAll()`, `EditorController.highlighter`,
`syncHangRoom`), T05 (`EditorScrollView.visibleLineRange()`, `SampleDocs`, `FrameWorkLogger`), T03 (`MarkdownHighlighter.update`).

**Read first**
- `plans/01-architecture.md` §4.2/§4.3, §6.1 (`update` overloads, `HighlightDelta`), §6.2 (`edits`, `version`), §7, §9, §10 rules 3, 5, 7.
- `plans/research/editor-engine.md` §5.4 (algorithm — its `applyEdit/rescan` API is WRONG, see C2), §6.3 (Restyler sketch),
  §6.12 (equality test), §2.3 (how equality was checked in the bench).
- `plans/research/factcheck.md` A13, A17, A18, A20; C2.
- `plans/reference/bench/MainActivity.kt` (`fmListener`, `recordEdit`, `verifyLayout`, `report`, `scheduleEdits`).

## Scope — In / Out
In: `DirtyRange` (pure) + tests, `Restyler`, controller wiring (`edits`, `version`, `markAllDirty` on style/geometry/
highlight-setting change), debug perf activity, two instrumented tests.

Out: undo stack, smart Enter/Backspace/Tab, task toggle taps, key shortcuts → **T08**. Selection flow/pill → **T09**.
Autosave consuming `edits` → **T11**. Focus overlay refresh → **T15** (may use `onRestyled`). Viewport pinning for
above-viewport height changes during cascades: not done (accepted, editor-engine §5.4.2.3). Release perf sign-off → **T21**.

## Files to create / modify
- `app/src/main/kotlin/dev/mdwriter/editor/DirtyRange.kt` — pure offset-interval accumulator.
- `app/src/main/kotlin/dev/mdwriter/editor/Restyler.kt` — TextWatcher + Choreographer reconcile.
- `app/src/main/kotlin/dev/mdwriter/editor/EditorController.kt` (modify) — `EditEvent`, `edits`, `version`, wiring.
- `app/src/test/kotlin/dev/mdwriter/editor/DirtyRangeTest.kt` — JVM.
- `app/src/debug/AndroidManifest.xml` — declares `dev.mdwriter.debug.EditorPerfActivity` (`exported="true"`, no filter).
- `app/src/debug/kotlin/dev/mdwriter/debug/EditorPerfActivity.kt` — perf harness (debug build only).
- `app/src/androidTest/kotlin/dev/mdwriter/editor/IncrementalLayoutEqualsFullReflowTest.kt` — instrumented.
- `app/src/androidTest/kotlin/dev/mdwriter/editor/RestyleCorrectnessTest.kt` — instrumented.

## Steps
1. `DirtyRange` exactly per the spec in Reference §A; `DirtyRangeTest` (Acceptance 1). `make test`.
2. `Restyler` (§B). Register with `editText.addTextChangedListener(restyler)` in the `EditorController` constructor.
3. Controller (§C): `install` suspends the restyler around `setText`, hands it the highlighter, resets it; `setStyle`,
   the geometry hook and a `highlightSyntax` change call `restyler.markAllDirty()` (after `reflowAll()`).
4. `EditorPerfActivity` (§D). Install debug, run the harness for 100k and 300k (Verification). Record numbers.
5. Instrumented tests (§E). `make test-device DEVICE=emulator-5554`.
6. Emulator screenshot sequence for `# ` → `Title` (Acceptance 6). `make check`. STATUS. Commit.

## Reference code
**A. DirtyRange (spec + sketch; pure Kotlin, no android imports)** — one half-open interval `[start, end)` in
CURRENT-text offsets, or `full`, or empty. Several edits may arrive before the frame runs; each shifts the interval.
```kotlin
class DirtyRange {
    var start = 0; private set
    var end = 0; private set                      // exclusive
    var full = false; private set
    var isEmpty = true; private set
    fun clear() { isEmpty = true; full = false; start = 0; end = 0 }
    fun markAll() { full = true; isEmpty = false }
    /** Union with [s, e) (new-text offsets; e may equal s). */
    fun add(s: Int, e: Int) { if (full) return
        if (isEmpty) { start = s; end = maxOf(s, e); isEmpty = false } else { start = minOf(start, s); end = maxOf(end, e) } }
    /** Text [at, at+removed) was replaced by [added] chars; newLength = length after the edit. Call BEFORE add(delta). */
    fun onEdit(at: Int, removed: Int, added: Int, newLength: Int) {
        if (isEmpty || full) return
        val d = added - removed; val oldEditEnd = at + removed
        val ns = if (oldEditEnd <= start) start + d else minOf(start, at)       // edit before / overlapping start
        val ne = when { end <= at -> end                                         // edit after the range (or touching end)
                        end >= oldEditEnd -> end + d                              // range end after the edit
                        else -> at + added }                                      // range end inside the removed text
        start = ns.coerceIn(0, newLength); end = ne.coerceIn(start, newLength)
    }
    /** Consume [start, newStart) after reconciling it. Empties the range when newStart >= end. */
    fun trimStart(newStart: Int) { if (full) return; if (newStart >= end) clear() else start = maxOf(start, newStart) }
}
```
Note an edit exactly at `start` with `removed == 0` shifts the range right (`oldEditEnd <= start`); that is correct
because the edit's own highlighter delta is `add`ed right after.

**B. Restyler (adapt editor-engine §6.3 to the REAL API, C2)**
```kotlin
internal class Restyler(private val editText: MarkdownEditText, private val scrollView: EditorScrollView,
                        style: EditorStyle, private val onTextChange: () -> Unit) : TextWatcher, Choreographer.FrameCallback {
    var suspended = true                           // true until the first install finishes, and during every install
    var highlighter: MarkdownHighlighter? = null
    var onRestyled: (() -> Unit)? = null           // after a frame that changed spans (T15 focus overlay)
    private val dirty = DirtyRange(); private var scheduled = false; private var visibleDone = false
    private var hlLength = 0; private var depth = 0
    private val factory = SpanFactory(PaintTextMeasurer(style)); private val mat = SpanMaterializer(style)
    private val specs = ArrayList<SpanSpec>(); private val want = HashMap<Key, SpanSpec>()
    private data class Key(val kind: Int, val arg: Int, val start: Int, val end: Int)
    fun reset(length: Int) { hlLength = length; dirty.clear(); visibleDone = false }
    fun markAllDirty() { dirty.markAll(); visibleDone = false; schedule() }
    val isIdle: Boolean get() = dirty.isEmpty && !scheduled

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) { depth++ }
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        if (suspended) return
        val hl = highlighter ?: return
        val consistent = depth == 1 && hlLength - before + count == s.length
        val delta = if (consistent) hl.update(s, start, before, count) else hl.update(s)   // diff = defensive only
        hlLength = s.length
        dirty.onEdit(start, before, count, s.length)
        if (delta.full) dirty.markAll() else dirty.add(delta.startOffset, delta.endOffset)
        visibleDone = false; onTextChange(); schedule()          // NO span changes here
    }
    override fun afterTextChanged(s: Editable) { depth-- }
    private fun schedule() { if (!scheduled) { scheduled = true; Choreographer.getInstance().postFrameCallback(this) } }

    override fun doFrame(frameTimeNanos: Long) {                 // CALLBACK_ANIMATION: before layout+draw of THIS frame
        scheduled = false
        val hl = highlighter ?: return; val e = editText.text ?: return
        if (dirty.isEmpty) return
        val deadline = System.nanoTime() + BUDGET_NS
        var from = if (dirty.full) 0 else hl.lineIndexOf(dirty.start)
        val endLine = if (dirty.full) hl.lineCount else hl.lineIndexOf(dirty.end) + 1
        if (!visibleDone && endLine - from > CHUNK_LINES) {       // cascades: visible lines (±20) first
            val vis = visibleLogicalLines(hl)
            val a = maxOf(from, vis.first - 20); val b = minOf(endLine, vis.last + 21)
            var l = a; while (l < b) { val c = minOf(b, l + CHUNK_LINES); reconcile(e, hl, l, c); l = c }
            visibleDone = true
        }
        if (dirty.full) { dirty.clear(); dirty.add(0, e.length) }  // from here on consume it from the front
        while (from < endLine) {
            val to = minOf(endLine, from + CHUNK_LINES)
            reconcile(e, hl, from, to); dirty.trimStart(paraEnd(hl, e, to - 1)); from = to
            if (System.nanoTime() > deadline) break
        }
        if (!dirty.isEmpty) schedule()
        onRestyled?.invoke()
    }
    /** Layout (visual) rows -> highlighter (logical) lines. */
    private fun visibleLogicalLines(hl: MarkdownHighlighter): IntRange {
        val layout = editText.layout ?: return 0..0; val r = scrollView.visibleLineRange(); if (r.isEmpty()) return 0..0
        return hl.lineIndexOf(layout.getLineStart(r.first))..hl.lineIndexOf(layout.getLineEnd(r.last).coerceAtMost(editText.length()))
    }
    private fun paraEnd(hl: MarkdownHighlighter, e: CharSequence, line: Int) = minOf(hl.lineEnd(line) + 1, e.length)

    /** Key diff of existing MdStyleSpans vs SpanFactory output over lines [l0, l1) widened to sticking-out spans. */
    fun reconcile(e: Editable, hl: MarkdownHighlighter, fromLine: Int, endLine: Int) {
        var l0 = fromLine; var l1 = endLine
        repeat(3) {                                             // widen until stable (stale spans after '\n' inserts)
            var a = hl.lineStart(l0); var b = paraEnd(hl, e, l1 - 1)
            for (sp in e.getSpans(a, b, MdStyleSpan::class.java)) { a = minOf(a, e.getSpanStart(sp)); b = maxOf(b, e.getSpanEnd(sp)) }
            val n0 = hl.lineIndexOf(a); val n1 = hl.lineIndexOf(maxOf(a, b - 1)) + 1
            if (n0 == l0 && n1 == l1) return@repeat; l0 = minOf(l0, n0); l1 = maxOf(l1, n1)
        }
        specs.clear(); want.clear(); factory.specsForLines(e, hl, l0, l1, specs)
        for (s in specs) want.putIfAbsent(Key(s.kind, s.arg, s.start, s.end), s)
        for (sp in e.getSpans(hl.lineStart(l0), paraEnd(hl, e, l1 - 1), MdStyleSpan::class.java)) {   // rule 5: ONLY ours
            if (want.remove(Key(sp.kind, sp.arg, e.getSpanStart(sp), e.getSpanEnd(sp))) == null) e.removeSpan(sp)
        }
        for ((k, s) in want) e.setSpan(mat.create(s), k.start, k.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        // NO beginBatchEdit/endBatchEdit (rule 3, A20: endBatchEdit -> bringPointIntoView yanks the viewport)
    }
    companion object { const val BUDGET_NS = 4_000_000L; const val CHUNK_LINES = 256 }
}
```
(`repeat` + `return@repeat` is a sketch: write it as a small `while` loop with a `changed` flag.)

**C. Controller wiring**
```kotlin
data class EditEvent(val version: Long)
private val _edits = MutableSharedFlow<EditEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
val edits: SharedFlow<EditEvent> = _edits.asSharedFlow()
var version: Long = 0; private set
private val restyler = Restyler(editText, scrollView, style) { version++; _edits.tryEmit(EditEvent(version)) }
// install: restyler.suspended = true; setText(ssb); restyler.highlighter = hl; restyler.reset(editText.length());
//          version++ (NO EditEvent: loading is not a user edit); restyler.suspended = false
// setStyle / geometry hook / highlightSyntax change: … editText.reflowAll(); restyler.markAllDirty()
//   highlightSyntax changed -> hl = MarkdownHighlighter(new flag); hl.fullScan(editText.text!!) on main; hand to restyler.
internal val isRestyleIdle get() = restyler.isIdle     // tests/harness only
```
**D. EditorPerfActivity (debug only; sketch)** — `ComponentActivity`; `EditorController(this, EditorStyle.create(this,
LightWriterColors))`, `setContentView(controller.scrollView)`, `editText.showSoftInputOnFocus = false`. Extras:
`sample` (`100k`|`300k`, via `SampleDocs.forExtra`), `perfEdits` (Int, default 24, 0 = none), `vary` (Boolean),
`verifyLayout` (Boolean). After install: caret + scroll to the middle line, `requestFocus()`, wait 2500 ms, attach a
FrameMetrics listener (copy `fmListener`/`recordEdit`/`report` formula from `plans/reference/bench/MainActivity.kt`),
then every 250 ms apply one edit at the caret via `editText.text!!.insert/delete` (plain: `"a"`; `vary`: cycle
`"a"`, `" "`, `"\n"`, `"# "`, `"*"`, `"x"`, delete-1). For each edit, measure the first frame after it. After the last
edit log `MDPERF RESULT|<sample>|per-keystroke-main-thread-work|med=%.2f|p90=%.2f|n=%d`. If `verifyLayout`: wait until
`isRestyleIdle`, capture every line's `getLineStart`/`getLineTop`, `reflowAll()`, compare after the next layout, log
`MDPERF LAYOUT|equal=<bool>|lines=<n>`.

**E. Instrumented tests** — launch `EditorPerfActivity` with `perfEdits=0` via `ActivityScenario` (debug-only class is
visible to androidTest); helper `awaitIdle()` = poll `controller.isRestyleIdle` on main, 5 s timeout, then `waitForIdleSync()`.
- `IncrementalLayoutEqualsFullReflowTest.incrementalLayoutEqualsFullReflow`: 100k sample; 50 edits from `Random(7)`
  (insert char / insert `"\n"` / insert `"# "` / `"**"` / delete 1–20 chars) at random offsets; `awaitIdle()`; then the
  §6.12 comparison (line count, every `getLineStart` and `getLineTop` equal after `reflowAll()`).
  Second test `…WithMdEditableDisabled` is NOT needed; the kill switch is for manual A/B only.
- `RestyleCorrectnessTest`: helper `spanKeys(e)` = sorted `(kind,arg,start,end)` of all `MdStyleSpan`s; `fresh(text)` =
  same keys from `buildStyledDocument(text, …)` with a new highlighter and the SAME `EditorStyle`. Each test types
  char-by-char with `insert` + `awaitIdle()` then asserts `spanKeys(editable) == fresh(snapshot)`:
  `typeAtxHeading` (`# Hi`), `typeStrong` (`**b**`), `openAndCloseFence` (insert ```` ``` ```` + `\n` above a paragraph,
  assert band, then close it), `deleteHeadingMarker`, `newlineInsideStrong`, `pasteMultiLineBlock` (one `replace` of 40 lines),
  `imeComposition` (`onCreateInputConnection(EditorInfo())` → `setComposingText("Ti",1)`, `("Tit",1)`, `finishComposingText()`),
  `headingGrowsInFirstFrame`: type `# ` → line height unchanged; insert `T`, and in `editText.doOnPreDraw {}` registered
  right after the insert assert `h(line) ≥ 1.55 × h(body)` (proves same-frame restyle).

## Acceptance criteria
1. `DirtyRangeTest` (JVM) passes: `insertBeforeShifts`, `deleteBeforeShifts`, `insertInsideExpandsEnd`,
   `insertAtStartShifts`, `insertAtEndKeepsEnd`, `deleteOverlappingStartClampsToEditStart`, `deleteCoveringRangeCollapses`,
   `editAfterUnchanged`, `twoEditsBeforeFrame` (shift, then add delta = hull), `markAllIgnoresAdd`, `trimStartConsumes`,
   `clampsToNewLength`.
2. `make test-device DEVICE=emulator-5554` green incl. both test classes above (every test name listed in §E).
3. `grep -n "beginBatchEdit\|clearSpans\|setText(" app/src/main/kotlin/dev/mdwriter/editor/Restyler.kt` → nothing.
4. Harness, 100k plain: `MDPERF RESULT|100k|…|med=` ≤ 8 and `p90=` ≤ 12; 300k plain: `med=` ≤ 12 (01 §9). Report 100k
   `vary=true` too. Numbers (3 runs each) go in STATUS.
5. `verifyLayout=true` after `vary=true` edits → `MDPERF LAYOUT|equal=true`.
6. Screenshot sequence on the small sample: after `input text "#%s"` the new line is body height; after `input text "Title"`
   it is ~1.6× — and `headingGrowsInFirstFrame` passes (same-frame proof).
7. A/B: `mdEditable=false` with the 100k harness is ≥ 5× slower (proves MdEditable is active; STATUS only, not gating).

## Verification commands
```sh
make test && make install-debug DEVICE=emulator-5554
A="adb -s emulator-5554"; P=dev.mdwriter.debug/dev.mdwriter.debug.EditorPerfActivity
$A logcat -c
$A shell am start -S -n $P --es sample 100k --ei perfEdits 24; sleep 14; $A logcat -d -s MDPERF | grep RESULT
$A shell am start -S -n $P --es sample 100k --ei perfEdits 24 --ez vary true --ez verifyLayout true; sleep 16
$A shell am start -S -n $P --es sample 300k --ei perfEdits 24; sleep 18; $A logcat -d -s MDPERF | grep -E "RESULT|LAYOUT"
$A shell am start -S -n dev.mdwriter.debug/dev.mdwriter.MainActivity --es sample small; sleep 2
$A shell input tap 700 300; $A shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_MOVE_END; $A shell input keyevent KEYCODE_ENTER
$A shell input text "#%s"; $A exec-out screencap -p > /tmp/t07-a.png; $A shell input text "Title"; $A exec-out screencap -p > /tmp/t07-b.png
make test-device DEVICE=emulator-5554 && make check
```

## Pitfalls
- C2: there is no `applyEdit`/`rescan`/`pendingRescan`. `update(s, start, before, count)` is synchronous and complete;
  the frame budget applies to the span reconcile only. `before`→`removedLen`, `count`→`addedLen`.
- Rule 3 / A20: batch edits around reconcile scroll the viewport to the caret. Batch only around OUR text edits (T08).
- Rule 5: `getSpans(…, MdStyleSpan::class.java)` only; never touch `HangRoomSpan`, composing, `SuggestionSpan`,
  `SpellCheckSpan`, selection. The `imeComposition` test guards composing survival.
- No span changes inside `onTextChanged`: the text is mid-`replace()`; spans are changed only in `doFrame`.
- Visible range from `EditorScrollView.visibleLineRange()` is in LAYOUT rows — convert to logical lines (§B).
- `HighlightDelta.endOffset` excludes the last line's `\n`; `lineIndexOf(end)` + 1 gives the exclusive end line.
- A delta with `full = true` (link-definition set changed) at 300k takes many frames: visible first, then the rest.
- `version` must increment for undo/redo/toolbar edits too (they arrive via `onTextChanged`); install bumps it without an EditEvent.
- Emulator timings are noisy: take the median of 3 runs; a cold first run after install is not representative.

## Definition of done
- [ ] Acceptance 1–7 done; MDPERF numbers + screenshot descriptions in STATUS.md (if 4 fails: check `MdEditable` is
      active and no global `UpdateLayout` span exists, then STOP-AND-ASK with numbers).
- [ ] `make check` green; `make test-device DEVICE=emulator-5554` green.
- [ ] STATUS.md entry; commit `T07: incremental restyle (Restyler, DirtyRange, perf harness)`.
