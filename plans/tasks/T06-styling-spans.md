# T06 — Live styling: MdEditable, span classes, SpanFactory, document install

**Goal** Opening a document shows it fully styled the iA way: headings bigger and bold with `#` marks at body size and
colour, real bold/italic faces, grey syntax for code/links/tables/fences, monospace code blocks on a light band,
hanging indents for wrapped list/quote lines, and done tasks greyed and struck. A 100k-char document opens to its first
styled frame in ≤ 500 ms. Text typed after opening stays unstyled until T07 (expected).

**Depends on** T03 (`:core:markdown`: `MarkdownHighlighter`, `MdSpan`, `MdKind`, `LineInfo`, `BlockType`),
T05 (`MarkdownEditText`, `EditorScrollView.onGeometryChanged`, `EditorController`, `EditorStyle`, `FontSet`,
`EditorColors`, `SampleDocs.SMALL/generate`).

**Read first**
- `plans/01-architecture.md` §4.2/§4.3, §6.1 (highlighter contract), §6.2, §7, §9, §10 rules 3–7, 9.
- `plans/02-design-spec.md` §3 (heading scale), §4 (THE mapping table — implement exactly).
- `plans/research/editor-engine.md` §4 (MdEditable mechanism), §6.1 + `plans/reference/bench/FastEditable2.kt`,
  §6.2 (span sketches; apply corrections below), §6.4 (build + `reflowAll` toggle trick).
- `plans/research/factcheck.md` A12, A13, A17; C3, C4, C5, C6, C7.
- `core/markdown/src/main/kotlin/dev/mdwriter/markdown/MdModel.kt` (KDoc of every `MdKind`: ranges, `arg`s).

## Scope — In / Out
In: `MdEditable`(+factory, kill switch), `spans/` package (marker interface, kinds, span classes, `SpanSpec`,
`TextMeasurer`, `SpanFactory`, `SpanMaterializer`), styled install, `setStyle` → one full reflow, `HangRoomSpan` at ≥ 600 dp.

Out: `Restyler`, `DirtyRange`, re-styling after edits, `edits`/`version`, perf harness → **T07**. Undo, smart
Enter, task-toggle taps (T06 only places `TaskSpan`) → **T08**. Pill → **T09**. Focus overlay → **T15**.
Settings UI → its settings task (T06 only reads `EditorStyle`). Preview CSS → Preview task.

## Files to create / modify
- `app/src/main/kotlin/dev/mdwriter/editor/MdEditable.kt` — `MdEditable`, `MdEditableFactory` (+ `enabled`).
- `app/src/main/kotlin/dev/mdwriter/editor/spans/MdStyleSpan.kt` — `interface MdStyleSpan`, `object SpanKind`.
- `app/src/main/kotlin/dev/mdwriter/editor/spans/Spans.kt` — all span classes + `HangRoomSpan`.
- `app/src/main/kotlin/dev/mdwriter/editor/spans/SpanFactory.kt` — `SpanSpec`, `TextMeasurer`, `SpanFactory` (pure).
- `app/src/main/kotlin/dev/mdwriter/editor/spans/SpanMaterializer.kt` — `SpanMaterializer`, `PaintTextMeasurer`.
- `app/src/main/kotlin/dev/mdwriter/editor/StyledDocument.kt` — `buildStyledDocument(...)` (off-main build).
- `app/src/main/kotlin/dev/mdwriter/editor/MarkdownEditText.kt` (modify) — editable factory, `reflowAll()`.
- `app/src/main/kotlin/dev/mdwriter/editor/EditorController.kt` (modify) — styled `install`, `setStyle`, hang room.
- `app/src/main/kotlin/dev/mdwriter/MainActivity.kt` (modify) — debug extra `mdEditable=false` → kill switch.
- `app/src/test/kotlin/dev/mdwriter/editor/spans/SpanFactoryTest.kt` — JVM, one test per 02 §4 row.
- `app/src/test/kotlin/dev/mdwriter/editor/spans/SpanMaterializerTest.kt` — Robolectric, class/interface rules.
- `app/src/androidTest/kotlin/dev/mdwriter/editor/InstallStylingDeviceTest.kt` — instrumented metrics.

