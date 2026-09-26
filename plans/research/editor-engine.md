# mdwriter: Editor Engine Research (the widget that renders live-styled Markdown)

Status: complete (retry run). Items that are not verified are marked UNVERIFIED.
Date: 2026-09-25. Targets: minSdk 36, targetSdk/compileSdk 37, Compose BOM 2026.09.00 (Compose foundation 1.12.1).
Evidence comes from three places:
- Measurements on the AVD `Pixel_10_Pro_XL` (android-37.1, 16k pages, Apple-silicon host). The bench APK is a release build, AOT-compiled with `cmd package compile -m speed -f`.
- Platform source: `~/Library/Android/sdk/sources/android-37.0` (the official API 37 sources package, `Pkg.Revision=2`). **All platform line numbers below refer to that tree.** The copies in `scratchpad/src/*.java` are from AOSP main and differ slightly.
- Compose source: `scratchpad/research/src/foundation/` (foundation sources). The class list was cross-checked against the resolved `foundation-android-1.12.1` AAR in `~/.gradle/caches`.

---

## 0. Decision summary

**The engine is the platform `android.widget.EditText`, subclassed as `MarkdownEditText`. No AppCompat.** It is hosted in Compose through `AndroidView`, and all styling comes from `android.text` spans on a custom `Editable`. Three techniques are mandatory:

1. **`MdEditable`.** This is a `SpannableStringBuilder` subclass, installed with `setEditableFactory`. Every `SpannableStringBuilder.replace()` ends by broadcasting a span-changed event to every `SpanWatcher` for every span that was only *shifted* by the edit. For those broadcasts, when they are far from the selection or composing region, `MdEditable` returns **no watchers**. Without this, stock `EditText` re-lays out every styled paragraph after the cursor on every keystroke: 162 ms/keystroke at 100k chars and 696 ms at 300k (measured). The trick is detailed in §4.
2. **Diff ("reconcile") restyle.** After an edit, re-tokenize only the dirty lines. Remove or add only the spans that actually differ, and never touch spans we don't own (composing, spell-check, selection). Remove-all/add-all costs a DynamicLayout reflow for every metric span it churns.
3. **Custom span classes** that implement a marker interface `MdSpan` and are **not** `ParcelableSpan`. That way we only ever remove our own spans, and styling never leaks into the clipboard or IME.

Measured result, per-keystroke main-thread work (edit + restyle + frame UI time, median / p90):

| Engine | 20k chars | 100k chars | 300k chars |
|---|---|---|---|
| **EditText + MdEditable + reconcile (RECOMMENDED)** | **2.1 / 2.4 ms** | **5.4 / 6.4 ms** | **6.8 / 9.4 ms** |
| Compose `BasicTextField(TextFieldState)` + `OutputTransformation` styling | 48.9 / 83.2 ms | 832 / 847 ms | 6,969 / 7,051 ms |
| Compose `BasicTextField(TextFieldState)`, **no styling at all** | 11.0 / 13.2 ms | 120 / 133 ms | 866 / 869 ms |

Compose's text field lays out the whole document on every change (§3), so it is disqualified for 100k–300k documents even before any styling.

Other decisions, with detail in §5 and §6:

| Topic | Decision |
|---|---|
| Widget | `class MarkdownEditText(ctx) : EditText(ctx, null, 0, R.style.Widget_MdWriter_Editor)`. Platform EditText, no AppCompat. The style sets `android:allowUndo=false`. |
| Scrolling | The EditText scrolls itself (bounded height, `MATCH_PARENT`). Never wrap it in a ScrollView or a Compose `verticalScroll`. |
| Heading size | `HeadingSpan : MetricAffectingSpan` sets `textSize` and a bold typeface in one span per heading line. |
| Bold/italic | `StrongSpan` / `EmphasisSpan : MetricAffectingSpan` choose from an explicit `FontSet` (regular/italic/bold/boldItalic/mono). No fake bold. |
| Dim markers | `MarkerSpan : CharacterStyle, UpdateAppearance` (color only, so no relayout when it is added or removed). |
| Hanging `#` | One global `HangRoomSpan : LeadingMarginSpan` (margin H) over the whole text, plus `HeadingHangSpan` returning **−markerWidth** for the heading's first line. **Verified on the emulator**: the `#` sits in the margin and heading text aligns with body text. |
| Code blocks | `CodeBlockSpan : MetricAffectingSpan, LineBackgroundSpan` (mono font plus a full-width tinted background per line). Inline code uses `CodeSpan` (mono plus an optional background). |
| Blockquote | `QuoteSpan : LeadingMarginSpan, UpdateLayout` (indent plus an optional 2dp bar drawn in `drawLeadingMargin`), plus `MarkerSpan` on `>`. |
| Lists | `ListIndentSpan : LeadingMarginSpan, UpdateLayout` (wrapped lines hang under the item text). |
| Caret | `setTextCursorDrawable(GradientDrawable 2dp × 1, iA blue)`. **Verified.** Editor sizes it to the line height *without* line spacing. |
| Selection color | `highlightColor = accent @ ~25–30% alpha`. Handles use `setTextSelectHandle*(getTextSelectHandle*()!!.mutate().setTint(accent))`. |
| Selection toolbar | `customSelectionActionModeCallback` returns **true** from `onCreateActionMode` and **clears the menu** in both `onCreateActionMode` and `onPrepareActionMode`. This hides the system toolbar and **keeps the selection handles**. Our Compose overlay toolbar is shown while `hasSelection() && hasFocus()`. **Verified on the emulator.** Returning *false* dismisses the selection (source). Returning `null` from `startActionMode` keeps the selection but **hides the handles** (verified). |
| Undo/redo | Own `UndoManager`, with platform undo disabled via `android:allowUndo=false`. The platform one works (Ctrl+Z/Ctrl+Y/Ctrl+Shift+Z), but it merges a whole typed run into one step and has no public `canUndo()`. |
| Focus Mode | **No spans.** Draw a translucent background-colored overlay in `onDraw` with `canvas.clipOutPath(layout.getSelectionPath(focusStart, focusEnd))`. **Verified.** Cost is O(visible) per frame, and moving the cursor just calls `invalidate()`. |
| Typewriter | Override `bringPointIntoView(offset, requestRectWithoutFocus)` and animate `scrollY` so the caret line sits at 45% of the height. Use constant top/bottom padding derived from the *display* height, **never** changed on IME show/hide. |
| Column | Centered max-width column via left/right padding computed in `onMeasure`. Padding changes null the layout (full relayout), so recompute only when the width changes. |
| Threading | Main thread, synchronous, incremental. Tokenize dirty lines and reconcile inside a `Choreographer` frame callback, so edits in one frame coalesce and styling lands before that frame draws. Long cascades (fence toggles) are time-sliced at about 4 ms per frame, visible lines first. Opening a document parses and builds the styled `SpannableStringBuilder` on `Dispatchers.Default`, then calls `setText` on main. |
| Copy/paste | Copy/cut put a plain `String` on the clipboard. Paste is mapped to `android.R.id.pasteAsPlainText`. |
| Search | `setSearchResultHighlights(...)` + `setFocusedSearchResultIndex` (API 34). No spans. |

---

## 1. The salvaged benchmark app: what each mode does

Location: `scratchpad/bench-editor/` (package `dev.bench.mdtext`).
- Scripts: `run.sh` starts the activity and greps the `MDBENCH` logcat. `e2e.sh` is the original matrix. `e2e2.sh` is this session's AOT matrix.
- Styler (`Md.kt`): `Doc.generate(n)` produces a synthetic document. It has a heading every ~1.3k chars, inline `**bold**`, `_it_`, `` `code` ``, and `[t](url)` on about 1 in 30 words, a 3-item list, a quote, and a fence every 4 sections. `MdStyler.parse` is a simple line/regex styler that emits `(kind, start, end, level)`. The 300k document has 15,116 spans.
- Span setup: `ASpans.apply` maps kinds to `AbsoluteSizeSpan`+`StyleSpan(BOLD)` for headings, `ForegroundColorSpan` gray for markers, `StyleSpan` for bold/italic, `TypefaceSpan(MONOSPACE)` for code, and gray for quote and URL. The classes are subclassed as `MdSize`, `MdStyle`, `MdColor`, `MdFont` and all implement a marker `MdSpan`. All flags are `SPAN_EXCLUSIVE_EXCLUSIVE`.
- Timing: `scheduleEdits` inserts `"a"` at `len/2` every 250 ms. It records the time of the edit call, then pairs it with the next frame's `FrameMetrics` (animation + layout/measure + draw + input + sync). The headline metric is `per-keystroke-main-thread-work = edit call + frame UI time`.

Modes:

| Mode | What it does |
|---|---|
| `edittext-plain` | Stock `EditText`, no spans. Floor. |
| `edittext` | Stock `EditText` and stock `SpannableStringBuilder`. After each insert: remove every `MdSpan` fully inside the edited paragraph, re-parse the paragraph, and add its spans again (paragraph-local restyle). |
| `edittext-fast` | Same restyle, but with `setEditableFactory(FastEditable.Factory)`. This is v1 of the trick: hide `DynamicLayout`'s `ChangeWatcher` from the post-edit span broadcast (§4). |
| `edittext-fast2` | *(added this session)* `FastEditable2`: silence **all** SpanWatchers for post-edit broadcasts whose range doesn't touch the selection or composing region. |
| `edittext-fast2r` | *(added this session)* `FastEditable2` plus a **reconcile** restyle (diff wanted vs existing, key = class+attr+start+end). It also sets a blue 2dp caret and highlight color. This is the recommended engine shape. |
| `compose-plain` | `BasicTextField(TextFieldState)`, no styles. |
| `compose-state` | `TextFieldState` with styles added once via `state.edit { addStyle(style, TextRange, ExpandPolicy.AtEnd) }` (tracked styles). |
| `compose-ot` | `BasicTextField(state, outputTransformation = OutputTransformation { parse(asCharSequence()); addStyle(...) })`, which re-parses the full text on every change. |
| `micro` | Background thread, no view: `SpannableStringBuilder` + `DynamicLayout` insert cost, StaticLayout full build, full parse. |

Extras added this session, via intent extras:
- `--ez vary true`: varied edits (char, newline, delete 3, inline markup, `## ` at paragraph start).
- `verifyLayout()`: after the edits, compares every line start and top of the incrementally maintained layout against a forced full reflow.
- `--es tb native|clear|null`: selection-toolbar experiments (`NoFloatEditText.kt`).
- `--ez hang true`: hanging heading markers via negative margin.
- `--ez focus true`: Focus-mode overlay.

---

## 2. Benchmark results

### 2.1 End-to-end: per-keystroke main-thread work, median / p90, AOT (`e2e-results-aot.txt`, `e2e-results-fast2.txt`)

