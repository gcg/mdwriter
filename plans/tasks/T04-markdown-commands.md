# T04 — SmartEdit additions, TextStats, MarkdownHtml, DocTitle

**Goal**
Finish the pure-Kotlin half of the Markdown engine that the editor, toolbar, stats, Preview/Export and library call.
Port `SmartEdit` (smart Enter/Backspace, wrap toggles, link, headings, quote, task), `TextStats` and `MarkdownHtml`,
with table-driven tests. Add the commands the selection toolbar's "Code" button and its More menu need (list, code
block, clear formatting), and add `DocTitle`: title from content, safe file names and library excerpts. Nothing
changes in the UI yet. T08/T09/T10/T12 wire these up.

**Depends on**
- **T03**: `:core:markdown` compiles with `MarkdownHighlighter`, `MdModel` under `dev.mdwriter.markdown`; JUnit 6 +
  Truth test setup; `TestResources.kt` (`resourceText`); Spotless clean. Read T03's STATUS entry (suppressions used).

**Read first**
- `plans/01-architecture.md` §3 (package map), §6.1 (`SmartEdit`/`TextEdit`/`TextStats`/`MarkdownHtml`/`DocTitle`
  contract), §7 (stats/preview threading).
- `plans/02-design-spec.md` §6 (selection toolbar: "Code" = inline or fenced block; More menu: Strikethrough, Highlight,
  Quote, Bulleted list, Numbered list, Task, Code block, Clear formatting, Select all).
- `plans/research/markdown.md` §8 (SmartEdit rules + **harness output table** = expected values), §9 (stats rules +
  table), §3.1 (renderer setup).
- `plans/research/platform.md` §2.6 (file naming).
- Sources: `plans/reference/markdown/{SmartEdit,TextStats,MarkdownHtml}.kt`,
  `plans/reference/markdown/test/{SmartHarness,StatsHarness,HtmlCheck}.kt`.

## Scope — In / Out
**In:** port the three sources verbatim, apart from the package line and formatting. Add four new SmartEdit commands,
`ListKind`, `DocTitle`, an internal `MarkupStrip` helper, and all the tests below.
**Out:** EditText apply/undo/smart-key wiring (**T08**); pill, More menu, `ToolbarAction` mapping (**T09**); stats UI +
debounce (stats task); WebView/`WebViewAssetLoader`/`preview.css` (Preview task); auto-rename + `UniqueName` (**T10**);
library excerpt UI (**T12**). No `android.*` imports.

## Files to create / modify
Main: `core/markdown/src/main/kotlin/dev/mdwriter/markdown/`
- `SmartEdit.kt`: port (`TextEdit`, `LinePrefix`, `object SmartEdit`), plus new `enum class ListKind`, `toggleList`,
  `toggleCodeBlock`, `clearFormatting`, `codeToggle`, and private helpers `touchedSpan`, `applyRewrites`.
- `TextStats.kt`: port (`Stats`, `object TextStats`), verbatim.
- `MarkdownHtml.kt`: port (`renderBody`, `renderPage` with CSP), verbatim.
- `MarkupStrip.kt`: **new, internal.** Computes char ranges to delete so that only visible text remains.
  Shared by `clearFormatting` and `DocTitle`. Add one line for it to `01-architecture.md` §3 in this commit.
- `DocTitle.kt`: **new.** `object DocTitle { fromContent, excerpt, sanitizeFileName, FALLBACK_NAME, MAX_NAME_LENGTH, EXCERPT_MAX }`.

Test: `core/markdown/src/test/kotlin/dev/mdwriter/markdown/`
- `SmartNotation.kt`: `parseState`, `render` (copied from `SmartHarness.kt`), `apply(state, f): String`.
- `SmartEditTest.kt`: every harness row as a dynamic test, plus toggle-twice identity and `minimize` properties.
- `SmartEditTogglesTest.kt`: tables for `toggleList`, `toggleCodeBlock`, `codeToggle` and `clearFormatting`, plus toggle-twice
  identity and `minimize` properties.