## Steps
1. `MdEditable.kt`: copy the logic of `plans/reference/bench/FastEditable2.kt` **verbatim** (phase-marker watcher,
   `replace` override, `touchesKeep`, `getSpans` override returning `NO_WATCHERS`), renaming `FastEditable2` →
   `MdEditable` and dropping the `suppressed` counter unless `BuildConfig.DEBUG`. Add
   `object MdEditableFactory : Editable.Factory() { @Volatile @JvmField var enabled = true;
   override fun newEditable(source: CharSequence): Editable = if (enabled) MdEditable(source) else SpannableStringBuilder(source) }`.
2. `MarkdownEditText.init`: `setEditableFactory(MdEditableFactory)` as the **first** statement (before any `setText`).
   Add `reflowAll()` (editor-engine §6.4, members `reflowTrigger`/`triggerWide`).
3. `MdStyleSpan.kt`, `Spans.kt` (Reference §A/§B). `SpanFactory.kt` (§C) + `SpanFactoryTest` → `make test`.
4. `SpanMaterializer.kt` (§D) + `SpanMaterializerTest`. `StyledDocument.kt` + controller changes (§E).
5. `MainActivity`: before creating the controller, `if (BuildConfig.DEBUG) MdEditableFactory.enabled = intent.getBooleanExtra("mdEditable", true)`.
6. `InstallStylingDeviceTest`, then emulator screenshots (Acceptance 5–6), timing log (Acceptance 7).
7. `make check`, `make test-device DEVICE=emulator-5554`, STATUS entry, commit.

## Reference code
**A. Marker + kinds (copy)**
```kotlin
package dev.mdwriter.editor.spans
/** Rule 9: the Android marker. Reconcile (T07) only ever removes spans implementing this. */
interface MdStyleSpan { val kind: Int; val arg: Int }
object SpanKind {
    const val HEADING = 1 /* arg level */; const val STRONG = 2; const val EMPHASIS = 3; const val CODE = 4
    const val CODE_BLOCK = 5; const val MARKER = 6; const val STRIKE = 7; const val MARK = 8; const val DONE_TASK = 9
    const val LINK_UNDERLINE = 10; const val TABLE_ROW = 11 /* arg 0 header,1 delim,2 body */; const val TASK = 12 /* arg 1 checked */
    const val HEADING_HANG = 13 /* arg marker px */; const val HANGING_INDENT = 14 /* arg prefix px */; const val MONO = 15
    const val DIM_EMPHASIS_MARKERS = false          // 02 §4: `* _ ** __` stay text colour
}
```
**B. Span classes (sketch from editor-engine §6.2, CORRECTED)** — every class: `MdStyleSpan`, plain subclass of
`CharacterStyle`/`MetricAffectingSpan`/`LeadingMarginSpan`/`LineBackgroundSpan` (rule 6, never `ParcelableSpan`),
reads colours/sizes from the shared `EditorStyle s` at draw/measure time.
- `HeadingSpan(arg, s)`: MetricAffecting; `textSize = s.textSizePx * s.headingScale[arg]`,
  `typeface = s.fonts.of(true, s.fonts.isItalic(tp.typeface))`; in `updateDrawState` also `if (arg == 6) tp.color = s.colors.textSecondary`.
- `StrongSpan(s)` / `EmphasisSpan(s)`: MetricAffecting; if the current face is mono (`=== s.fonts.mono || === s.fonts.monoBold`)
  → Strong sets `monoBold`, Emphasis leaves it; else `s.fonts.of(bold, italic)` as in the sketch (order-independent).
- `CodeSpan(s)`: MetricAffecting; mono (monoBold if current face is bold); draw: `tp.bgColor = s.colors.codeBg`.
- `CodeBlockSpan(s)`: MetricAffecting (mono) + `LineBackgroundSpan` full-width band `s.colors.codeBg` (sketch verbatim).
- `MonoSpan(s)` (front matter), `TableRowSpan(arg, s)` (mono; arg 0 → monoBold).
- `MarkerSpan(s)`: `CharacterStyle, UpdateAppearance`; `tp.color = s.colors.markup`.
- `StrikeSpan`, `MarkSpan(s)` (`bgColor = highlightBg`, `color = highlightText`), `DoneTaskSpan(s)` (`color = textSecondary`,
  strike), `LinkUnderlineSpan` (`isUnderlineText = true`) — all `CharacterStyle, UpdateAppearance`.