| Mode | 20k | 100k | 300k |
|---|---|---|---|
| edittext-plain (floor) | 3.4 / 16.2 | 1.9 / 3.5 | 3.9 / 5.0 |
| edittext (stock SSB, paragraph restyle) | 34.1 / 45.7 | **161.8** / 164.5 | **695.7** / 708.8 |
| edittext-fast (v1) | 7.2 / 15.7 (2nd run 4.5) | 18.4 / 21.9 (2nd run 15.7) | 48.1 / 53.4 (2nd run 35.8) |
| edittext-fast2 (v2) | 6.6 / 8.8 | 11.3 / 27.8 | 17.5 / 23.7 |
| **edittext-fast2r (v2 + reconcile)** | **2.1 / 2.4** | **5.4 / 6.4** (varied-edit run: 6.6 / 8.2) | **6.8 / 9.4** |
| compose-plain | 11.0 / 13.2 | 119.7 / 132.6 | 866 / 869 |
| compose-state | 54.9 / 57.3 | 874 / 942 | 6,976 / 7,019 |
| compose-ot | 48.9 / 83.2 | 832 / 847 | 6,969 / 7,051 |

The previous agent's JIT (not AOT) run at 20k was consistent: plain 1.5, naive 54.5, fast 5.8, compose-plain 16.1, compose-state 57.9. compose-ot under JIT was 49.8 (this session).

Open-to-first-frame is measured from `setText`/`setContent` to the first frame. It is noisy (the first run after install paid warm-up):
- EditText variants: 0.6–1.2 s at every size, and 1.48 s for naive at 300k.
- compose-state / compose-ot: 8.2 s at 300k.

### 2.2 Micro benchmark: SSB + DynamicLayout only, no TextView, one-char insert mid-document

| | 20k (1,041 spans) | 300k (15,116 spans) |
|---|---|---|
| Insert, no spans | 0.06 ms | 0.06 ms |
| Insert, stock SSB with spans | 26.2 ms | **467.6 ms** |
| Insert, FastEditable v1 | 0.41 ms | 6.72 ms |
| v1 insert + naive remove/add restyle of the paragraph | 1.43 ms | 17.07 ms |
| Full parse (bench regex styler) | 3.3 ms | 24.5 ms |
| StaticLayout full build | 8.3 ms | 68.6 ms |
| DynamicLayout initial build (no spans / with spans) | 15 ms / 5–7 ms (JIT noise) | 111 ms / 93–262 ms |

### 2.3 Correctness check (layout equality)

Setup: 100k document, 20 varied edits (insert char, insert `\n`, delete 3 chars, insert ` **bold** and `code` `, insert `## ` at paragraph start). Then the incremental layout was compared against a forced full reflow (set and remove an `UpdateLayout` span over `[0,len]`).
Result: `VERIFY lines=3030 height=309173 result=OK` for **all four** modes (edittext, fast, fast2, fast2r). Skipping the shifted-span broadcasts leaves the layout exactly identical. The same check should run as an instrumented test in the real app (§6.12).

### 2.4 Visual proofs (emulator screenshots)

- `scratchpad/shots/editor-proof.png`: bigger bold headings, dimmed `**`, `_`, `` ` ``, `[]()` markers, gray URLs, monospace inline code.
- `scratchpad/shots/tb-native-s.png`: system blue 2dp caret, plus the default insertion "Select all" toolbar.
- `scratchpad/shots/tb-native-crop.png`: stock selection toolbar (Cut, Copy, Share, Select all).
- `scratchpad/shots/tb-clear-s.png`: menu-clearing callback. Selection and both handles are present, **no toolbar**, IME shown.
- `scratchpad/shots/tb-null-s.png`: `startActionMode` returns null. Selection is present but **no handles** (rejected).
- `scratchpad/shots/hang-top-s.png`: `# Section 0 lorem` with `# ` hanging in the left margin and the text aligned with body paragraphs.
- `scratchpad/shots/hang-focus-s.png`: Focus overlay. The current paragraph is black and everything else is faded.

Caveats. These are emulator numbers on a fast host, so expect roughly 1.5–3× slower on a mid-range phone. The synthetic document is markup-dense; ordinary prose has fewer spans per kB. The real tokenizer will emit more span kinds (link parts, list and task markers), so the plan must keep span count low (§5.3).

---

## 3. Compose `BasicTextField(TextFieldState)`: definitive API and why it is slow

### 3.1 API (foundation 1.12.1)

- `TextFieldBuffer.addStyle(spanStyle: SpanStyle, start: Int, end: Int)` and `addStyle(paragraphStyle: ParagraphStyle, start: Int, end: Int)` are **stable** public API. The functions carry only an internal `@OptIn(ExperimentalFoundationApi::class)`. Source: `commonMain/androidx/compose/foundation/text/input/TextFieldBuffer.kt:607–652`.
- New overloads return a `TrackedRange`: `addStyle(spanStyle, range: TextRange, expandPolicy: ExpandPolicy): TrackedRange<SpanStyle>` and the same for `ParagraphStyle` (`TextFieldBuffer.kt:673–720`).
  - `class TrackedRange<T>` is at `TrackedRange.kt:49`.
  - `value class ExpandPolicy` is at `TrackedRange.kt:59`, with `AtStart`, `AtEnd`, `AtBoth`, `InsideOnly`.
  - The public API dump (`scratchpad/src/foundation-current.txt:3347–3431`) lists them.
  - These overloads throw `IllegalStateException` unless `ComposeFoundationFlags.isBasicTextFieldStyledTextEnabled` is true.
- `ComposeFoundationFlags.isBasicTextFieldStyledTextEnabled` defaults to **`true`** in 1.12.1 (`ComposeFoundationFlags.kt:174`, marked "TODO: Remove this flag once it has soaked (b/494340211)").
  - With the flag on, `addStyle` works in any buffer scope (`InputTransformation`, `TextFieldState.edit`), and styles are *tracked* (they shift with edits, like `ExpandPolicy.AtEnd`).
  - With the flag off, `addStyle` is only legal inside an `OutputTransformation` (`TextFieldBuffer.kt:551–559`, "You can add styling to a [TextFieldBuffer] only from an [OutputTransformation]").
- `OutputTransformation` is `@Stable fun interface` (`OutputTransformation.kt:26–27`).
- So yes: per-range `SpanStyle`/`ParagraphStyle` styling of a `TextFieldState` is supported and stable in 1.12.1.

### 3.2 Why it is slow (source)

- **Full-document relayout on every change.** `TextFieldLayoutStateCache.computeLayout()` (`internal/TextFieldLayoutStateCache.kt:287–321`) calls `textMeasurer.measure(text = AnnotatedString(text = visualText.toString(), annotations = …), …)`. It copies the entire text, then lays out a whole-document `MultiParagraph`.
  - Upstream acknowledges it: "TODO(b/294403840) Don't use TextMeasurer – it is not designed for this use case…" (line ~294).
  - The cache in `getOrComputeLayout` (lines 153–200) only hits when text and annotations are unchanged.
- **The output transformation copies everything.** It runs in a `derivedStateOf` (`TransformedTextFieldState.kt:118–128`). `calculateTransformedText` builds a new `TextFieldBuffer` copy of the full text on every change (`TransformedTextFieldState.kt:554–600`), and our parser then re-scans all of it.
- Measured consequence: even **unstyled** compose-plain costs 120 ms/keystroke at 100k and 866 ms at 300k. With styles, the MultiParagraph/span conversion takes about 7 s/keystroke at 300k.
- There is no incremental layout path in 1.12.1. **Verdict: not viable** for this app. Revisit only if a future foundation release replaces TextMeasurer with an incremental paragraph cache (b/294403840).

---

## 4. Why stock EditText span styling is slow, and the MdEditable fix

### 4.1 Mechanism (API 37 source)

1. `SpannableStringBuilder.replace()` runs these phases in order (`SpannableStringBuilder.java:537–582`):
   - `getSpans(start, start+origLen, TextWatcher.class)` (537)
   - `sendBeforeTextChanged` (538)
   - `change()`
   - `sendTextChanged` (578)
   - `sendAfterTextChanged` (579)
   - **`sendToSpanWatchers(start, end, delta)`** (582)
2. `sendToSpanWatchers` (602–) iterates **all** spans. For every span whose start or end moved (every span after the edit point, since `nbNewChars != 0`), it calls `sendSpanChanged(...)` (1302–1310). That does `getSpans(min(oldStart,start), …, SpanWatcher.class)` (1305–1306) and calls `onSpanChanged` on each watcher.
3. `DynamicLayout.ChangeWatcher` (`DynamicLayout.java:1232`) handles `onSpanChanged` (1344): `if (o instanceof UpdateLayout)`, it reflows the span's range. **Every `MetricAffectingSpan` implements `UpdateLayout`**, including `StyleSpan`, `AbsoluteSizeSpan`, `RelativeSizeSpan` and `TypefaceSpan`. So every shifted bold, size or font span re-lays out its paragraph: O(spans after cursor × paragraph layout) per keystroke. That is 467 ms per insert at 300k (micro).
4. `TextView.ChangeWatcher` also handles `onSpanChanged` via `TextView.spanChange()` (`TextView.java:13036`). For every shifted `CharacterStyle`/`ParagraphStyle`/`UpdateAppearance` span (13092–13105) it does `invalidate()`, `checkForResize()`, 2× `Editor.invalidateTextDisplayList(...)`, and `invalidateHandlesAndActionMode()`. It adds extracted-text bookkeeping for `ParcelableSpan`s.
5. Priorities: DynamicLayout's watcher `PRIORITY = 128` (`DynamicLayout.java:58`). TextView's `CHANGE_WATCHER_PRIORITY = 100` (`TextView.java:479`). Higher priority is notified first.

### 4.2 The fix: `MdEditable`

- A `TextWatcher` with **priority 0** (no priority bits) spans the whole text (`SPAN_INCLUSIVE_INCLUSIVE`). It is therefore the **last** `afterTextChanged` to run. When it runs at replace-depth 1, it records the post-edit selection start/end and composing start/end, then sets `silent = true`. Everything after that point inside `replace()` is `sendToSpanWatchers`.
- While `silent`, `getSpans(qs, qe, SpanWatcher::class.java)` returns an **empty array** unless `[qs, qe]` contains one of the recorded positions. So selection and composing broadcasts, and anything overlapping the cursor, are still delivered normally.
- `silent` is cleared when the outermost `replace()` returns.

### 4.3 Why this is safe

These are the SpanWatchers attached to an EditText's Editable and what each one does with span events:

| Watcher | What it does with a shifted-span event | Why skipping is safe |
|---|---|---|
| `DynamicLayout.ChangeWatcher` | Reflows the span range if `UpdateLayout`. | The edited paragraph was already reflowed via `onTextChanged` (spans had been moved by `change()` before `sendTextChanged`). Other paragraphs' text and spans only shifted, and DynamicLayout shifts their line starts itself. **§2.3 verified identical layouts.** |
| `TextView.ChangeWatcher` → `spanChange` | Selection spans: cursor invalidation, `onSelectionChanged`, action-mode refresh. Styling spans: invalidate and display-list invalidation. | Selection spans are always delivered (their query range contains the new selection position). For styling spans, the text change already invalidated the edited region, and display lists of shifted blocks remain valid (DynamicLayout keeps block validity for unchanged lines). |
| `Editor.SpanController` | Only `EasyEditSpan` / `SuggestionRangeSpan` popups. | Those spans sit at the edit or cursor. |
| Key-listener meta state (`TextKeyListener`, `MetaKeyKeyListener`) | Reacts only to `Selection.SELECTION_END`. | Delivered. |