- `StatsTest.kt`: the 19 §9 rows, a selection sub-range and rounding.
- `MarkdownHtmlTest.kt`: HTML features, sanitising and CSP.
- `DocTitleTest.kt`: `fromContent`, `excerpt` and `sanitizeFileName` tables.

## Steps
1. Port `SmartEdit.kt`, `TextStats.kt` and `MarkdownHtml.kt` the same way as T03 step 2 (`sed` the package line to
   `dev.mdwriter.markdown`). Compile.
2. `SmartNotation.kt`: `parseState`/`render` are copied verbatim (`|` = cursor, `«…»` = selection). Add
   `fun apply(state: String, f: (String, Int, Int) -> TextEdit?): String` that returns `"(default newline)"` for null.
   Otherwise it calls `e.applyTo(t)` and `render(newText, sel.first, sel.last)`.
3. `SmartEditTest`: for **every** `run(...)` call in `SmartHarness.kt` (in order, 78 rows, matching the 78 table rows in
   research §8), write `Row(op, before, after, f)`. Use the same lambda as the harness (for example
   `SmartEdit.onEnter(t, a, lineType = BlockType.FENCE_OPEN, fenceUnclosed = true)`). `after` = the §8 "After" cell.
   Transcribe by hand, with `⏎` → `\n`, and **strip exactly one backtick at each end** (the harness `show()` adds them),
   so ```` ````kotlin|` ```` becomes ```` "```kotlin|" ```` and `` ``«val x»`` `` becomes `` "`«val x»`" ``.
   `@TestFactory` → `dynamicTest("$i $op ${before}") { assertEquals(after, apply(before, f)) }`.
   Then port the harness's toggle-twice loop (5 markers × 7 samples: 0 violations) and the `minimize()` loop as
   two `@Test`s.
   If a transcribed row disagrees with the code, first check your transcription against `SmartHarness.kt` and §8.
   Don't change SmartEdit logic.
4. Add the new SmartEdit commands (Reference code §A–§D) and `MarkupStrip` (§C). Write `SmartEditTogglesTest` with the
   exact rows in §F.
5. `StatsTest` (§G), `MarkdownHtmlTest` (§H).
6. `DocTitle.kt` (§E) + `DocTitleTest` (§F).
7. `make format`, re-run tests after every pass (same rules as T03 step 7; no logic changes in ported code). Add KDoc to
   every public declaration, including a one-line example for each new command.
8. Update `01-architecture.md`: add the `MarkupStrip.kt` line to §3. In the §6.1 bullets list `ListKind`, the 4 new
   commands and `DocTitle.excerpt`. Then `make check`, the STATUS entry, and commit.

## Reference code
Everything below is a **sketch**. The behaviour is pinned by the §F tables. Where the prose and a table disagree, the
table wins.