- `TaskSpan(arg)`: only `MdStyleSpan` (hit-test marker for T08). **Not** `NoCopySpan` (A12: SSB copy drops those).
- `HeadingHangSpan(override val arg: Int, s)`: `LeadingMarginSpan, UpdateLayout` (rule 4);
  `getLeadingMargin(first) = if (first) -minOf(arg, s.gutterPx) else 0` → zero effect on phones (gutter 0).
- `HangingIndentSpan(override val arg: Int)`: `LeadingMarginSpan, UpdateLayout`; `if (first) 0 else arg`. Used for
  list AND quote lines (no quote bar, no italics: 02 §4).
- `HangRoomSpan(s)`: plain `LeadingMarginSpan` — **NOT `UpdateLayout`** (A17: two full reflows per keystroke), **not**
  `MdStyleSpan` (reconcile must never touch it); `getLeadingMargin = s.gutterPx`; no drawing.
- Drop the sketch's `QuoteSpan`, `QuoteTextSpan`, `ListIndentSpan` (replaced by `HangingIndentSpan`, iA look).

**C. SpanFactory (pure Kotlin: no `android.*` import; JVM-tested)**
```kotlin
data class SpanSpec(val kind: Int, val arg: Int, val start: Int, val end: Int)      // end exclusive
fun interface TextMeasurer { fun widthPx(text: CharSequence, start: Int, end: Int): Int }   // body size, regular face
class SpanFactory(private val measurer: TextMeasurer) {
    /** Specs for lines [fromLine, endLine) — endLine EXCLUSIVE like spansForLines. Appends to [out]. */
    fun specsForLines(text: CharSequence, hl: MarkdownHighlighter, fromLine: Int, endLine: Int, out: MutableList<SpanSpec>)
    /** One line; [lineSpans] = that line's MdSpans (sorted by start). */
    fun specsForLine(text: CharSequence, info: LineInfo, lineSpans: List<MdSpan>, out: MutableList<SpanSpec>)
}
```
`specsForLines` walks `hl.spansForLines(fromLine, endLine)` once with a pointer, grouping spans by line
(`span.start` in `[lineStart(l), lineEnd(l)]`; spans never cross `\n`, 01 §6.1). Rules of `specsForLine`
(`para = info.start until min(info.end + 1, text.length)`; skip any spec whose range is empty):
1. **Line-level** — `info.type` in `FENCE_OPEN, FENCE_CLOSE, FENCED_CODE, INDENTED_CODE` → `CODE_BLOCK` over `para`
   (covers the `\n`, so empty code lines get the band too). `FRONT_MATTER`, `FRONT_MATTER_FENCE` → `MONO` over the content.
2. **Hanging indent** — if `info.contentStart > info.start && !info.type.isVerbatim && (info.quoteDepth > 0 ||
   info.listDepth > 0 || line has a LIST_MARKER)` → `HANGING_INDENT(arg = measurer.widthPx(text, info.start, info.contentStart))` over `para`.
3. **ATX heading** (`ATX_HEADING`): `HEADING` span H; opening marker = the `HEADING_MARKER` starting at `H.start`;
   closing = any later `HEADING_MARKER` on the line. Content = `[open.end, close?.start ?: H.end)` trimmed of trailing
   spaces/tabs → `HEADING(level)` if non-empty (`"# "` alone stays body size: 02 §3, C5). Plus, when not in a
   quote/list, `HEADING_HANG(arg = measurer.widthPx(text, info.start, open.end))` over `para`. No span on `#` runs.
4. **Setext**: content line (`SETEXT_HEADING`) → `HEADING(level)` over the `HEADING` span; underline line
   (`SETEXT_UNDERLINE`) → its `HEADING_MARKER` → `MARKER`.