Spans *added* inside `replace()` (pasted styled text) are covered by the same argument: they lie inside the replaced range, which was reflowed. Our own restyle runs **outside** `replace()` (in a frame callback), so its `onSpanAdded`/`onSpanRemoved` events always reach DynamicLayout and trigger the needed reflows.

### 4.4 Fragility and mitigations

- The trick relies on implementation details that were stable through API 37: the phase order of `replace()`, and watchers being looked up through the virtual `getSpans(int,int,Class)` (lines 1285, 1294, 1306).
- Mitigation 1: an instrumented layout-equality test (§6.12), run on every target API.
- Mitigation 2: a debug toggle to fall back to a plain SSB.
- The worst case if a future platform changes this is **either** the old slowness (the filter never matches) **or** a stale layout for shifted paragraphs. The test catches the second.
- v1 (filter only watchers whose `enclosingClass == DynamicLayout`) is the more conservative variant: 3–7× slower at 300k but touches only one watcher. Recommendation: ship v2 with the test.

### 4.5 Remaining costs

At 300k, about 5 ms of the 6.8 ms is `SpannableStringBuilder` itself. There are O(N) array loops in `change()` / `sendToSpanWatchers` / interval-tree maintenance over about 15k spans, plus the empty `getSpans` calls. Keeping the span count low matters (§5.3). A fully custom `Editable` would remove this, but it is out of scope.

---

## 5. Engine design

### 5.1 Components (module placement)