§A Shared line helpers + `toggleList`. Put them in `object SmartEdit`, with `ListKind` top-level in SmartEdit.kt.
```kotlin
public enum class ListKind { BULLET, ORDERED, TASK }

// group1 = container prefix (indent + '>' levels + indent), group2 = list marker + spacing (+ task box), group3 = task box
private val LIST_PREFIX = Regex("^([ \\t]*(?:>[ \\t]?)*[ \\t]*)((?:[-*+]|\\d{1,9}[.)])(?:[ \\t]+|$)(\\[[ xX]\\](?:[ \\t]+|$))?)?")

/** [first line start, last line end) of the lines touched by [a,b]; a selection ending at a line start excludes that line. */
private fun touchedSpan(text: String, a: Int, b: Int): Pair<Int, Int> {
    val lo = minOf(a, b); var hi = maxOf(a, b)
    if (hi > lo && text[hi - 1] == '\n') hi--
    val first = text.lastIndexOf('\n', lo - 1) + 1
    val last = text.indexOf('\n', hi).let { if (it < 0) text.length else it }
    return first to last
}

private class LineRewrite(val oldStart: Int, val oldLen: Int, val keep: Int, val oldMarkerLen: Int, val newMarker: String)

/** Rebuilds [from,to) line by line: keep container prefix, swap marker, keep content. Maps the selection per line. */
private fun applyRewrites(text: String, from: Int, to: Int, rws: List<LineRewrite>, a: Int, b: Int): TextEdit {
    val sb = StringBuilder()
    rws.forEachIndexed { i, r ->
        if (i > 0) sb.append('\n')
        sb.append(text, r.oldStart, r.oldStart + r.keep).append(r.newMarker)
            .append(text, r.oldStart + r.keep + r.oldMarkerLen, r.oldStart + r.oldLen)
    }
    fun map(pos: Int): Int {
        var shift = 0
        for (r in rws) {
            val d = r.newMarker.length - r.oldMarkerLen
            if (pos > r.oldStart + r.oldLen) { shift += d; continue }
            if (pos < r.oldStart) break
            val prefixEnd = r.oldStart + r.keep
            return when {
                pos >= prefixEnd + r.oldMarkerLen -> pos + shift + d
                pos > prefixEnd -> prefixEnd + shift + r.newMarker.length
                else -> pos + shift
            }
        }
        return pos + shift
    }
    return TextEdit(from, to, sb.toString(), map(minOf(a, b)), map(maxOf(a, b)))
}

public fun toggleList(text: String, selStart: Int, selEnd: Int, kind: ListKind): TextEdit {
    val (from, to) = touchedSpan(text, selStart, selEnd)
    var off = from
    val lines = text.substring(from, to).split('\n').map { line ->
        val m = LIST_PREFIX.find(line)!!                        // always matches (group 1 may be empty)
        Triple(off, line, m).also { off += line.length + 1 }
    }
    fun kindOf(m: MatchResult): ListKind? {
        val mk = m.groups[2]?.value ?: return null
        return when { m.groups[3] != null -> ListKind.TASK; mk[0].isDigit() -> ListKind.ORDERED; else -> ListKind.BULLET }
    }
    fun hasContent(line: String, m: MatchResult) = line.substring(m.groups[1]!!.value.length).isNotBlank()
    val content = lines.filter { (_, l, m) -> hasContent(l, m) }
    val remove = content.isNotEmpty() && content.all { (_, _, m) -> kindOf(m) == kind }
    var n = 0
    val rws = lines.map { (start, line, m) ->
        val keep = m.groups[1]!!.value.length
        val old = m.groups[2]?.value.orEmpty()
        val new = when {
            remove -> ""
            content.isNotEmpty() && !hasContent(line, m) -> old            // blank lines inside a block: untouched
            else -> when (kind) { ListKind.BULLET -> "- "; ListKind.ORDERED -> "${++n}. "; ListKind.TASK -> "- [ ] " }
        }
        LineRewrite(start, line.length, keep, old.length, new)
    }
    return applyRewrites(text, from, to, rws, selStart, selEnd)
}
```
The rules: if every touched line with content already has `kind`, remove the markers (keep indent and `>` prefix).
Otherwise give every line with content the new marker, replacing any other list or task marker. ORDERED numbers
run 1..n over lines with content. If no touched line has content, every touched line gets the marker (an empty line
becomes `- |`).

§B `toggleCodeBlock` + `codeToggle` (uses the highlighter for fence context; commands are rare, so an O(n) scan is fine)
```kotlin
public fun toggleCodeBlock(text: String, selStart: Int, selEnd: Int): TextEdit {
    val (from, to) = touchedSpan(text, selStart, selEnd)
    val hl = MarkdownHighlighter(enableHighlight = false).apply { fullScan(text) }
    val l0 = hl.lineIndexOf(from); val l1 = hl.lineIndexOf(to)
    fun type(l: Int) = hl.lineInfo(l).type
    val inFence = setOf(BlockType.FENCE_OPEN, BlockType.FENCE_CLOSE, BlockType.FENCED_CODE)
    // Case A: selection covers the opening and the closing fence line -> drop both lines.
    // Case B: no touched line is a fence line and at least one is FENCED_CODE -> scan up (over FENCED_CODE/BLANK) to the
    //         FENCE_OPEN line and down to the FENCE_CLOSE line (none if unclosed) -> drop those lines (+ their '\n').
    // Case C: any other touched line in `inFence` -> no-op: TextEdit(selStart, selStart, "", selStart, selEnd).
    // Wrap: body = text.substring(from, to); fence = "`".repeat(maxOf(3, longestBacktickRun(body) + 1));
    //       TextEdit(from, to, "$fence\n$body\n$fence", clamp(selStart) + fence.length + 1, clamp(selEnd) + fence.length + 1)
    //       where clamp(p) = p.coerceIn(from, to). Unwrap maps the selection onto the body (positions on removed
    //       lines clamp to the body's start or end).
}