5. **Inline**, per MdSpan kind: `STRONG`→STRONG, `EMPHASIS`→EMPHASIS (whole construct); `EMPHASIS_MARKER`→MARKER only if
   `text[start]` is `~` or `=` (or `DIM_EMPHASIS_MARKERS`); `STRIKETHROUGH`→STRIKE; `HIGHLIGHT`→MARK; `CODE_SPAN`→CODE;
   `CODE_SPAN_MARKER`, `CODE_FENCE`, `CODE_INFO`, `QUOTE_MARKER`, `LINK_MARKER`, `LINK_TITLE`, `LINK_LABEL`,
   `FOOTNOTE_REF`, `THEMATIC_BREAK`, `HTML_BLOCK`, `HTML_INLINE`, `ESCAPE_MARKER`, `LINK_DEF`, `TABLE_PIPE`,
   `FRONT_MATTER`, `FRONT_MATTER_FENCE` → MARKER; `LINK_URL` → MARKER unless inside an `AUTOLINK` on the line;
   `AUTOLINK` → LINK_UNDERLINE over `[start+1, end-1)` if `text[start] == '<'`, else whole range;
   `TASK_MARKER` → MARKER + `TASK(arg)`; if `arg == 1` also `DONE_TASK` over `[first non-blank after marker, info.end)`;
   `TABLE_ROW(arg)` → `TABLE_ROW(arg)` (+ MARKER when `arg == 1`).
   No spec: `HEADING_MARKER` (ATX), `LIST_MARKER`, `BLOCKQUOTE`, `LINK`, `LINK_TEXT`, `CODE_BLOCK` (rule 1 covers it),
   `ENTITY`, `HARD_BREAK`. Write it as an exhaustive `when (span.kind)` over `MdKind` (no `else`), so a new kind fails compile.

**D. Materializer (Android side)**
```kotlin
class SpanMaterializer(private val s: EditorStyle) {
    fun create(spec: SpanSpec): MdStyleSpan = when (spec.kind) {
        SpanKind.HEADING -> HeadingSpan(spec.arg, s); SpanKind.STRONG -> StrongSpan(s) /* … one line per kind … */
        else -> error("unknown kind ${spec.kind}")
    }
}
/** Not thread-safe: one instance per build (Default) and one for main (T07). */
class PaintTextMeasurer(private val s: EditorStyle) : TextMeasurer {
    private val p = TextPaint(Paint.ANTI_ALIAS_FLAG)
    override fun widthPx(text: CharSequence, start: Int, end: Int): Int {
        p.textSize = s.textSizePx; p.typeface = s.fonts.regular; return ceil(p.measureText(text, start, end)).toInt() }
}
```
**E. Install + setStyle (sketch)**
```kotlin
fun buildStyledDocument(text: String, hl: MarkdownHighlighter, factory: SpanFactory, mat: SpanMaterializer,
                        hangRoom: HangRoomSpan?): SpannableStringBuilder {        // Dispatchers.Default
    hl.fullScan(text)
    val ssb = SpannableStringBuilder(text); val specs = ArrayList<SpanSpec>(text.length / 8)
    factory.specsForLines(text, hl, 0, hl.lineCount, specs)
    for (sp in specs) ssb.setSpan(mat.create(sp), sp.start, sp.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)   // rule 7
    hangRoom?.let { ssb.setSpan(it, 0, ssb.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE) }   // the ONE exception, see Pitfalls
    return ssb
}
// EditorController
private val hangRoom = HangRoomSpan(style)
internal var highlighter: MarkdownHighlighter? = null; private set   // main thread only after install; T07 reads it
suspend fun install(doc: InstallRequest) {                             // called on main
    val t0 = SystemClock.uptimeMillis(); val gutter = style.gutterPx
    val hl = MarkdownHighlighter(enableHighlight = style.highlightSyntax, enableFrontMatter = true)
    val ssb = withContext(Dispatchers.Default) {
        buildStyledDocument(doc.text, hl, SpanFactory(PaintTextMeasurer(style)), SpanMaterializer(style), hangRoom.takeIf { gutter > 0 }) }
    val t1 = SystemClock.uptimeMillis()
    editText.setText(ssb, TextView.BufferType.EDITABLE)                 // factory copies into MdEditable
    highlighter = hl                                                     // explicit hand-off: Default -> main
    editText.setSelection(doc.selection.coerceIn(0, editText.length())); editText.showSoftInputOnFocus = !doc.readOnly
    scrollView.doOnNextLayout { scrollView.scrollTo(0, doc.scrollY) }
    editText.doOnPreDraw { Log.i("MDPERF", "OPEN|chars=${doc.text.length}|build=${t1 - t0}|firstFrame=${SystemClock.uptimeMillis() - t0}") }
}
fun setStyle(s: EditorStyle) { /* T05 body (colours, typeface, applyGeometry) */ ; editText.reflowAll() }
```
In `init`, set `scrollView.onGeometryChanged = { g -> syncHangRoom(g.gutterPx) }`:
`syncHangRoom`: text spanned `e`; if `gutter > 0` and `e.getSpanStart(hangRoom) < 0` → `e.setSpan(hangRoom, 0, e.length, SPAN_INCLUSIVE_INCLUSIVE)`;
if `gutter == 0` and attached → `e.removeSpan(hangRoom)`; then `editText.reflowAll()` (width changes already relayout;
the toggle makes the new gutter value take effect). Log with `Log` from `util/Log.kt` (debug-gated) if it exists.

