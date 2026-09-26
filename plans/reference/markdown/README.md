# Markdown engine reference implementation (`:core:markdown`)

Status (2026-09-25): **reference-quality prototype, verified, intended to be ported as the starting point** of the
`:core:markdown` module. The code is pure Kotlin with no Android types. It compiles with Kotlin 2.3.10 at
`-Xexplicit-api=strict -jvm-target 17`, which matches the toolchain decision `kotlin { explicitApi() }` with JVM 17.
The one exception is `MarkdownHtml.kt`, which needs the commonmark jars.

## Files
| File | What | Depends on |
|---|---|---|
| `MdModel.kt` | Token model: `MdKind` (37 kinds), `MdSpan`, `BlockType`, `LineInfo`, `HighlightDelta`, `HeadingItem` | - |
| `MarkdownHighlighter.kt` | Incremental per-line block-state scanner (CommonMark 0.31.2 + GFM tables/tasks/strikethrough/autolinks + footnotes + YAML front matter + optional `==highlight==`). API: `fullScan`, `update(text, start, removed, added)`, `update(text)`, `spansForLines`, `lineInfo`, `headings`, `isFenceUnclosed` | `InlineScanner` |
| `InlineScanner.kt` (internal) | CommonMark delimiter-stack inline parser for one paragraph/heading/cell, with linear-time guards | - |
| `SmartEdit.kt` | Pure editing commands returning `TextEdit`: smart Enter / Backspace, indent/outdent, wrap toggles, link, heading cycle/set, quote toggle, task toggle | `BlockType` |
| `TextStats.kt` | Markdown-aware words / chars / sentences / tasks / reading time (238 wpm) | `MdSpan` |
| `MarkdownHtml.kt` | Preview/export HTML (commonmark-java 0.30.0 + tables, strikethrough, task lists, autolink, footnotes, YAML front matter, alerts, heading anchors) + page template with CSP | commonmark 0.30.0 jars |
| `test/HighlighterCases.kt` | 50 curated static cases + 7 typing sequences (exact expected strings) | - |
| `test/CasesCheck.kt` | Plain-`main` runner for the cases (port to JUnit 6 `@TestFactory`) | - |
| `test/Harness.kt` | `diff` (652 CommonMark spec examples vs commonmark-java oracle), `fuzz` (incremental == full), `bench`, `dump` | commonmark jars (test only) |
| `test/SmartHarness.kt`, `test/StatsHarness.kt`, `test/Patho.kt`, `test/ParaCost.kt`, `test/HtmlCheck.kt` | Behavior tables, pathological-input timing, long-paragraph cost, HTML output | - / commonmark |
| `test/resources/spec-0.31.2.json` | CommonMark 0.31.2 spec examples (652), from spec.commonmark.org | - |
| `test/resources/spec-cases.txt` | The same examples in `@@@@ <n> <section>` … `@@@@END` format read by `Harness.kt diff` | - |
| `test/resources/specseed.md` | Fuzz seed document (first 6,000 chars used) | - |

## Verified results (desktop JBR 21, 2026-09-25)
- Curated cases: **57/57 pass**. The 50 static inputs also agree **50/50** with the commonmark-java oracle on inline ranges, headings and code lines.
- Spec differential: **641/652** CommonMark examples agree with commonmark-java 0.30.0 (+GFM exts) on emphasis/strong/strike/code/link/image/autolink ranges, heading lines/levels and code lines. The known diffs are {6, 193, 195, 196, 198, 217, 259, 541, 571, 602, 606}: multi-line link reference definitions, `[a][b][c]` chaining, 2 tab/lazy corner cases, and 2 lenient autolinks of the oracle.
- Fuzz: 3,000 random edits on each of 3 seeds. Result: **0** span mismatches vs full scan, **0** `LineInfo` mismatches, and **0** changed spans outside the returned `HighlightDelta`.
- Speed (100 KB / 300 KB / 1 MB):
  - `fullScan`: 1.7 / 4.4 / 15.0 ms;
  - `update(text, start, removed, added)` p50: **0.011 / 0.009 / 0.010 ms** (independent of size);
  - `update(text)` (diff overload) p50: 0.13 / 0.36 / 1.2 ms.