public fun codeToggle(text: String, selStart: Int, selEnd: Int): TextEdit {
    // multi-line (a '\n' inside [min,max)) OR the touched line is FENCE_OPEN/FENCE_CLOSE/FENCED_CODE -> toggleCodeBlock
    // else toggleWrap(text, selStart, selEnd, "`")
}
```
Known v1 limitation (put it in KDoc): wrapping doesn't carry `>`/list prefixes onto the fence lines.

§C `MarkupStrip` (internal, new file) + `clearFormatting`
```kotlin
internal object MarkupStrip {
    /** Adds [start, end) ranges (as `start until end`) to delete inside [from, to) so only visible text stays. */
    fun inlineDeletions(text: CharSequence, spans: List<MdSpan>, from: Int, to: Int, out: MutableList<IntRange>) {
        val inside = spans.filter { it.start >= from && it.end <= to }
        for (s in inside) when (s.kind) {
            MdKind.EMPHASIS_MARKER, MdKind.CODE_SPAN_MARKER -> out += s.start until s.end   // * _ ** __ ~ ~~ == `
            MdKind.LINK -> {                                                                // keep only the link text / alt
                val t = inside.firstOrNull { it.kind == MdKind.LINK_TEXT && it.start >= s.start && it.end <= s.end }
                if (t != null) { out += s.start until t.start; out += t.end until s.end }
                else inside.filter { it.kind == MdKind.LINK_MARKER && it.start >= s.start && it.end <= s.end }
                    .forEach { out += it.start until it.end }
            }
            MdKind.AUTOLINK -> if (text[s.start] == '<') { out += s.start until s.start + 1; out += s.end - 1 until s.end }
            else -> Unit
        }
    }
    /** Sorts + merges [del] and returns text without them. */
    fun deleteAll(text: CharSequence, del: List<IntRange>): String
    /** Maps an old offset through the merged deletions (inside a range -> range start). */
    fun map(pos: Int, merged: List<IntRange>): Int
}

public fun clearFormatting(text: String, selStart: Int, selEnd: Int, enableHighlight: Boolean = false): TextEdit
```
Algorithm:
1. `a = min(sel)`, `b = max(sel)`. If `a == b`, use the current line's `[start, end)` as the scope instead.
2. Inline scope: if there are marker chars (`*_~=` + backtick) directly outside the scope on **both** sides, extend the scope
   over those runs. That way `**«x»**` works, while `snake_ca«se»` stays untouched.
3. `hl = MarkdownHighlighter(enableHighlight, enableFrontMatter = true).apply { fullScan(text) }`.
4. For each touched line whose `lineInfo(l).type.isVerbatim` is false, delete `[info.start, info.contentStart)` (quote,
   list and task prefix, plus indent) and every `HEADING_MARKER` span on that line.
5. Add `MarkupStrip.inlineDeletions(text, hl.spansForLines(firstLine, lastLine + 1), scopeA, scopeB, del)`.
6. Result: `TextEdit(minStart, maxEnd, …)` covering only the deleted region, with both selection ends mapped
   through `MarkupStrip.map` (use the **original** selection). If there are no deletions, return
   `TextEdit(a, a, "", selStart, selEnd)`.

§D Contract for T08/T09. All four return a non-null `TextEdit`, and callers apply `edit.minimize(text)`. The signatures
are exactly: `toggleList(text: String, selStart: Int, selEnd: Int, kind: ListKind)`, `toggleCodeBlock(text, selStart, selEnd)`,
`codeToggle(text, selStart, selEnd)`, `clearFormatting(text, selStart, selEnd, enableHighlight: Boolean = false)`.

§E `DocTitle` (new)
```kotlin
public object DocTitle {
    public const val FALLBACK_NAME: String = "Untitled"
    public const val MAX_NAME_LENGTH: Int = 80        // UTF-16 units, never splits a surrogate pair
    public const val EXCERPT_MAX: Int = 120
    private val FORBIDDEN = "/\\:*?\"<>|"
    private val FENCE_LINE = Regex("^[ \\t]*(?:>[ \\t]?)*[ \\t]*(?:`{3,}|~{3,})")