## Acceptance criteria
1. `SpanFactoryTest` (JVM, real `MarkdownHighlighter`, fake measurer `{ _, s, e -> (e - s) * 10 }`), one `@Test` per
   02 §4 row, asserting the exact sorted spec list (offsets per `MdModel.kt` KDoc; if an expectation disagrees with the
   highlighter, re-read the KDoc and fix the TEST, never `:core:markdown`):
   `atxHeadingContentOnly` (`# Title` → only `HEADING(1)[2,7)` + `HEADING_HANG(20)[0,7)`), `emptyAtxHeadingHasNoHeadingSpan`,
   `closingHashesExcluded` (`## Hi ##` → `HEADING(2)[3,5)`), `setextHeadingAndUnderline`, `emphasisMarkersUnstyled`
   (`*a* **b**` → EMPHASIS+STRONG, no MARKER), `strikeMarkersGrey`, `highlightOnlyWhenEnabled`, `inlineCode`,
   `fencedBlockBandsEveryLineIncludingEmpty` (```` ```kt\n\nx\n``` ```` → 4 CODE_BLOCK incl. the empty line, MARKER on fences + `kt`),
   `indentedCode`, `quoteMarkerGreyAndHangingIndent`, `listMarkerPlainWithHangingIndent`, `taskOpen`, `taskDoneGreyStrike`,
   `inlineLinkSyntaxGreyTextPlain`, `autolinkUnderlinedUrlNotGrey`, `bareUrlUnderlined`, `footnoteHrHtmlEscapeLinkDefGrey`,
   `tableRowsAndPipes`, `frontMatterMonoGrey`, `entityAndHardBreakUnstyled`, `h6Heading`, `multiLineRangeGrouping`
   (specsForLines(1, 3) returns only lines 1–2), `dimEmphasisMarkersIsFalse`.
2. `SpanMaterializerTest` (Robolectric): for every `SpanKind` the object `is MdStyleSpan`, `!is ParcelableSpan`,
   `!is NoCopySpan`; `HeadingHangSpan`/`HangingIndentSpan` `is UpdateLayout`; `HangRoomSpan !is UpdateLayout && !is MdStyleSpan`;
   `HeadingHangSpan(30).getLeadingMargin(true) == 0` when `gutterPx = 0` and `== -30` when `gutterPx = 40`.
3. `InstallStylingDeviceTest` (instrumented, `SampleDocs.SMALL`, portrait): with `ex = style.lineSpacingExtraPx`, line
   box `h(l) = getLineBottom(l) − getLineTop(l) − ex`: `h(H1) ≥ 1.55 × h(body)`, `h(H2) ≥ 1.35 × h(body)`; the
   editable `is MdEditable`; every `MdStyleSpan` has flags `SPAN_EXCLUSIVE_EXCLUSIVE`; no `HangRoomSpan` at 448 dp;
   the wrapped quote/list line's `getLineLeft` (2nd visual line) > first line's.