- `:core:markdown` (pure JVM, owned by the markdown track) provides the incremental tokenizer (see `research/proto/MarkdownHighlighter.kt`, `MdModel.kt`: `MdKind`, `MdSpan(kind,start,end,arg)`, per-line spans, line-state convergence). The engine needs this contract from it:
  - `setText(CharSequence)` does a full scan and is callable on a background thread with a String.
  - `applyEdit(start: Int, before: Int, count: Int, text: CharSequence)` updates the line table **without** diffing the whole text. (The proto's `update(newText)` does an O(n) prefix/suffix diff. Replace it with an edit-based entry point fed from `TextWatcher.onTextChanged`.)
  - `rescan(maxLines: Int): IntRange?` returns the line range whose spans changed. It is resumable, so a cascade (fence open or close, front matter, ref-def label set change) can be time-sliced. `pendingRescan: Boolean` reports whether work remains.
  - `spansForLines(from, to): List<MdSpan>` returns absolute offsets.
  - Threading: the tokenizer is confined to the **main thread** after open. The initial full scan may run on `Dispatchers.Default` because the view doesn't exist yet.
- `:app` `editor/` package:
  - `MdEditable` + `MdEditableFactory`
  - `spans/*` (span classes + `FontSet` + `SpanFactory`)
  - `Restyler` (dirty tracking, frame scheduling, reconcile)
  - `MarkdownEditText` (the widget)
  - `SelectionUi` (state + Compose toolbar)
  - `UndoManager`
  - `FocusMode` (sentence and paragraph bounds)
  - `EditorHost` composable (`AndroidView`)

### 5.2 MdKind → span mapping

"M" means metric-affecting (reflow on add/remove), "A" appearance-only (redraw), "P" paragraph (reflow).

| MdKind | Span(s) | Type | Notes |
|---|---|---|---|
| HEADING(level) | `HeadingSpan(level)` over the heading line content | M | textSize = base × scale[level] (e.g. 1.0–1.6 depending on the design track), bold typeface from FontSet, italic preserved. One span, not size+style. |
| HEADING (first line) | `HeadingHangSpan(markerWidthPx)` over the line | P | `getLeadingMargin(first) = if (first) -markerWidth else 0`. markerWidth = `headingPaint.measureText(markerText)` where markerText = the `#…# ` run including the space. Clamp to ≤ H. |
| HEADING_MARKER | `MarkerSpan(dimColor)` | A | Dim `#`. Inherits heading size and weight from `HeadingSpan`, which covers it. |
| STRONG | `StrongSpan` over the whole construct | M | `FontSet.of(bold=true, italic=isItalic(cur))` |
| EMPHASIS | `EmphasisSpan` | M | `FontSet.of(isBold(cur), italic=true)` |
| STRIKETHROUGH | `StrikeSpan` | A | `tp.isStrikeThruText = true` |
| HIGHLIGHT (`==x==`) | `MarkSpan(bg)` | A | `tp.bgColor` |
| CODE_SPAN | `CodeSpan(mono)` | M | Mono typeface, optional `tp.bgColor`. Mono size factor ~0.9 if the design track wants it. |
| CODE_BLOCK / CODE_FENCE line | `CodeBlockSpan(mono, bg)` per line | M + line bg | `LineBackgroundSpan.drawBackground` fills `left..right`, top..bottom. |
| CODE_FENCE, CODE_SPAN_MARKER, EMPHASIS_MARKER, LINK_MARKER, QUOTE_MARKER, LIST_MARKER, TASK_MARKER, THEMATIC_BREAK, TABLE_PIPE, ESCAPE_MARKER, FRONT_MATTER_FENCE | `MarkerSpan(dimColor)` | A | Share one color per theme. The span *object* must be distinct per range (a span object can only be attached once). |
| LINK_URL, LINK_TITLE, LINK_LABEL, AUTOLINK (url part) | `MarkerSpan(dimColor)` (iA dims URLs) | A | LINK_TEXT gets nothing, or `LinkTextSpan` if the design wants a color. |
| BLOCKQUOTE(depth) | `QuoteSpan(depth)` per line | P | Indent = depth × quoteIndent. Optional bar drawn in `drawLeadingMargin`. Implements `UpdateLayout`. Text color via the same span's `updateDrawState`? A `LeadingMarginSpan` can't change color, so add a separate `QuoteTextSpan : CharacterStyle`. |
| List item | `ListIndentSpan(first=0, rest=markerWidth)` on the item's first line; continuation lines get `rest` via their own span | P | Wrapped lines align after the marker. Nested depth adds `depth × indent`. |
| TASK_MARKER | `MarkerSpan` + `TaskSpan(checked)` | A | `TaskSpan` is a plain marker object used for hit testing. It must **not** be a `NoCopySpan` (see §6.2). Checked items may get `StrikeSpan` + dim on the item text (design choice). |
| TABLE_ROW | Optional `MonoSpan` for alignment (design choice) | M | Keep off by default. |
| FRONT_MATTER, HTML_BLOCK, HTML_INLINE, ENTITY | `MarkerSpan(dim)` or nothing | A | |

The whole document also carries **one** `HangRoomSpan(H)` (`LeadingMarginSpan` + `UpdateLayout`, `SPAN_INCLUSIVE_INCLUSIVE`, `[0,len]`). It is not an `MdSpan`, so reconcile never removes it. H = max over levels of the heading marker width (at least 3 em of body text).

### 5.3 Rules for span classes

1. **Custom classes only.** Extend `CharacterStyle` / `MetricAffectingSpan` or implement `LeadingMarginSpan` / `LineBackgroundSpan` directly. Do **not** extend `ForegroundColorSpan`, `StyleSpan` or other framework `ParcelableSpan`s. Parcelable spans are serialized into ClipData and extracted text, and TextView does extra bookkeeping for them.
2. Implement `interface MdSpan { val kind: Int; val arg: Int }` so reconcile can key them.
3. Mark every paragraph-level span (`LeadingMarginSpan`, `LineHeightSpan`, `LineBackgroundSpan` that changes geometry) with `UpdateLayout`. Otherwise DynamicLayout won't reflow when it is added or removed (`DynamicLayout.java:1316–1345` only checks `instanceof UpdateLayout`).
4. **Minimize metric-affecting spans.** One span per construct (HeadingSpan does size+weight). Markers are appearance-only. Color is never done with a metric span.
5. Flags: `Spanned.SPAN_EXCLUSIVE_EXCLUSIVE` for everything except `HangRoomSpan` (`SPAN_INCLUSIVE_INCLUSIVE`). Never use `SPAN_PARAGRAPH`: it throws if the boundaries aren't at paragraph starts after edits. Paragraph styles only need to overlap the paragraph.
6. The span object never holds document offsets. Offsets live in the Editable.
7. Theme changes (dark mode, font size) do not require new spans if the spans read colors and sizes from a shared mutable `EditorStyle` object. Mutate it, then force one full reflow (§6.4 `reflowAll()`). That is about 100–250 ms at 300k, but only on theme or size changes.

### 5.4 Incremental restyle algorithm

1. `TextWatcher.onTextChanged(s, start, before, count)`: this runs inside `replace()`, after the text moved. Call `tokenizer.applyEdit(start, before, count, s)` and `scheduleRestyle()` (one `Choreographer.postFrameCallback` per frame). Nothing else, and **no span changes here**.
2. Frame callback `doFrame`. It runs in `CALLBACK_ANIMATION`, before `CALLBACK_TRAVERSAL`, so the restyled text is what gets drawn in this same frame.
   1. `val deadline = now + 4 ms`.
   2. Loop: `range = tokenizer.rescan(maxLines = 256)`. If non-null, `reconcile(range)`. Stop when `!tokenizer.pendingRescan || now > deadline`. If work remains, post another frame callback.
   3. Visible-first variant for cascades: if the pending range is huge, reconcile `[firstVisibleLine-50, lastVisibleLine+50]` first. The tokenizer already knows the new spans for all scanned lines. Then continue from the cursor outward. An offscreen line styled a few frames later is invisible to the user, but content *above* the viewport that changes height shifts what's visible. Accept it (only fence toggles cause it). Or pin the viewport by adjusting `scrollY` by the height delta of lines above `firstVisibleLine`, measured before and after reconcile.
3. `reconcile(fromLine, toLine)`:
   - a = lineStart(from), b = lineEnd(to) (exclusive of `\n`).
   - Existing: `editable.getSpans(a, b, MdSpan::class.java)`. Widen a/b to the union of existing spans that overlap and extend outside (possible after a `\n` was inserted inside a span). Then re-fetch the wanted spans for the widened line range.
   - Wanted: `tokenizer.spansForLines(from', to')` mapped to span specs `(class, arg, start, end)`.
   - Key both sides by `(kind, arg, start, end)`. Remove existing spans not wanted. Add wanted spans not existing (new objects).
   - **Do NOT wrap reconcile in `beginBatchEdit()`/`endBatchEdit()`.** Inside a batch, every span change sets `ims.mContentChanged` (`TextView.java:13092–13100`). Then `endBatchEdit` → `Editor.finishBatchEdit` → `TextView.updateAfterEdit()` (`Editor.java:1917–1918`), which calls `bringPointIntoView(cursor)` (`TextView.java:12998`) and would yank the viewport back to the caret whenever a cascade chunk is applied while the user is scrolled away. Markor's source warns about this too. Without a batch, each span change just calls `invalidate()` + `checkForResize()`, which are idempotent per frame. Batch edits remain correct around *text* edits we make (toolbar formatting, undo, task toggles), where scrolling to the caret is wanted.
4. Never touch spans that aren't `MdSpan`: selection, composing (`BaseInputConnection` `COMPOSING`), `SuggestionSpan`, `SpellCheckSpan`, and `HangRoomSpan`.
5. Opening a document (the big batch):
   - On `Dispatchers.Default`: read the text, `tokenizer.setText(text)`, build `SpannableStringBuilder(text)`, and set all spans (no watchers attached, so this is cheap).
   - On main: `editText.setText(ssb, TextView.BufferType.EDITABLE)`. `MdEditableFactory.newEditable(ssb)` copies the spans (O(N)), and TextView builds a DynamicLayout for the whole text. That is 0.1–0.3 s at 300k on the emulator and is unavoidable with EditText. `setText` must never be called for anything else (it resets undo, IME, scroll).
   - Guard: if the view's text changed between the snapshot and `setText` (it can't, since it's a new document), discard.

### 5.5 Selection toolbar (custom, Compose)

Suppression, verified on the emulator:
- `customSelectionActionModeCallback = object : ActionMode.Callback { onCreateActionMode → menu.clear(); true; onPrepareActionMode → menu.clear(); true; … }`.
- The system keeps the selection, both handles and the IME. The floating toolbar has zero items and draws nothing.
- Why `onPrepareActionMode` must clear too:
  - `Editor.TextActionModeCallback.onCreateActionMode` (`Editor.java:4741–4765`) populates Cut/Copy/Paste/Share/Select all *before* calling us (4747). It adds PROCESS_TEXT items (e.g. Translate) *after* we return true (`canProcessText()` branch).
  - Its `onPrepareActionMode` (4827–4836) adds the select-all, replace and smart-selection assist items, then calls our `onPrepareActionMode` **last**.
  - So clearing in our `onPrepareActionMode` removes everything, including items added later when a smart-selection result re-invalidates the mode.
  - The emulator had no PROCESS_TEXT apps installed, so that specific path was source-verified only.
- Do **not** return false. `Editor.java:4751–4755` does `Selection.setSelection(text, selectionEnd)`, which dismisses the selection.
- Do **not** return null from `startActionMode`. `Editor.startActionModeInternal` (2623–2665) then returns false, and the selection handles are never shown (verified screenshot).
- The insertion action mode (the "Paste / Select all" pill when tapping the caret handle) is left as the system default. Our toolbar is selection-only, per the requirement.

Our toolbar:
- **Visibility.** `visible = hasFocus() && selectionStart != selectionEnd`, driven by `onSelectionChanged`, `onFocusChanged` and `onScrollChanged`. It also shows for hardware-keyboard selections, which have no action mode.
- **Drag hiding.** Hide while the selection is being dragged: if `onSelectionChanged` fired less than 150 ms ago, hide, then re-show 150 ms after the last change. This mimics the system.
- **Anchor rect.** Use `layout.getSelectionPath(s, e, path)` → `path.computeBounds(rectF, true)`, offset by `(totalPaddingLeft - scrollX, totalPaddingTop - scrollY)`.
  - The rect is in EditText coordinates. The Compose overlay sits in the same `Box` as the `AndroidView`, so the coordinates are identical. Otherwise convert via `getLocationInWindow`.
  - Place the toolbar 8dp above `rect.top`. If that is < 0, place it below `rect.bottom + handleHeight (~24dp)`. If the selection's top is offscreen, clamp to the top of the viewport. Horizontally, center on the rect and clamp to the parent.
- **Actions.**
  - Clipboard and selection: Cut `onTextContextMenuItem(android.R.id.cut)`, Copy (`copy`), Paste (`paste`, which our override maps to plain text), Select all (`selectAll`).
  - Formatting: Bold, Italic, Strike, Code, Link, Heading cycle, Quote, List, Task. The actual edit logic is the markdown track's `SmartEdit`.
  - Apply the edit in a batch edit and re-set the selection over the inner text.
  - UNVERIFIED: whether the system handles stay visible after a programmatic replace. The toolbar doesn't depend on them.
- The toolbar composable must not be focusable (plain `Modifier.clickable` and no `focusable()`), so the EditText keeps focus and the IME stays up. Use an in-tree overlay (`Box` + `Modifier.offset { }`), not `Popup`: no extra window and no focus changes.

### 5.6 Undo/redo

Platform facts (API 37):
- `Editor` has an `UndoManager` and `UndoInputFilter` (`Editor.java:238` `mAllowUndo = true`, 7565 `UndoInputFilter`).
- `TextView.onKeyShortcut` maps Ctrl+Z → `ID_UNDO` and Ctrl+Y / Ctrl+Shift+Z → `ID_REDO` (`TextView.java:13695–13731`), but only `if (canUndo())`, which is package-private.
- `onTextContextMenuItem(android.R.id.undo/redo)` is public.
- Merging: "Merge insertions that are contiguous even when it's frozen" (`Editor.java:7907`). A whole typed paragraph becomes one undo step.
- Edits made inside a TextWatcher are force-merged into the previous op (`isInTextWatcher`, 7772).
- There is no public `canUndo` (needed to enable toolbar buttons), no time or word grouping, and the history is not persisted.

Decision: set `android:allowUndo=false` via the widget's defStyleRes, and implement our own `UndoManager`:
- It is a `TextWatcher`. In `beforeTextChanged` capture `old = substring(start, start+count)` and `selBefore`. In `onTextChanged` capture `new = substring(start, start+count)`.
- Record `Edit(start, old, new, selBefore, selAfter, time)`.
- Merge the new edit into the top one when: same type (insert with insert, delete with delete); contiguous; ≤ 1500 ms apart; not crossing a word boundary (break the group when a whitespace-to-non-whitespace transition starts a new word, like Markor's char-class merge); and not flagged `hardBreak` (cursor jump, focus loss, toolbar action, paste, smart-edit).
- Edits made while `applyingUndo` are ignored.
- Programmatic groups (toolbar format = 2 inserts; smart list continuation) use `undo.group { … }` to become one step.
- Cap at 1,000 steps or about 2 MB of strings.
- Keys: our `onKeyShortcut` override handles Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y first. Since `canUndo()` is false with allowUndo=false, TextView wouldn't handle them anyway.
- Undo restores text via `editable.replace` inside a batch edit plus `setSelection`. The restyle runs through the normal watcher path.

### 5.7 Focus Mode

- Modes: `Off | Sentence | Paragraph`. The focus range is computed on `onSelectionChanged` (cheap, local):
  - Paragraph = scan for `\n` from the cursor, bounded to ±10k chars.
  - Sentence = `android.icu.text.BreakIterator.getSentenceInstance(ULocale.getDefault())` over the **paragraph substring** only (`bi.setText(paragraphString)`, `preceding`/`following`).
- Drawing: `onDraw` → `super.onDraw(canvas)`, then overlay:
  - `canvas.save()`, `translate(totalPaddingLeft, totalPaddingTop)`, `clipOutPath(focusPath)`.
  - `drawRect(visible area, dimPaint)`, where `dimPaint.color = background @ 60–70% alpha`.
  - `restore()`.
- **Verified**: text outside the focus fades toward the background, and dim markers get dimmer, as in iA.
- No spans and no relayout. A cursor move only rebuilds the path (`Layout.getSelectionPath`, `Layout.java:3132`) and calls `invalidate()`. The Editor's cached text display lists are reused.
- Caret and selection highlight are drawn inside the focused range. If the user selects across the boundary, extend the focus range to the selection.

### 5.8 Typewriter scrolling

- Override `bringPointIntoView(offset: Int, requestRectWithoutFocus: Boolean)` (`TextView.java:11935`). The one-arg overload (11916) delegates to it, and TextView's internal callers use the one-arg overload (8834 onPreDraw, 11831 onLayout, 12998).
- When typewriter is on, and after the `isLayoutRequested` guard: target `scrollY = totalPaddingTop + lineCenter(caretLine) − 0.45 × height`, clamped to `[0, layout.height + totalPaddingTop + totalPaddingBottom − height]`. Animate with a 150 ms `ValueAnimator` that calls `scrollTo(scrollX, v)` and cancels on new requests or on touch down. Return true if a scroll happened.
- **Scroll room.** Top and bottom padding must allow centering the first and last lines. **Do not tie them to the view height.** `TextView.setPadding` calls `nullLayouts()` on any change (`TextView.java:3792–3798`), which means a full relayout of the document (0.1–0.3 s at 300k). The IME showing or hiding (imePadding shrinks the view) must not trigger that.
  - Use a fixed padding = `0.5 × maxWindowHeight` from `WindowManager.currentWindowMetrics.bounds.height()` (API 30+), recomputed only when the width or the typewriter setting changes.
  - When typewriter is off, use normal padding (e.g. 24dp top, 50% bottom).
- **IME.** Compose `imePadding()` shrinks the AndroidView. In `onSizeChanged(h < oldh)`, if focused, call `post { bringPointIntoView(selectionEnd) }` so the caret stays visible (or re-centers, in typewriter mode).
- **Interaction.** User flings are not fought. Re-centering happens only when the caret moves or text changes, which are TextView's own `bringPointIntoView` triggers.

### 5.9 Hanging markers and the centered column

- **Column.** In `onMeasure`, compute `contentWidth = min(availableWidth − 2·minGutter, maxLineChars × avgCharWidth)`, where `avgCharWidth = paint.measureText("abcdefghijklmnopqrstuvwxyz")/26` at the body size and `maxLineChars` comes from the design track (~64–72).
  - `sidePadding = (availableWidth − contentWidth)/2`.
  - `paddingLeft = sidePadding − H`: `HangRoomSpan(H)` puts the margin inside the Layout so the markers aren't clipped. `paddingRight = sidePadding`.
  - Call `setPadding` only if the values changed. It is legal inside `onMeasure` before `super.onMeasure`.
- **Why H lives in a span, not in padding.** `TextView.onDraw` clips to the padded box (`clipLeft = compoundPaddingLeft + scrollX`, `canvas.clipRect(...)`, `TextView.java:9377–9391`). Text drawn left of the padding is invisible.
- **Why the negative margin works.** `Layout` sums `getLeadingMargin` over all LeadingMarginSpans of the paragraph, with no clamping (`Layout.java:737–745` for drawing, 3269–3290 for `getParagraphLeadingMargin`). So H + (−w) = H − w ≥ 0. Verified visually (`hang-top-s.png`).
- The same mechanism can hang `>` and list bullets if the design wants it.

### 5.10 Task checkbox taps

- In `onTouchEvent`:
  - On DOWN, record `pendingTask = taskAt(x, y)`. It uses `getOffsetForPosition(x, y)` (`TextView.java:15959`), then `getSpans(off-3, off+3, TaskSpan)`, then the horizontal check: x within `[primaryHorizontal(start), primaryHorizontal(end)]` ± 8dp, offset by padding and scroll.
  - On UP, if within touch slop and under the long-press timeout: toggle `[ ]`↔`[x]` via `editable.replace(start+1, start+2, …)` inside a batch edit and an undo group. Then send `ACTION_CANCEL` to `super.onTouchEvent` so the tap doesn't move the caret or open the IME, call `performClick()`, and return true.
- Accessibility: expose a custom accessibility action later (not MVP).

### 5.11 Spell-check / autocorrect / IME coexistence

- Recommended flags:
  - `inputType = TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_MULTI_LINE | TYPE_TEXT_FLAG_CAP_SENTENCES | TYPE_TEXT_FLAG_AUTO_CORRECT`. A setting can add `TYPE_TEXT_FLAG_NO_SUGGESTIONS`.
  - `imeOptions = IME_FLAG_NO_EXTRACT_UI | IME_FLAG_NO_FULLSCREEN`. Landscape extract mode copies text and breaks our styling view.
- System spans (`SuggestionSpan` underlines, `SpellCheckSpan`, composing) are never touched by reconcile (MdSpan filter). They are shifted cheaply thanks to MdEditable.
- Never call `setText` or `clearSpans` during editing. Programmatic edits go through `editable.replace` inside a batch edit.
- `restartInput` isn't needed for small edits away from the composing region. After replacing a range that intersects composition (rare), call `BaseInputConnection.removeComposingSpans(editable)` first.
- Accessibility cost: when any accessibility service is enabled, `TextView.ChangeWatcher.beforeTextChanged` copies the **whole** text (`mBeforeText = mTransformed.toString()`, `TextView.java:16717–16718`). That is an O(n) allocation per keystroke (about 600 KB at 300k). It's unavoidable, and a risk to note for the QA device matrix.

### 5.12 Other widget settings

- Keep EditText's defaults `BREAK_STRATEGY_SIMPLE` and `HYPHENATION_FREQUENCY_NONE` (`TextView.java:1238–1239`). High-quality breaking and hyphenation re-wrap the line while typing and cost more.
- Line spacing: `setLineSpacing(0f, 1.5f–1.6f)` (the value comes from the design track). The caret excludes spacing (`Editor.java:2460–2461` `getLineBottom(line, includeLineSpacing=false)`) and uses the drawable's intrinsic width (2891–2896).
- `isSaveEnabled = false`, as decided by the platform track. We persist ourselves.
- `setHorizontallyScrolling(false)`, `isVerticalScrollBarEnabled = true`, `background = null`, `gravity = TOP or START`.
- Copy/cut override: `ClipData.newPlainText(null, TextUtils.substring(text, min, max))` (plain `String`). Stock TextView puts the *spanned* subsequence (`TextView.java` `ID_COPY` → `getTransformedText(min,max)`).
- Paste override: `android.R.id.paste` → `super.onTextContextMenuItem(android.R.id.pasteAsPlainText)`. Ctrl+Shift+V already maps to plain (`TextView.java:13731`).

---

## 6. Code sketches (Kotlin, API 36+, platform classes only)

These are sketches with correct API usage. Names prefixed `Md` are ours.

### 6.1 MdEditable (production version of the bench's `FastEditable2`)

```kotlin
package app.mdwriter.editor

import android.text.Editable
import android.text.Selection
import android.text.SpanWatcher
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.view.inputmethod.BaseInputConnection

/**
 * SpannableStringBuilder that does not broadcast "span shifted" events to SpanWatchers after an
 * edit, except for broadcasts whose range touches the selection or composing region. See §4 of
 * research/editor-engine.md: without this, DynamicLayout re-lays out every styled paragraph after
 * the cursor on each keystroke (O(document)).
 */
class MdEditable(source: CharSequence) : SpannableStringBuilder(source) {
    private var depth = 0
    private var silent = false
    private var selA = -1; private var selB = -1; private var compA = -1; private var compB = -1

    /** Priority 0 => the LAST TextWatcher notified (TextView=100, DynamicLayout=128). */
    private val phaseMarker = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (depth != 1) return
            val self = this@MdEditable
            selA = Selection.getSelectionStart(self); selB = Selection.getSelectionEnd(self)
            compA = BaseInputConnection.getComposingSpanStart(self)
            compB = BaseInputConnection.getComposingSpanEnd(self)
            silent = true // everything after this inside replace() is sendToSpanWatchers()
        }
    }

    init { setSpan(phaseMarker, 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE) }

    override fun replace(start: Int, end: Int, tb: CharSequence, tbstart: Int, tbend: Int): SpannableStringBuilder {
        depth++
        try {
            return super.replace(start, end, tb, tbstart, tbend)
        } finally {
            depth--
            if (depth == 0) silent = false
        }
    }

    private fun touches(a: Int, b: Int) =
        selA in a..b || selB in a..b || (compA >= 0 && compA in a..b) || (compB >= 0 && compB in a..b)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getSpans(queryStart: Int, queryEnd: Int, kind: Class<T>?): Array<T> {
        if (silent && fastPathEnabled && kind === SpanWatcher::class.java && !touches(queryStart, queryEnd)) {
            return NO_WATCHERS as Array<T>
        }
        return super.getSpans(queryStart, queryEnd, kind)
    }

    companion object {
        private val NO_WATCHERS: Array<SpanWatcher> = emptyArray()
        /** Debug kill-switch (Settings > Developer or BuildConfig). */
        @Volatile @JvmStatic var fastPathEnabled = true
    }
}

object MdEditableFactory : Editable.Factory() {
    override fun newEditable(source: CharSequence): Editable = MdEditable(source)
}
```

### 6.2 Spans and FontSet

```kotlin
package app.mdwriter.editor.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.MetricAffectingSpan
import android.text.style.UpdateAppearance
import android.text.style.UpdateLayout

/** Marker for spans owned by the editor; reconcile only ever removes these. */
interface MdSpan { val kind: Int; val arg: Int }

class FontSet(
    val regular: Typeface, val italic: Typeface, val bold: Typeface, val boldItalic: Typeface, val mono: Typeface,
) {
    fun isBold(t: Typeface?) = t === bold || t === boldItalic
    fun isItalic(t: Typeface?) = t === italic || t === boldItalic
    fun of(bold: Boolean, italic: Boolean): Typeface = when {
        bold && italic -> boldItalic; bold -> this.bold; italic -> this.italic; else -> regular
    }
}

/** Mutable, shared by all spans: theme/size changes mutate this + one full reflow (§6.4). */
class EditorStyle(
    var fonts: FontSet,
    var baseTextPx: Float,
    var headingScale: FloatArray,      // index 1..6
    var dimColor: Int, var quoteColor: Int, var codeBg: Int, var markBg: Int, var quoteBarColor: Int,
    var quoteIndentPx: Int, var quoteBarPx: Int, var listIndentPx: Int,
)

class HeadingSpan(override val arg: Int, private val s: EditorStyle) : MetricAffectingSpan(), MdSpan {
    override val kind get() = K_HEADING
    private fun apply(tp: TextPaint) {
        tp.textSize = s.baseTextPx * s.headingScale[arg]
        tp.typeface = s.fonts.of(bold = true, italic = s.fonts.isItalic(tp.typeface))
    }
    override fun updateMeasureState(tp: TextPaint) = apply(tp)
    override fun updateDrawState(tp: TextPaint) = apply(tp)
}

class StrongSpan(private val s: EditorStyle) : MetricAffectingSpan(), MdSpan {
    override val kind get() = K_STRONG; override val arg get() = 0
    private fun apply(tp: TextPaint) { tp.typeface = s.fonts.of(true, s.fonts.isItalic(tp.typeface)) }
    override fun updateMeasureState(tp: TextPaint) = apply(tp)
    override fun updateDrawState(tp: TextPaint) = apply(tp)
}

class EmphasisSpan(private val s: EditorStyle) : MetricAffectingSpan(), MdSpan {
    override val kind get() = K_EMPH; override val arg get() = 0
    private fun apply(tp: TextPaint) { tp.typeface = s.fonts.of(s.fonts.isBold(tp.typeface), true) }
    override fun updateMeasureState(tp: TextPaint) = apply(tp)
    override fun updateDrawState(tp: TextPaint) = apply(tp)
}

class CodeSpan(private val s: EditorStyle) : MetricAffectingSpan(), MdSpan {
    override val kind get() = K_CODE; override val arg get() = 0
    override fun updateMeasureState(tp: TextPaint) { tp.typeface = s.fonts.mono }
    override fun updateDrawState(tp: TextPaint) { tp.typeface = s.fonts.mono; if (s.codeBg != 0) tp.bgColor = s.codeBg }
}

class CodeBlockSpan(private val s: EditorStyle) : MetricAffectingSpan(), LineBackgroundSpan, MdSpan {
    override val kind get() = K_CODE_BLOCK; override val arg get() = 0
    override fun updateMeasureState(tp: TextPaint) { tp.typeface = s.fonts.mono }
    override fun updateDrawState(tp: TextPaint) { tp.typeface = s.fonts.mono }
    override fun drawBackground(
        canvas: Canvas, paint: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int,
    ) {
        if (s.codeBg == 0) return
        val old = paint.color
        paint.color = s.codeBg
        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
        paint.color = old
    }
}

class MarkerSpan(private val s: EditorStyle) : CharacterStyle(), UpdateAppearance, MdSpan {
    override val kind get() = K_MARKER; override val arg get() = 0
    override fun updateDrawState(tp: TextPaint) { tp.color = s.dimColor }
}

class StrikeSpan : CharacterStyle(), UpdateAppearance, MdSpan {
    override val kind get() = K_STRIKE; override val arg get() = 0
    override fun updateDrawState(tp: TextPaint) { tp.isStrikeThruText = true }
}

class MarkSpan(private val s: EditorStyle) : CharacterStyle(), UpdateAppearance, MdSpan {
    override val kind get() = K_MARK; override val arg get() = 0
    override fun updateDrawState(tp: TextPaint) { tp.bgColor = s.markBg }
}

class QuoteTextSpan(private val s: EditorStyle) : CharacterStyle(), UpdateAppearance, MdSpan {
    override val kind get() = K_QUOTE_TEXT; override val arg get() = 0
    override fun updateDrawState(tp: TextPaint) { tp.color = s.quoteColor }
}

/** Whole-document hang room so hanging markers are drawn INSIDE the layout (TextView clips padding). */
class HangRoomSpan(@Volatile var widthPx: Int) : LeadingMarginSpan, UpdateLayout {
    override fun getLeadingMargin(first: Boolean) = widthPx
    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?,
    ) = Unit
}

/** Negative margin on a heading's first line: "## " hangs in the HangRoom, heading text aligns with body. */
class HeadingHangSpan(override val arg: Int /* marker width px */) : LeadingMarginSpan, UpdateLayout, MdSpan {
    override val kind get() = K_HEADING_HANG
    override fun getLeadingMargin(first: Boolean) = if (first) -arg else 0
    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?,
    ) = Unit
}

class QuoteSpan(override val arg: Int /* depth */, private val s: EditorStyle) : LeadingMarginSpan, UpdateLayout, MdSpan {
    override val kind get() = K_QUOTE
    override fun getLeadingMargin(first: Boolean) = arg * s.quoteIndentPx
    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?,
    ) {
        if (s.quoteBarPx <= 0) return
        val oldStyle = p.style; val oldColor = p.color
        p.style = Paint.Style.FILL; p.color = s.quoteBarColor
        for (d in 0 until arg) {
            val bx = x + dir * (d * s.quoteIndentPx)
            c.drawRect(bx.toFloat(), top.toFloat(), (bx + dir * s.quoteBarPx).toFloat(), bottom.toFloat(), p)
        }
        p.style = oldStyle; p.color = oldColor
    }
}

class ListIndentSpan(private val firstPx: Int, private val restPx: Int) : LeadingMarginSpan, UpdateLayout, MdSpan {
    override val kind get() = K_LIST; override val arg get() = restPx  // arg participates in the reconcile key
    override fun getLeadingMargin(first: Boolean) = if (first) firstPx else restPx
    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?,
    ) = Unit
}

/**
 * Hit-test marker only. NOT a NoCopySpan: the SpannableStringBuilder copy constructor skips NoCopySpans
 * (SpannableStringBuilder.java:86), and MdEditableFactory.newEditable(ssb) is such a copy, so the spans
 * built off-main in buildStyled() would be lost on setText(). Clipboard hygiene comes from the plain-String copy override.
 */
class TaskSpan(override val arg: Int /* 1 = checked */) : MdSpan {
    override val kind get() = K_TASK
}
```

Kind constants (`K_*`) are Ints in `spans/Kinds.kt`. `SpanFactory.create(md: MdSpan(kind,start,end,arg), text, style, paint): List<Pair<Any,IntRange>>` implements the §5.2 table. For `HeadingHangSpan` the width is measured like this:

```kotlin
val hp = TextPaint(paint).apply { textSize = style.baseTextPx * style.headingScale[level]; typeface = style.fonts.of(true, false) }
val w = hp.measureText(text, markerStart, markerEnd).roundToInt().coerceAtMost(hangRoomPx)
```

### 6.3 Restyler (dirty tracking, frame scheduling, reconcile)

```kotlin
class Restyler(
    private val view: MarkdownEditText,
    private val tokenizer: IncrementalMarkdown,        // from :core:markdown (contract in §5.1)
    private val factory: SpanFactory,
) : android.text.TextWatcher, android.view.Choreographer.FrameCallback {
    private var scheduled = false
    var suspended = false  // true while setText() installs a new document

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        if (suspended) return
        tokenizer.applyEdit(start, before, count, s)   // O(changed lines), no whole-text diff
        schedule()
    }
    override fun afterTextChanged(s: android.text.Editable) = Unit

    fun schedule() {
        if (!scheduled) { scheduled = true; android.view.Choreographer.getInstance().postFrameCallback(this) }
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        val deadline = System.nanoTime() + 4_000_000L
        // NO beginBatchEdit here: endBatchEdit() would call bringPointIntoView(cursor) (see §5.4)
        while (tokenizer.pendingRescan && System.nanoTime() < deadline) {
            val r = tokenizer.rescan(maxLines = 256) ?: continue
            reconcile(r.first, r.last)
        }
        if (tokenizer.pendingRescan) schedule()
        view.onStyled()  // e.g. refresh focus path / toolbar anchor
    }

    private data class Key(val kind: Int, val arg: Int, val start: Int, val end: Int)

    fun reconcile(fromLine: Int, toLine: Int) {
        val e = view.text ?: return
        var a = tokenizer.lineStart(fromLine); var b = tokenizer.lineEnd(toLine)
        val existing = e.getSpans(a, b, MdSpan::class.java)
        // widen to spans that stick out (e.g. '\n' typed inside a span)
        for (sp in existing) { a = minOf(a, e.getSpanStart(sp)); b = maxOf(b, e.getSpanEnd(sp)) }
        val l0 = tokenizer.lineIndexOf(a); val l1 = tokenizer.lineIndexOf(b)
        val want = HashMap<Key, Any>()
        for (md in tokenizer.spansForLines(l0, l1)) for ((obj, range) in factory.create(md, e)) {
            val k = Key((obj as MdSpan).kind, obj.arg, range.first, range.last + 1)
            want.putIfAbsent(k, obj)
        }
        val a2 = tokenizer.lineStart(l0); val b2 = tokenizer.lineEnd(l1)
        for (sp in e.getSpans(a2, b2, MdSpan::class.java)) {
            val k = Key(sp.kind, sp.arg, e.getSpanStart(sp), e.getSpanEnd(sp))
            if (want.remove(k) == null) e.removeSpan(sp)
        }
        for ((k, obj) in want) e.setSpan(obj, k.start, k.end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
```

Notes:
- `HashMap<Key>` is fine for paragraph-sized work. For cascades, the 256-line slices bound it.
- The two `getSpans` passes are cheap (interval tree).
- `view.text` is the `MdEditable`.
- The Restyler's TextWatcher is added with `addTextChangedListener`. TextView forwards to its listeners from its own ChangeWatcher, which runs before the MdEditable phase marker.

### 6.4 Opening a document and full reflow

```kotlin
// ViewModel / controller (coroutines)
suspend fun buildStyled(text: String, tokenizer: IncrementalMarkdown, factory: SpanFactory): SpannableStringBuilder =
    withContext(Dispatchers.Default) {
        tokenizer.setText(text)                      // full scan (bench: ~25 ms / 300k for a regex styler)
        val ssb = SpannableStringBuilder(text)       // no watchers attached yet => setSpan is cheap
        for (md in tokenizer.spansForLines(0, tokenizer.lineCount - 1))
            for ((obj, r) in factory.create(md, ssb)) ssb.setSpan(obj, r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(factory.hangRoom, 0, ssb.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        ssb
    }

// main thread
fun MarkdownEditText.install(ssb: SpannableStringBuilder, selection: Int, scrollY: Int) {
    restyler.suspended = true
    setText(ssb, TextView.BufferType.EDITABLE)       // factory => MdEditable copy; DynamicLayout full build
    restyler.suspended = false
    setSelection(selection.coerceIn(0, length()))
    post { scrollTo(0, scrollY) }
    undo.clear()
}

/**
 * Theme/font-size change: spans read EditorStyle, so just force ONE full reflow.
 * DynamicLayout.onSpanChanged reflows BOTH the old and the new range (DynamicLayout.java:1363-1368), and
 * SSB.setSpan on an attached span always broadcasts a change, even with equal bounds (SSB.java:736-757).
 * So toggle a permanent trigger between [0,0] and [0,len]: each call costs one full + one empty reflow.
 * (A set+remove pair would cost two full reflows.)
 */
private val reflowTrigger = object : android.text.style.UpdateLayout {}
private var triggerWide = false
fun MarkdownEditText.reflowAll() {
    val e = text ?: return
    triggerWide = !triggerWide
    e.setSpan(reflowTrigger, 0, if (triggerWide) e.length else 0, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    invalidate()
}
```
(In the real class, `reflowTrigger` and `triggerWide` are members of `MarkdownEditText`.)

Ownership caveat: the tokenizer is thread-confined. It is created fresh per document, used on Default for `setText`, then handed to main. Keep that hand-off explicit, with no concurrent use.

### 6.5 MarkdownEditText core configuration

```kotlin
class MarkdownEditText(context: Context) :
    EditText(context, null, 0, R.style.Widget_MdWriter_Editor) {   // style: android:allowUndo=false

    val undo = MdUndoManager(this)
    lateinit var restyler: Restyler
    var accent: Int = 0xFF1AA3FF.toInt()        // design track decides the exact iA blue
        set(v) { field = v; applyAccent() }

    init {
        setEditableFactory(MdEditableFactory)                      // BEFORE any setText
        background = null
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        setHorizontallyScrolling(false)
        isVerticalScrollBarEnabled = true
        isSaveEnabled = false
        customSelectionActionModeCallback = HideSystemSelectionToolbar
        addTextChangedListener(undo)
        applyAccent()
    }

    private fun applyAccent() {
        val d = resources.displayMetrics.density
        setTextCursorDrawable(GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(accent)
            setSize((2 * d).roundToInt(), 1)        // Editor stretches height to the line (without spacing)
        })
        highlightColor = (accent and 0x00FFFFFF) or (0x4D shl 24)   // ~30% alpha
        textSelectHandle?.let { setTextSelectHandle(it.mutate().apply { setTint(accent) }) }
        textSelectHandleLeft?.let { setTextSelectHandleLeft(it.mutate().apply { setTint(accent) }) }
        textSelectHandleRight?.let { setTextSelectHandleRight(it.mutate().apply { setTint(accent) }) }
    }

    // ---- plain-text clipboard ----
    override fun onTextContextMenuItem(id: Int): Boolean = when (id) {
        android.R.id.paste -> super.onTextContextMenuItem(android.R.id.pasteAsPlainText)
        android.R.id.copy, android.R.id.cut -> {
            val min = minOf(selectionStart, selectionEnd); val max = maxOf(selectionStart, selectionEnd)
            if (min == max) false else {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText(null, TextUtils.substring(text, min, max)))
                if (id == android.R.id.cut) { undo.hardBreak(); text!!.delete(min, max) }
                true
            }
        }
        android.R.id.undo -> { undo.undo(); true }
        android.R.id.redo -> { undo.redo(); true }
        else -> super.onTextContextMenuItem(id)
    }

    // ---- hardware keyboard ----
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        val ctrl = event.hasModifiers(KeyEvent.META_CTRL_ON)
        val ctrlShift = event.hasModifiers(KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
        when {
            ctrl && keyCode == KeyEvent.KEYCODE_Z -> { undo.undo(); return true }
            (ctrlShift && keyCode == KeyEvent.KEYCODE_Z) || (ctrl && keyCode == KeyEvent.KEYCODE_Y) -> { undo.redo(); return true }
            ctrl && keyCode == KeyEvent.KEYCODE_B -> { onFormat?.invoke(Format.Bold); return true }
            ctrl && keyCode == KeyEvent.KEYCODE_I -> { onFormat?.invoke(Format.Italic); return true }
        }
        return super.onKeyShortcut(keyCode, event)
    }
    var onFormat: ((Format) -> Unit)? = null
}
```

`res/values/styles.xml`:

```xml
<style name="Widget.MdWriter.Editor" parent="android:Widget.Material.EditText">
    <item name="android:allowUndo">false</item>
    <item name="android:background">@null</item>
    <item name="android:textColorHighlight">#4D1AA3FF</item>
</style>
```

The styles may also set `android:textCursorDrawable` and handles. Code sets them anyway to follow the theme accent at runtime.

### 6.6 Centered column, hang room, typewriter padding (`onMeasure` / `onSizeChanged`)

```kotlin
var maxLineChars = 66            // design track
var minGutterPx = 16.dp
var typewriter = false
    set(v) { field = v; requestLayout() }
lateinit var hangRoom: HangRoomSpan      // same instance put into the document by buildStyled()

override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val avail = MeasureSpec.getSize(widthMeasureSpec)
    if (avail > 0) {
        val avgChar = paint.measureText("abcdefghijklmnopqrstuvwxyz") / 26f
        val content = minOf(avail - 2 * minGutterPx, (maxLineChars * avgChar).roundToInt())
        val side = (avail - content) / 2
        val h = hangRoom.widthPx
        val winH = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height()
        val top = if (typewriter) (winH * 0.45f).roundToInt() else 24.dp
        val bottom = if (typewriter) (winH * 0.55f).roundToInt() else (winH * 0.4f).roundToInt()
        val left = (side - h).coerceAtLeast(0)
        if (left != paddingLeft || side != paddingRight || top != paddingTop || bottom != paddingBottom) {
            setPadding(left, top, side, bottom)      // nullLayouts(): only happens when width/mode changes
        }
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
}

override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (hasFocus() && h != oldh) post { bringPointIntoView(selectionEnd) }   // IME show/hide
}
```

### 6.7 Typewriter scrolling

```kotlin
private var twAnim: ValueAnimator? = null

override fun bringPointIntoView(offset: Int, requestRectWithoutFocus: Boolean): Boolean {
    val l = layout
    if (!typewriter || l == null || isLayoutRequested) return super.bringPointIntoView(offset, requestRectWithoutFocus)
    val line = l.getLineForOffset(offset.coerceIn(0, length()))
    val center = totalPaddingTop + (l.getLineTop(line) + l.getLineBottom(line, false)) / 2
    val maxY = (l.height + totalPaddingTop + totalPaddingBottom - height).coerceAtLeast(0)
    val target = (center - height * 0.45f).roundToInt().coerceIn(0, maxY)
    if (target == scrollY) return false
    twAnim?.cancel()
    twAnim = ValueAnimator.ofInt(scrollY, target).apply {
        duration = 150
        interpolator = DecelerateInterpolator()
        addUpdateListener { scrollTo(scrollX, it.animatedValue as Int) }
        start()
    }
    return true
}

override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_DOWN) twAnim?.cancel()
    // task-toggle handling (§6.10) goes here too
    return super.onTouchEvent(event)
}
```

### 6.8 Focus Mode overlay

```kotlin
enum class FocusMode { Off, Sentence, Paragraph }
var focusMode = FocusMode.Off
    set(v) { field = v; updateFocusRange(); invalidate() }
var focusDimColor = 0xB3FFFFFF.toInt()          // = editor background with ~70% alpha (theme-dependent)
private var fStart = -1; private var fEnd = -1
private val focusPath = Path()
private val dimPaint = Paint()

override fun onSelectionChanged(selStart: Int, selEnd: Int) {
    super.onSelectionChanged(selStart, selEnd)
    if (focusMode != FocusMode.Off) { updateFocusRange(); invalidate() }
    selectionUi?.onSelectionChanged(selStart, selEnd)          // §6.9
}

private fun updateFocusRange() {
    val t = text ?: return
    val s = selectionStart.coerceAtLeast(0); val e = selectionEnd.coerceAtLeast(0)
    var ps = minOf(s, e); while (ps > 0 && t[ps - 1] != '\n') ps--
    var pe = maxOf(s, e); while (pe < t.length && t[pe] != '\n') pe++
    if (focusMode == FocusMode.Paragraph) { fStart = ps; fEnd = pe; return }
    val para = TextUtils.substring(t, ps, pe)
    if (para.isEmpty()) { fStart = ps; fEnd = pe; return }
    val bi = android.icu.text.BreakIterator.getSentenceInstance(android.icu.util.ULocale.getDefault())
    bi.setText(para)
    val lo = (minOf(s, e) - ps).coerceIn(0, para.length)
    val hi = (maxOf(s, e) - ps).coerceIn(0, para.length)
    // start of the sentence containing lo = last boundary <= lo
    val a = (if (lo >= para.length) bi.preceding(para.length) else bi.preceding(lo + 1)).coerceAtLeast(0)
    // end = first boundary > hi (or hi itself when a selection ends exactly on a boundary)
    val b = if (hi > a && bi.isBoundary(hi)) hi
            else bi.following(hi).let { if (it == android.icu.text.BreakIterator.DONE) para.length else it }
    fStart = ps + a; fEnd = ps + b
}

override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val l = layout ?: return
    if (focusMode == FocusMode.Off || fStart < 0) return
    focusPath.reset()
    l.getSelectionPath(fStart, fEnd, focusPath)
    dimPaint.color = focusDimColor
    canvas.save()
    canvas.translate(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
    canvas.clipOutPath(focusPath)
    canvas.drawRect(-totalPaddingLeft.toFloat(), (scrollY - totalPaddingTop).toFloat(),
        (width - totalPaddingLeft).toFloat() + scrollX, (scrollY + height).toFloat(), dimPaint)
    canvas.restore()
}
```

The sentence logic is a sketch. The plan should unit-test the boundary math on JVM with ICU4J, or on-device. Note that `getSelectionPath` covers only glyph areas per line, so the empty right part of the focused sentence's last line is dimmed. That's the intended iA look.

### 6.9 Selection toolbar: suppression and the Compose overlay

```kotlin
object HideSystemSelectionToolbar : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { menu.clear(); return true } // MUST be true
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { menu.clear(); return true }
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
    override fun onDestroyActionMode(mode: ActionMode) = Unit
}

/** Observable state for Compose. */
class SelectionUi(private val view: MarkdownEditText) {
    data class State(val visible: Boolean, val anchor: android.graphics.RectF)
    val state = kotlinx.coroutines.flow.MutableStateFlow(State(false, android.graphics.RectF()))
    private val path = android.graphics.Path()
    private val showRunnable = Runnable { publish(true) }

    fun onSelectionChanged(s: Int, e: Int) {
        view.removeCallbacks(showRunnable)
        if (s == e || !view.hasFocus()) { publish(false); return }
        publish(false)                       // hide while handles are being dragged
        view.postDelayed(showRunnable, 150)
    }
    fun onScrollOrLayout() { if (state.value.visible) publish(true) }

    private fun publish(visible: Boolean) {
        val l = view.layout
        val s = view.selectionStart; val e = view.selectionEnd
        if (!visible || l == null || s == e) { state.value = State(false, android.graphics.RectF()); return }
        path.reset(); l.getSelectionPath(minOf(s, e), maxOf(s, e), path)
        val r = android.graphics.RectF(); path.computeBounds(r, true)
        r.offset((view.totalPaddingLeft - view.scrollX).toFloat(), (view.totalPaddingTop - view.scrollY).toFloat())
        state.value = State(true, r)
    }
}
```

In `MarkdownEditText`: override `onScrollChanged(...)` and `onLayout(...)` to call `selectionUi?.onScrollOrLayout()`, and `onFocusChanged(...)` to call `onSelectionChanged(selectionStart, selectionEnd)`.

```kotlin
@Composable
fun EditorWithToolbar(controller: EditorController, modifier: Modifier = Modifier) {
    Box(modifier) {
        AndroidView(
            factory = { ctx -> controller.createView(ctx) },     // MarkdownEditText, created once
            modifier = Modifier.fillMaxSize(),
        )
        val st by controller.selectionUi.state.collectAsState()
        var boxSize by remember { mutableStateOf(IntSize.Zero) }
        var tbSize by remember { mutableStateOf(IntSize.Zero) }
        Spacer(Modifier.matchParentSize().onSizeChanged { boxSize = it })     // no pointer input: taps pass through
        if (st.visible) {
            val density = LocalDensity.current
            FormatToolbar(
                onAction = controller::onToolbarAction,          // cut/copy/paste/selectAll/bold/…
                modifier = Modifier
                    .onSizeChanged { tbSize = it }
                    .offset {
                        val gap = with(density) { 8.dp.roundToPx() }
                        val handle = with(density) { 28.dp.roundToPx() }
                        var y = st.anchor.top.toInt() - tbSize.height - gap
                        if (y < 0) y = st.anchor.bottom.toInt() + handle
                        val x = st.anchor.centerX().toInt() - tbSize.width / 2
                        IntOffset(
                            x.coerceIn(0, (boxSize.width - tbSize.width).coerceAtLeast(0)),
                            y.coerceIn(0, (boxSize.height - tbSize.height).coerceAtLeast(0)),
                        )
                    }
                    .graphicsLayer { alpha = if (tbSize == IntSize.Zero) 0f else 1f },   // hide the unmeasured first frame
            )
        }
    }
}
```
`Modifier.offset { }` moves both the drawing and the hit-test bounds. Don't make the toolbar fill the Box, or it would swallow taps meant for the editor.

`FormatToolbar` is a Row of icon buttons using `Modifier.clickable` (no `focusable()`), so the EditText keeps focus and the IME stays. `controller.onToolbarAction` does these things:
- For the clipboard and selection entries, calls `view.onTextContextMenuItem(android.R.id.cut | copy | paste | selectAll)`.
- For formatting entries, runs `view.beginBatchEdit(); undo.group { smartEdit.toggleBold(editable, selStart, selEnd) }; view.endBatchEdit()` and restores the selection.

### 6.10 Task checkbox tap

```kotlin
private var taskDown: TaskSpan? = null
private var downX = 0f; private var downY = 0f
private val slop = ViewConfiguration.get(context).scaledTouchSlop

private fun taskAt(x: Float, y: Float): TaskSpan? {
    val l = layout ?: return null
    val t = text ?: return null
    val off = getOffsetForPosition(x, y)
    val lx = x - totalPaddingLeft + scrollX
    val pad = 8 * resources.displayMetrics.density
    return t.getSpans((off - 3).coerceAtLeast(0), (off + 3).coerceAtMost(t.length), TaskSpan::class.java).firstOrNull {
        val a = l.getPrimaryHorizontal(t.getSpanStart(it)); val b = l.getPrimaryHorizontal(t.getSpanEnd(it))
        lx in (minOf(a, b) - pad)..(maxOf(a, b) + pad)
    }
}

// inside onTouchEvent(event):
when (event.actionMasked) {
    MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; taskDown = taskAt(event.x, event.y) }
    MotionEvent.ACTION_UP -> taskDown?.let { span ->
        taskDown = null
        if (abs(event.x - downX) < slop && abs(event.y - downY) < slop &&
            event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()) {
            val e = text!!; val s = e.getSpanStart(span)   // span covers "[ ]" / "[x]"
            beginBatchEdit(); undo.group { e.replace(s + 1, s + 2, if (span.arg == 1) " " else "x") }; endBatchEdit()
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.onTouchEvent(cancel); cancel.recycle()
            performClick()
            return true
        }
    }
    MotionEvent.ACTION_CANCEL -> taskDown = null
}
```

### 6.11 Own undo manager (sketch)

A step holds a list of ops. Typing runs extend the last op. Grouped programmatic edits, such as bold (2 inserts at different places), become one step with several ops.

```kotlin
class MdUndoManager(private val view: EditText) : TextWatcher {
    private class Op(val start: Int, var old: String, var new: String)
    private class Step(val ops: MutableList<Op>, val selBefore: Int, var selAfter: Int, var time: Long)

    private val undoStack = ArrayDeque<Step>()
    private val redoStack = ArrayDeque<Step>()
    private var applying = false
    private var groupDepth = 0
    private var groupStep: Step? = null
    private var breakNext = true
    private var pendingOld = ""
    private var pendingSel = 0
    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()
    var onChanged: (() -> Unit)? = null        // toolbar enables Undo/Redo from canUndo/canRedo

    /** Call on caret jumps (onSelectionChanged not caused by typing), focus loss, paste, toolbar actions. */
    fun hardBreak() { breakNext = true }
    fun beginGroup() { if (groupDepth++ == 0) { groupStep = null; breakNext = true } }
    fun endGroup() { if (--groupDepth == 0) { groupStep = null; breakNext = true } }
    inline fun <T> group(block: () -> T): T { beginGroup(); try { return block() } finally { endGroup() } }
    fun clear() { undoStack.clear(); redoStack.clear(); breakNext = true; onChanged?.invoke() }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        if (applying) return
        pendingOld = TextUtils.substring(s, start, start + count)
        pendingSel = Selection.getSelectionStart(s)
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        if (applying) return
        val new = TextUtils.substring(s, start, start + count)
        val old = pendingOld
        val now = SystemClock.uptimeMillis()
        val selAfter = start + count
        redoStack.clear()
        if (groupDepth > 0) {
            val g = groupStep ?: Step(mutableListOf(), pendingSel, selAfter, now).also { push(it); groupStep = it }
            g.ops += Op(start, old, new); g.selAfter = selAfter; g.time = now
        } else {
            val top = undoStack.lastOrNull()
            val op = top?.ops?.lastOrNull()
            if (!breakNext && top != null && op != null && now - top.time <= 1500 && tryMerge(op, start, old, new)) {
                top.selAfter = selAfter; top.time = now
            } else {
                push(Step(mutableListOf(Op(start, old, new)), pendingSel, selAfter, now))
            }
            breakNext = false
        }
        onChanged?.invoke()
    }

    override fun afterTextChanged(s: Editable) = Unit

    /** Typing run, backspacing typed chars, or IME re-writing part of what was just typed (composition). */
    private fun tryMerge(op: Op, start: Int, old: String, new: String): Boolean {
        val opEnd = op.start + op.new.length
        if (old.isEmpty() && new.isNotEmpty() && start == opEnd) {
            val prev = op.new.lastOrNull()
            if (prev != null && prev.isWhitespace() && !new[0].isWhitespace()) return false   // a new word => new step
            op.new += new; return true
        }
        if (new.isEmpty() && old.isNotEmpty() && start + old.length == opEnd && op.new.length >= old.length) {
            op.new = op.new.dropLast(old.length); return true
        }
        if (old.isNotEmpty() && start >= op.start && start + old.length <= opEnd &&
            op.new.regionMatches(start - op.start, old, 0, old.length)) {
            val r = start - op.start
            op.new = op.new.substring(0, r) + new + op.new.substring(r + old.length); return true
        }
        return false
    }

    private fun push(st: Step) { undoStack.addLast(st); if (undoStack.size > 1000) undoStack.removeFirst() }

    fun undo() { val st = undoStack.removeLastOrNull() ?: return; applyStep(st, reverse = true); redoStack.addLast(st) }
    fun redo() { val st = redoStack.removeLastOrNull() ?: return; applyStep(st, reverse = false); undoStack.addLast(st) }

    private fun applyStep(st: Step, reverse: Boolean) {
        val ed = view.text ?: return
        applying = true
        view.beginBatchEdit()
        try {
            if (reverse) for (op in st.ops.asReversed()) ed.replace(op.start, op.start + op.new.length, op.old)
            else for (op in st.ops) ed.replace(op.start, op.start + op.old.length, op.new)
            Selection.setSelection(ed, (if (reverse) st.selBefore else st.selAfter).coerceIn(0, ed.length))
        } finally { view.endBatchEdit(); applying = false }
        breakNext = true; onChanged?.invoke()
    }
}
```

Notes:
- `setText()` on document install also reaches TextWatchers, so call `undo.clear()` right after `install()` (§6.4).
- Register the undo watcher before smart-edit watchers. TextView notifies listeners in registration order.
- Smart edits done inside a watcher (list continuation on Enter) must be wrapped in `undo.group { }` so they are undone together with the keystroke that triggered them. The keystroke has already been recorded, so call `hardBreak()` before and use a group that *appends to the last step* (add an `appendToLast` flag), or perform the smart edit via an InputFilter before the keystroke commits. The markdown track's `SmartEdit` decides which.
- Persisting undo history across process death is out of scope for MVP.

### 6.12 Instrumented test that guards MdEditable

```kotlin
@Test fun incrementalLayoutEqualsFullReflow() {
    // Launch a test activity hosting MarkdownEditText with a generated 100k doc (use the bench's Doc.generate)
    // perform 50 varied edits via editable.insert/delete (+ Restyler frame callbacks: InstrumentationRegistry.waitForIdleSync())
    val l = et.layout; val n = l.lineCount
    val starts = IntArray(n) { l.getLineStart(it) }; val tops = IntArray(n) { l.getLineTop(it) }
    et.reflowAll()
    val l2 = et.layout
    assertEquals(n, l2.lineCount)
    for (i in 0 until n) { assertEquals(starts[i], l2.getLineStart(i)); assertEquals(tops[i], l2.getLineTop(i)) }
}
```

A second test asserts that typing into a 300k document keeps per-keystroke work under a budget, using the bench's FrameMetrics approach. Run it as a macrobenchmark or manually on the device. Don't gate CI on it (emulator timing noise).

---

## 7. Risks and open questions

1. **MdEditable relies on the internal ordering of `SpannableStringBuilder.replace()`** (§4.4). Mitigations: the layout-equality test, the debug kill-switch, and the conservative v1 fallback. Confidence is high for API 36/37 (source-verified and emulator-verified). Future API levels are unknown.
2. **IME composition was not exercised.** `adb input` sends key events, not composition. The composing-span delivery rule is source-reasoned. Test with Gboard on a device: type fast, autocorrect, glide-typing, and voice input.
3. **Handles after a programmatic format.** Whether the system selection handles stay after a toolbar action replaces text is UNVERIFIED. The toolbar logic doesn't depend on them.
4. **Opening a 300k document costs about 0.7–1.0 s** on the emulator. The DynamicLayout full build is on the main thread and unavoidable. Show the document chrome immediately, and consider a "Large document" note above ~500k chars (the platform track has size thresholds).
5. **Cascading restyles** (typing ``` near the top of a 300k document) restyle up to the whole document over several frames. That is correct, but content above the viewport can shift. Mitigation: visible-first ordering plus the scroll anchoring described in §5.4.
6. **With accessibility services enabled,** TextView copies the whole text on each keystroke (§5.11). This is platform behavior and can't be avoided.
7. **Emulator vs device.** Numbers come from an Apple-silicon-hosted emulator. Validate on a mid-range device (Pixel 8a or similar) with the bench `edittext-fast2r` mode before freezing the design:
   - Build: `JAVA_HOME=<JBR21> <gradle-9.7.1>/bin/gradle --offline :app:assembleRelease` in `scratchpad/bench-editor`.
   - Install and AOT-compile: `adb install -r app/build/outputs/apk/release/app-release.apk`, then `adb shell cmd package compile -m speed -f dev.bench.mdtext`.
   - Run: `adb shell am start -S -W -n dev.bench.mdtext/.MainActivity --es mode edittext-fast2r --ei size 300000 --ei edits 24 [--ez vary true]`.
   - Read results: `adb logcat -s MDBENCH:I | grep -E 'RESULT|VERIFY'`.
   - `run.sh` hard-codes `-s emulator-5554`, so change it for a device.
8. **Compose hosting.** Hardware-keyboard shortcut delivery (`onKeyShortcut`) to an `AndroidView`-hosted EditText, and focus retention when tapping the Compose overlay toolbar, are UNVERIFIED in this session. They are standard interop behavior, but add them to QA.
9. **Hang room H.** A heading marker wider than H (e.g. `###### ` at a large H6 size, or a huge font setting) gets clamped, and the heading text shifts right by the excess. Compute H from the style's widest marker.
10. **Glyph-level details** (iA fonts' italic metrics, fallback line spacing for emoji) belong to the design and fonts track.

## 8. Sources

- API 37 platform sources (SDK package `sources/android-37.0`, rev 2), cited by line:
  - `android/text/SpannableStringBuilder.java` (537–582 replace phases, 602 sendToSpanWatchers, 1285/1294/1306 SpanWatcher lookups, 850 getSpans)
  - `android/text/DynamicLayout.java` (58 PRIORITY, 1232 ChangeWatcher, 1316–1345 UpdateLayout checks)
  - `android/widget/TextView.java` (479 CHANGE_WATCHER_PRIORITY, 1238–1239 break and hyphenation defaults, 3792 setPadding/nullLayouts, 9377–9391 clip, 11916/11935 bringPointIntoView, 13036/13092 spanChange, 13695–13731 shortcuts, 15347 onTextContextMenuItem, 15959 getOffsetForPosition, 16717 accessibility text copy, 4131 setTextCursorDrawable, 3962/4019/4076 handles, 5665 setHighlightColor, 6703/6777/6832 search highlights, 15539 custom selection callback)
  - `android/widget/Editor.java` (238 mAllowUndo, 2460–2461 caret bounds, 2623–2665 startActionModeInternal, 2891–2896 caret width, 4707–4765 TextActionModeCallback, 7565 UndoInputFilter, 7772 isInTextWatcher, 7907 merge rule)
  - `android/text/Layout.java` (737–745 and 3269–3290 margin summation, 2868 getLineBottom(int, boolean), 3132 getSelectionPath)
  - `android/text/style/LineBackgroundSpan.java` (49 signature), `LineHeightSpan.java` (86 Standard), `TypefaceSpan.java` (79 Typeface constructor), `MetricAffectingSpan.java` (27)
  - `android/view/View.java` (8448 startActionMode)
- AOSP `main` copies of `FloatingToolbar.java` and `LocalFloatingToolbarPopup.java` in `scratchpad/src/` (empty-menu handling).
- Compose foundation 1.12.1 sources, `scratchpad/research/src/foundation/`:
  - `commonMain/androidx/compose/foundation/text/input/TextFieldBuffer.kt` (551–559, 607–720)
  - `TrackedRange.kt` (49, 59)
  - `OutputTransformation.kt` (26–27)
  - `internal/TextFieldLayoutStateCache.kt` (153–200, 287–321)
  - `internal/TransformedTextFieldState.kt` (118–128, 554–600)
  - `ComposeFoundationFlags.kt:174`
  - API dump `scratchpad/src/foundation-current.txt:3347–3431`
  - AAR class list checked: `~/.gradle/caches/modules-2/files-2.1/androidx.compose.foundation/foundation-android/1.12.1/…/foundation.aar`
- Markor (Apache-2.0): `scratchpad/src/markor/SyntaxHighlighterBase.java` (design notes 51–92: "minimize UpdateLayout spans", viewport-only dynamic spans), `HighlightingEditor.java`, `TextViewUndoRedo.java` (public domain; char-class merge). Upstream: https://github.com/gsantner/markor
- Benchmark data (this and the previous session): `scratchpad/bench-editor/e2e-results.txt` (JIT, 20k), `e2e-results-aot.txt` (AOT matrix), `e2e-results-fast2.txt` (v2 modes), logcat VERIFY lines (quoted in §2.3), screenshots in `scratchpad/shots/`.