    /** First line with visible text, markup stripped; null if none. */
    public fun fromContent(text: String): String? = contentLines(text).firstOrNull()

    /** First line with visible text AFTER the title line, ≤ [EXCERPT_MAX] chars (cut to 119 + "…"); null if none. */
    public fun excerpt(text: String): String? = contentLines(text).drop(1).firstOrNull()?.let(::cap)

    /** Base name without extension; never empty ([FALLBACK_NAME]). */
    public fun sanitizeFileName(name: String): String

    // contentLines: lazy sequence over lines; skips YAML front matter (line 0 == "---" and a later "---"/"..." line),
    // FENCE_LINE lines, and lines whose plain text is empty. plain(line) = run
    // MarkdownHighlighter(enableHighlight = false, enableFrontMatter = false) on the single line; if lineInfo(0).type
    // is LINK_DEF or HTML -> "", else delete every span with kind.isMarker, HTML_INLINE, FOOTNOTE_REF, plus
    // MarkupStrip.inlineDeletions(...) over the whole line; then collapse whitespace runs to one ' ' and trim.
}
```
`sanitizeFileName`, in order: control chars (`Character.isISOControl`, incl. `\t`/`\n`) → space; remove `FORBIDDEN`;
collapse whitespace runs to one space + trim; strip leading dots and trailing dots/spaces; cap at 80 UTF-16 units
(drop a dangling high surrogate); strip trailing dots/spaces again; empty → `"Untitled"`.

## Tests (exact rows)
§F `SmartEditTogglesTest`, in `before → after` notation (`⏎` = `\n`, and every expected value is exact):
- **toggleList BULLET:** `«one⏎two»`→`- «one⏎- two»`; `- «one⏎- two»`→`«one⏎two»`; `ta|sk`→`- ta|sk`; `|`→`- |`;
  `- |`→`|`; `> «quoted»`→`> - «quoted»`; `  - a⏎  - «b»`→`  - a⏎  «b»`; `«- a⏎b»`→`«- a⏎- b»`;
  `«one⏎»two`→`- «one⏎»two`.
- **toggleList ORDERED:** `«a⏎⏎b»`→`1. «a⏎⏎2. b»`; `- «a⏎- b»`→`1. «a⏎2. b»`; `1) «a»`→`«a»`.
- **toggleList TASK:** `1. «a⏎2. b»`→`- [ ] «a⏎- [ ] b»`; `- [x] «done»`→`«done»`.
- **toggleCodeBlock:** `«val x = 1⏎val y = 2»`→`` ```⏎«val x = 1⏎val y = 2»⏎``` ``; that output→the input (Case B);
  `` «```⏎val x⏎```» ``→`«val x»` (Case A); ``«a ``` b»``→`` ````⏎«a ``` b»⏎```` ``; `|`→`` ```⏎|⏎``` ``;
  `~~~⏎«x»⏎~~~`→`«x»`; `` ```⏎a⏎«b»⏎``` ``→`a⏎«b»`.
- **codeToggle:** `«val x»`→`` `«val x»` ``; `` `«val x»` ``→`«val x»`; `«a⏎b»`→`` ```⏎«a⏎b»⏎``` ``; `` ```⏎«x»⏎``` ``→`«x»`.
- **clearFormatting** (default `enableHighlight = false`): `«**bold** and *it*»`→`«bold and it»`;
  `make **«this»** plain`→`make «this» plain`; `«[docs](https://x.y "T")»`→`«docs»`; `«# Title»`→`«Title»`;
  `«- [x] done ~~old~~»`→`«done old»`; ``> «quote `code`»``→`«quote code»`; `snake_ca«se»`→unchanged;
  `# Ti|tle`→`Ti|tle`; `«_a_ __b__»`→`«a b»`; `«![alt](i.png)»`→`«alt»`; `«<https://x.y>»`→`«https://x.y»`;
  `==«mark»==`→`«mark»` with `enableHighlight = true`, unchanged with `false`.
- **Properties** (`@Test`s):
  1. Applying twice, using the selection returned by the first call, gives back the original text:
     - `toggleList` × 3 kinds on `«one⏎two⏎three»`, `«a⏎⏎b»`, `ta|sk`, `|`, `> «quoted⏎> lines»`, `  «indented»`,
       `«# Heading⏎para»`;
     - `toggleCodeBlock` and `codeToggle` on `«val x = 1⏎val y = 2»`, ``«a ``` b»``, `|`, `x|y`, `«one»⏎two`.
  2. `e.applyTo(t) == e.minimize(t).applyTo(t)` for every row above.

`DocTitleTest` (exact):
- `fromContent`: `"# Hello World\n\nBody"`→`"Hello World"`; `"\n\n  \n## **Bold** title"`→`"Bold title"`;
  `"> - [ ] Buy *milk*"`→`"Buy milk"`; ``"[Docs](https://x.y) and `code`"``→`"Docs and code"`;
  `"---\ntitle: X\n---\nReal title"`→`"Real title"`; `"---\n\nAfter rule"`→`"After rule"`;
  `` "```kotlin\nval x = 1\n```" ``→`"val x = 1"`; `"  Trailing spaces   "`→`"Trailing spaces"`;
  `"Title with \\*escaped\\* stars"`→`"Title with *escaped* stars"`; `"<b>Bold</b> tag"`→`"Bold tag"`;
  `""`, `"\n   \n"`, `"---"`, `"- [ ] "` → `null`.
- `excerpt`: `"# Title\n\nFirst **para** line\nsecond"`→`"First para line"`; `"Title only"`, `""` → `null`;
  `"---\ntitle: X\n---\n# T\n- item one"`→`"item one"`; `"# T\n\n" + "word ".repeat(40)` → length 120, ends `"…"`.
- `sanitizeFileName`: `"My: Note/Draft?"`→`"My NoteDraft"`; `"  a\tb\n c  "`→`"a b c"`; `"Title..."`→`"Title"`;
  `"...hidden"`→`"hidden"`; `"a <b> \"c\" |d|"`→`"a b c d"`; `"Ünïcödé 日本語"`→unchanged;
  `""`, `"   "`, `"***"`, `"\u0000\u0007"` → `"Untitled"`; `"a".repeat(200)`→80×`a`; `"a".repeat(79)+"😀"`→79×`a`;
  `"a".repeat(79)+". b"`→79×`a`.

§G `StatsTest`: each input from `StatsHarness.kt` `cases` (same order), `st(md)` as in the harness (default
`MarkdownHighlighter()`), and assert `(words, chars, charsNoSpaces, sentences, tasks, tasksDone, readingMinutesRounded())`:
```text
2,11,10,1,0,0,1 | 4,28,24,1,0,0,1 | 2,44,42,1,0,0,1 | 4,30,23,1,2,1,1 | 6,39,34,1,0,0,1 | 8,8,8,1,0,0,1 | 5,13,11,1,0,0,1
4,10,7,1,0,0,1 | 3,17,15,1,0,0,1 | 6,38,33,1,0,0,1 | 2,41,37,1,0,0,1 | 0,31,27,0,0,0,0 | 2,13,11,1,0,0,1 | 4,27,24,3,0,0,1
3,28,25,1,0,0,1 | 1,19,17,1,0,0,1 | 0,21,19,0,0,0,0 | 3,26,24,1,0,0,1 | 2,18,17,1,0,0,1
```
Also: `compute("Hello brave world", 6, 11, spans).words == 1`; `Stats(238,0,0,0,0,0).readingMinutesRounded() == 1`,
`Stats(239,0,0,0,0,0)` → `2`; `Stats.ZERO.readingMinutesRounded() == 0`.

§H `MarkdownHtmlTest`: `SAMPLE` = the `md` string from `HtmlCheck.kt`, verbatim. `renderBody(SAMPLE)` contains
`type="checkbox"` and `checked`; `footnote` and `href="#fn`; `align="left"` and `align="right"`; `<h1 id="head-x">`;
`href=""` with no `javascript:` anywhere; and no `title: T` (front matter not rendered). Plus:
`renderBody("> [!NOTE]\n> hi")` contains `markdown-alert-note`;
`MarkdownHtml(allowRawHtml = false).renderBody("<script>x</script>")` contains `&lt;script&gt;`;
`renderPage(SAMPLE, "dark", mapOf("--font-size" to "18px"), "A<B")` contains `http-equiv="Content-Security-Policy"`,
`default-src 'none'`, `<html class="dark"`, `:root{--font-size:18px}` and `<title>A&lt;B</title>`, and has no `<script src`.

If a commonmark attribute string differs, fix the **assertion**, record the actual output in STATUS, and leave
`MarkdownHtml` as it is.

## Acceptance criteria
1. The `SmartEditTest` XML reports ≥ 80 tests (78 rows + 2 properties), 0 failures.
2. `SmartEditTogglesTest`, `StatsTest`, `MarkdownHtmlTest` and `DocTitleTest` pass with every row in §F–§H present.
3. The ported files differ from the reference only in the package line, formatting and KDoc (review
   `git diff --no-index -w` against `plans/reference/markdown/*.kt`).
4. `grep -rn 'import android' core/markdown/src` prints nothing. The T03 suite still passes (57 / 641 / fuzz 0).
5. `./gradlew spotlessCheck` and `make check` are green.

## Verification commands
```sh
./gradlew :core:markdown:test --rerun
grep -ho 'testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  core/markdown/build/test-results/test/TEST-*.xml
grep -rn 'import android' core/markdown/src || echo "OK: no android imports"
make format && ./gradlew :core:markdown:test spotlessCheck
make check        # release parts: KEYSTORE_DIR=/tmp/... override per README "Rules for agents"
```
No emulator (pure JVM). Don't run `make install`.

## Pitfalls
- **The §8 table is harness output wrapped by `show()`:** strip one backtick at each end, and `⏎` is `\n`. Two spaces
  in `make **«this»**  bold` are real.
- `TextEdit.applyTo` returns `Pair<String, IntRange>`, where `sel.last` is `selEnd` (an inclusive-looking IntRange that
  holds the end). Render with `sel.first`/`sel.last`, as the harness does.
- `touchedSpan`: a selection ending exactly at a line start must **not** touch that line (the `«one⏎»two` row).
- `clearFormatting`/`toggleCodeBlock`/`DocTitle` construct a `MarkdownHighlighter`. That's fine for rare commands,
  but never call them per keystroke (the §9 perf budget in 01 belongs to the editor).
- HARD RULE §10.3: the *callers* (T08/T09) wrap our TextEdit in a batch edit. Nothing here touches Android.
- `DocTitle.sanitizeFileName` returns a **base name** (no `.md`) and is never empty. T10/T12 append the extension and
  resolve collisions.
- Keep `ListKind` in `dev.mdwriter.markdown`. Don't name it `ListType`/`ListStyle` (T09 imports this name).

## Definition of done
- [ ] 3 ported + 2 new main files, 6 test files. `01-architecture.md` §3/§6.1 updated in the same commit.
- [ ] Acceptance 1–5 met; test counts pasted into the STATUS entry.
- [ ] `make check` green.
- [ ] `plans/STATUS.md` T04 entry appended: new API signatures, deviations, commonmark outputs you had to pin.
- [ ] Commit `T04: SmartEdit toggles, TextStats, MarkdownHtml, DocTitle` (no push).