4. `grep -rn "ParcelableSpan\|NoCopySpan\|SPAN_PARAGRAPH\|beginBatchEdit" app/src/main/kotlin/dev/mdwriter/editor` → nothing.
5. Screenshot (`--es sample small`, light theme) matches 02 §4 — check and record each: H1 ≈ 1.6× body cap height;
   `#`/`##` body size and text colour (#1A1A1A); `**`/`*` text colour, content bold/italic (real faces, not synthetic);
   backticks grey, code mono on #EDEDED; fenced block = continuous band incl. fence lines, fences + `kotlin` grey;
   `>` grey, quote text body colour, not italic, wrapped line indented under the text; list wrap under item text;
   `[ ]`/`[x]` grey, done item grey + struck; link text plain, `[]()` + URL grey; `<https://example.org>` underlined,
   `<>` grey; table mono, pipes + delimiter row grey, header bold; `~~strike~~` struck with grey `~~`; `***` grey.
6. Rotate to landscape (> 840 dp): heading `#` runs hang left of the body column (body text left edge aligned).
7. `adb logcat -d -s MDPERF | grep OPEN` for `--es sample 100k`: `firstFrame` ≤ 500 (report 3 runs; STATUS).

## Verification commands
```sh
make test && make install-debug DEVICE=emulator-5554
A="adb -s emulator-5554"
$A shell am start -S -n dev.mdwriter.debug/dev.mdwriter.MainActivity --es sample small; sleep 2; $A exec-out screencap -p > /tmp/t06-small.png
$A shell settings put system accelerometer_rotation 0; $A shell settings put system user_rotation 1; sleep 2
$A exec-out screencap -p > /tmp/t06-land.png; $A shell settings put system user_rotation 0; $A shell settings put system accelerometer_rotation 1
for i in 1 2 3; do $A shell am start -S -n dev.mdwriter.debug/dev.mdwriter.MainActivity --es sample 100k; sleep 4; done
$A logcat -d -s MDPERF | grep OPEN
make test-device DEVICE=emulator-5554 && make check
```

## Pitfalls
- A17 / rule 4: `HangRoomSpan` must not be `UpdateLayout`; per-line `LeadingMarginSpan`s must be. `MetricAffectingSpan` is already `UpdateLayout`.
- Rule 7 exception: `HangRoomSpan` needs `SPAN_INCLUSIVE_INCLUSIVE` (with EXCLUSIVE_EXCLUSIVE, text typed at the document
  end or start falls outside it and loses the gutter). It is the only non-exclusive span; it is set once, never reconciled.
- C4: no span on ATX `#`, `* _ ** __`, list markers, `>` content — fewer spans = faster. C5: HeadingSpan starts after the marker run.
- C3 / rule 9: marker interface `MdStyleSpan`; `MdSpan` is the `:core:markdown` data class.
- A12: `TaskSpan` must not be `NoCopySpan` or `setText`'s factory copy drops it.
- Colours are read at draw time, but TextView caches per-block display lists: a colour-only change still needs `reflowAll()`.
- `MarkdownHighlighter` is not thread-safe: create it per document, `fullScan` on Default, then main only (01 §6.1).
  `PaintTextMeasurer` likewise: never share one between the Default build and main.
- `setEditableFactory` after a `setText` = the first document is a plain SSB (no MdEditable) → 162 ms keystrokes later.
- Paragraph spans end at `min(lineEnd + 1, length)`: a range past `length` throws `IndexOutOfBoundsException`.
- Indent/hang widths are px at the current size/font; after a font/size/width-class change they are refreshed by T07's
  full restyle (`markAllDirty`). Until T07, a rotation across 600 dp shows slightly stale indents — acceptable.

## Definition of done
- [ ] Acceptance 1–7 pass; screenshot checklist + OPEN timings in STATUS.md.
- [ ] `make check` green; `make test-device DEVICE=emulator-5554` green.
- [ ] STATUS.md entry (incl. any SpanFactoryTest expectation you corrected and why); commit `T06: live styling spans + styled install`.