- Pathological inputs (20,000 repetitions): all ≤ 35 ms except a 4 MB deep list (80 ms). `"x <!--"*n` was **1,984 ms before the memo fix**.
- Long single paragraph, cost per keystroke: 500 lines 0.38 ms, 2,000 lines 1.6 ms, 5,000 lines 4.3 ms. Before the fix, 2,000 lines took **44 ms** because span distribution was quadratic.
- Smart-edit harness: toggle-twice == identity (0 violations); `TextEdit.minimize` preserves results (0 violations).

## Bugs fixed relative to the salvaged `proto/`
1. `inlineOver` distributed spans to lines in O(spans × lines), making the cost quadratic in paragraph length. It now uses a binary search.
2. There were quadratic re-scans for unfinished `<!--`, `<?`, `<![CDATA[`, `<!X`, attribute quotes, link titles and backtick runs. These searches are now memoized, as in commonmark-java 0.30.0.
3. GFM bare e-mail autolinks were missing. They are now a post-pass that respects code, link, HTML and emphasis markers.
4. `update()` diffed the whole document on every call (O(n)). Added `update(text, changeStart, removedLen, addedLen)`, which returns a `HighlightDelta`.
5. Added per-line `LineInfo` (block type, content start after container prefixes, quote/list depth), `headings()`, `isFenceUnclosed()`, and `HTML_BLOCK` `arg` = CommonMark HTML block type.
6. SmartEdit:
   - Enter is verbatim-aware (no list continuation inside code/HTML/front matter);
   - Enter on an unclosed opening fence auto-inserts the closing fence;
   - lazy `1. 1.` numbering is kept;
   - added `toggleQuote`, `setHeading(level)`, `onBackspace` (remove list marker / quote level), and `TextEdit.minimize`.
7. TextStats: text inside HTML blocks counts, and only tags and comments are excluded. Added `readingMinutesRounded()`.

## Known limitations (accepted for v1)
- Link reference definitions spanning several lines are not recognized.
- Entity names are not validated against the HTML5 list.
- `http://localhost` (no dot) is linked.
- Word count treats Thai, Lao, Khmer and Myanmar runs as one word.
- Incremental cost is O(enclosing paragraph/table).
- An unclosed fence typed at the top rescans to the end of the document once (about 15 ms/MB desktop).
- `MdSpan` objects are allocated per span. This is fine at the measured costs, but if profiling on device shows GC pressure, pack spans into an `IntArray` per line.

## Porting notes for the implementing agent
- Package: `mdwriter.markdown`. Rename it to the app's package if the plan says so, and keep file names.
- `InlineScanner`, `Mode`, `LineState` and `IntList` are `internal`; everything else is `public`, as explicit API requires.
- **Formatting:** the code uses compact one-liners and `;`. Run `./gradlew spotlessApply` (ktlint_official, `max_line_length = 120`), then manually wrap whatever ktlint cannot auto-fix. **Do not change logic while reformatting.** Re-run the tests after every formatting pass.
- Tests: port `test/*.kt` into `core/markdown/src/test/kotlin/…` as JUnit 6 tests:
  - `CasesCheck` → `@TestFactory` over `HIGHLIGHTER_CASES` and `TYPING_CASES`;
  - `Harness.differential` → spec test over `spec-0.31.2.json`, with the 11 allowed failures pinned;
  - `Harness.fuzz` → seeded fuzz test;
  - `Patho` → a timing test with a generous bound;
  - SmartEdit and TextStats harness rows → table-driven tests.
- commonmark-java is `implementation` in `:core:markdown` (it is needed by `MarkdownHtml`). The oracle tests use it too.
- Local run without Gradle (as used for these numbers):
  ```sh
  export JAVA_HOME=/Users/gcg/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home
  K="/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc"   # kotlinc 2.3.10
  CP=$(ls ../lib/commonmark*.jar ../lib/autolink*.jar | tr '\n' ':')
  "$K" -jvm-target 17 -cp "$CP" *.kt test/*.kt -d /tmp/md.jar
  java -cp /tmp/md.jar:../lib/kotlin-stdlib-2.4.20.jar:$CP CasesCheckKt
  java -cp /tmp/md.jar:../lib/kotlin-stdlib-2.4.20.jar:$CP HarnessKt diff test/resources/spec-cases.txt
  java -cp /tmp/md.jar:../lib/kotlin-stdlib-2.4.20.jar:$CP HarnessKt fuzz test/resources/specseed.md 3000
  ```
