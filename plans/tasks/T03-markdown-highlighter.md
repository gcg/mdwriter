# T03 — Markdown highlighter port + full test suite (`:core:markdown`)

**Goal**
Port the verified incremental Markdown highlighter (`MdModel.kt`, `InlineScanner.kt`, `MarkdownHighlighter.kt`) into
`:core:markdown` under `dev.mdwriter.markdown`. Port its test harnesses to JUnit 6: curated cases, typing sequences,
a seeded incremental-vs-full fuzz, the CommonMark 0.31.2 spec differential against commonmark-java, pathological timing
and a small API test. Nobody sees this in the app yet. It is the engine that T05/T06 style the editor with. Done =
the verified numbers (57/57, 0/0/0 fuzz, 641/652 spec) reproduced by `make test`.

**Depends on**
- **T01**: Gradle project builds; `:core:markdown` exists with `plans/reference/build/core/markdown/build.gradle.kts`
  (JVM-only, `explicitApi()`, JUnit BOM + jupiter + `junit-platform-launcher`, Truth, `implementation(libs.bundles.commonmark)`);
  `.editorconfig` copied from `plans/reference/editorconfig`; `make test`, `make format`, `make check` work.
  Read T01's entry in `plans/STATUS.md` first.

**Read first**
- `plans/01-architecture.md` §2 (JVM-only module, no `android.*`), §3 (package map), §6.1 (highlighter contract),
  §11 (testing strategy row "Markdown engine").
- `plans/reference/markdown/README.md` (whole file: verified results, porting notes, formatting rule).
- `plans/research/markdown.md` §5 (incremental algorithm; skim) and §6 (public API + budgets).
- `plans/research/README.md` known errors: package names in reports are wrong (`mdwriter.markdown`, `app.mdwriter`), so use
  `dev.mdwriter`. The tokenizer API is `update(...)`, not `applyEdit/rescan`.
- Sources you port (read each fully before porting): `plans/reference/markdown/{MdModel,InlineScanner,MarkdownHighlighter}.kt`,
  `plans/reference/markdown/test/{HighlighterCases,CasesCheck,Harness,Patho}.kt`.

## Scope — In / Out
**In**
- Three main sources, ported verbatim apart from the package line, formatting and KDoc.
- Test sources + resources listed below; ktlint/Spotless clean.

**Out**
- `SmartEdit.kt`, `TextStats.kt`, `MarkdownHtml.kt`, `DocTitle.kt` and their tests are **T04**. Don't copy them now, even
  though they sit in the same reference folder.
- `ParaCost.kt`, `Harness.bench`, `Harness.dump` are benchmarks, not tests. Don't port them; device perf is **T21**.
- Anything in `:app` (spans, restyler, editor) is **T05/T06**.
- **No logic changes** to the highlighter. A test failure after porting means a porting or formatting mistake, so diff
  against the reference. If a verified number can't be reproduced, STOP-AND-ASK (README rule 4).

## Files to create / modify
Main: `core/markdown/src/main/kotlin/dev/mdwriter/markdown/`
- `MdModel.kt`: `MdKind`, `MdSpan`, `BlockType`, `LineInfo`, `HighlightDelta`, `HeadingItem` (+ KDoc).
- `InlineScanner.kt`: internal CommonMark inline parser (`internal`, as in the reference).
- `MarkdownHighlighter.kt`: the public highlighter (+ KDoc on every public member).
- Delete any placeholder source T01 left in `core/markdown/src/main` / `src/test`, and note it in STATUS.

Test: `core/markdown/src/test/kotlin/dev/mdwriter/markdown/`
- `HighlighterCases.kt`: `HlCase`, `HIGHLIGHTER_CASES` (50), `TypingCase`, `TYPING_CASES` (7). Copy verbatim; this is test data.
- `HighlighterCasesTest.kt`: two `@TestFactory`s (static + typing) = 57 dynamic tests.
- `SpecOracle.kt`: `Key`, `oracle(md)`, `ours(md)`, the commonmark `Parser` with the 6 extensions (verbatim from `Harness.kt`).
- `SpecDifferentialTest.kt`: the 652-example differential and the curated-inputs-vs-oracle check.
- `IncrementalFuzzTest.kt`: seed 7 × 3,000 edits, both `update` overloads, 3 assertions.
- `PathologicalTimingTest.kt`: the 14 `Patho.kt` inputs, each `fullScan` < 500 ms.
- `HighlighterApiTest.kt`: `lineStart/lineEnd/lineIndexOf/lineCount/spansForLines/headings/isFenceUnclosed/HighlightDelta`.
- `TestResources.kt`: `internal fun resourceText(name: String): String` (classpath, UTF-8).

Test resources: `core/markdown/src/test/resources/`
- `spec-cases.txt`, `spec-0.31.2.json`, `specseed.md`: `cp` from `plans/reference/markdown/test/resources/`.

## Steps
1. `git status` should be clean. Run `make test` for a green baseline and note the test counts.
2. Port the main sources, changing only the package line:
   ```sh
   D=core/markdown/src/main/kotlin/dev/mdwriter/markdown; mkdir -p $D
   for f in MdModel InlineScanner MarkdownHighlighter; do
     sed 's/^package mdwriter\.markdown$/package dev.mdwriter.markdown/' plans/reference/markdown/$f.kt > $D/$f.kt; done
   grep -n '^package' $D/*.kt        # all three: package dev.mdwriter.markdown
   ./gradlew :core:markdown:compileKotlin
   ```
   Kotlin 2.4.20 may warn where the reference (built with 2.3.10) did not. Fix warnings mechanically only (for example
   `_` for an unused lambda parameter). Keep the public `setText(CharSequence)` (an alias of `fullScan`, used by
   the reference harnesses) and give it KDoc "Same as [fullScan]".
3. Copy the resources: `mkdir -p core/markdown/src/test/resources && cp plans/reference/markdown/test/resources/* core/markdown/src/test/resources/`.
4. Write `HighlighterCases.kt`: copy it verbatim and set the package to `dev.mdwriter.markdown`. The expected strings are
   exact; never edit them. Add `@file:Suppress("ktlint:standard:max-line-length")` because this is test data.
5. Write `TestResources.kt`, `HighlighterCasesTest.kt` (Reference code §A), `SpecOracle.kt` + `SpecDifferentialTest.kt`
   (§B), `IncrementalFuzzTest.kt` (§C), `PathologicalTimingTest.kt` (§D) and `HighlighterApiTest.kt` (§E).
   The `Harness.kt`/`Patho.kt` scripts call `setText`; the tests call `fullScan`, which is the same thing.
6. `./gradlew :core:markdown:test`: everything green, with the counts in Acceptance.
7. Format: `make format` (Spotless + ktlint_official, max 120). Then `./gradlew :core:markdown:test` again. Repeat
   until `./gradlew spotlessCheck` passes. The reference packs code into `;`-joined one-liners, which ktlint splits
   automatically (`statement-wrapping`, `no-semi`). Lines over 120 chars you wrap by hand.
   **Never change logic while formatting:** no reordering conditions, no "simplifying" loops, no renaming internals.
   Re-run the tests after every pass.
   If a rule truly fights the structure (for example `standard:condition-wrapping` on a dense scanner condition), use a
   narrowly scoped `@Suppress("ktlint:standard:<rule-id>")` on that one declaration, with a `// why` comment.
   File-level suppression is only for test data files. Don't edit `.editorconfig` unless more than about 10
   suppressions of the same rule would be needed. If you do, add a section scoped to `core/markdown/**` and record
   it as a Deviation. List every suppression in STATUS.
8. KDoc every public declaration in `MdModel.kt` and `MarkdownHighlighter.kt`, reusing the reference comments.
   Must be stated: threading ("not thread-safe; keeps a reference to the text; main thread after install"), that
   offsets are UTF-16 and that `\n` is the only break, that `spansForLines` `endLine` is **exclusive** and returns absolute
   offsets, and what `HighlightDelta.full` means.
9. `make check`, then the STATUS entry, then commit `T03: port markdown highlighter + JUnit 6 suite`.

## Reference code
All of this is a **sketch**: adapt it, but keep the assertions exactly as written. Put
`package dev.mdwriter.markdown` in every file. Tests are exempt from explicit-API mode.

§A `HighlighterCasesTest` (logic ported from `CasesCheck.kt`)
```kotlin
class HighlighterCasesTest {
    @TestFactory
    fun staticCases(): List<DynamicTest> = HIGHLIGHTER_CASES.map { c ->
        dynamicTest("${c.id} ${c.note}") {
            val h = MarkdownHighlighter(enableHighlight = c.highlight)
            h.fullScan(c.input)
            assertEquals(c.expectedSpans, h.spans().joinToString(" "), "spans")
            assertEquals(c.expectedLines, (0 until h.lineCount).joinToString(" ") { h.lineInfo(it).type.name }, "lines")
        }
    }

    @TestFactory
    fun typingCases(): List<DynamicTest> = TYPING_CASES.map { t ->
        dynamicTest(t.id) {
            val h = MarkdownHighlighter(enableHighlight = false)
            var cur = t.start
            h.fullScan(cur)
            var pos = t.at
            t.typed.forEachIndexed { i, ch ->
                cur = cur.substring(0, pos) + ch + cur.substring(pos)
                val d = h.update(cur, pos, 0, 1)
                pos++
                val ref = MarkdownHighlighter(enableHighlight = false).also { it.fullScan(cur) }
                assertEquals(t.stepSpans[i], h.spans().joinToString(" "), "step $i spans")
                assertEquals(ref.spans(), h.spans(), "step $i incremental == full")
                assertEquals(t.stepFull[i], d.full, "step $i delta.full")
            }
        }
    }
}
```

§B Spec differential. `SpecOracle.kt` is **copied verbatim** from `Harness.kt`: `exts` (6 extensions: tables,
strikethrough, task list, autolink, footnotes, YAML front matter; no alerts/anchors), `cm` (with
`IncludeSourceSpans.BLOCKS_AND_INLINES`), `Key`, `oracle(md)`, `ours(md)`. Wrap them in `internal object SpecOracle`
and replace `h.setText(md)` with `h.fullScan(md)`.
```kotlin
class SpecDifferentialTest {
    private val pinned = setOf(6, 193, 195, 196, 198, 217, 259, 541, 571, 602, 606)
    private val rx = Regex("@@@@ (\\d+) ([^\\n]*)\\n([\\s\\S]*?)\\n@@@@END\\n")   // same as Harness.differential

    @Test
    fun specExamplesAgreeWithCommonmarkJava() {
        var total = 0
        val failures = sortedSetOf<Int>()
        for (m in rx.findAll(resourceText("spec-cases.txt"))) {
            total++
            val md = m.groupValues[3]
            val agree = runCatching { SpecOracle.oracle(md) == SpecOracle.ours(md) }.getOrDefault(false)
            if (!agree) failures += m.groupValues[1].toInt()
        }
        assertEquals(652, total, "spec examples parsed")
        assertTrue(failures.all { it in pinned }, "new disagreements: ${failures - pinned}")
        assertTrue(total - failures.size >= 641, "agree=${total - failures.size}")
    }

    @Test
    fun curatedCasesAgreeWithOracle() {       // reference README: 50/50
        val bad = HIGHLIGHTER_CASES.filter { SpecOracle.oracle(it.input) != SpecOracle.ours(it.input) }.map { it.id }
        assertEquals(emptyList<String>(), bad)
    }
}
```
If `curatedCasesAgreeWithOracle` fails while everything else passes, check the reference README's 50/50 claim with
`ours()` exactly as ported. If it still fails, delete that one test and write down which ids failed in STATUS
(Deviation). Don't touch the highlighter.

§C Fuzz, ported from `Harness.fuzz`: the same seed, alphabet (copy the `alphabet` list **verbatim**), edit mix and delta
check.
```kotlin
class IncrementalFuzzTest {
    @Test
    fun incrementalEqualsFullScanSeed7() {
        val base = resourceText("specseed.md").take(6000)
        val rnd = java.util.Random(7)
        val alphabet = listOf(/* verbatim from Harness.kt */)
        val h = MarkdownHighlighter(); var cur = base; h.fullScan(cur)
        var bad = 0; var badInfo = 0; var deltaMiss = 0; val log = StringBuilder()
        for (r in 0 until 3000) {
            // edit generation, `before`, `d = if (r % 2 == 0) h.update(cur, p, removed, added) else h.update(cur)`,
            // `ref`, the delta-miss check (`it.end > d.endOffset + 1` exactly as in Harness), lineInfo compare,
            // span compare + `h.fullScan(cur)` resync on mismatch: all verbatim; append the first 3 of each to `log`
        }
        assertEquals(0, bad, "span mismatches\n$log")
        assertEquals(0, badInfo, "LineInfo mismatches\n$log")
        assertEquals(0, deltaMiss, "changes outside HighlightDelta\n$log")
    }
}
```

§D `PathologicalTimingTest`
```kotlin
class PathologicalTimingTest {
    private fun cases(n: Int): Map<String, String> = linkedMapOf(/* the 14 entries of Patho.kt, verbatim */)

    @TestFactory
    fun eachInputScansUnder500ms(): List<DynamicTest> {
        cases(500).values.forEach { MarkdownHighlighter().fullScan(it) }            // JIT warm-up
        return cases(20_000).map { (name, md) ->
            dynamicTest(name) {
                val h = MarkdownHighlighter()
                val t0 = System.nanoTime(); h.fullScan(md); val ms = (System.nanoTime() - t0) / 1e6
                h.spans()                                                            // must not throw
                assertTrue(ms < 500.0, "$name len=${md.length} took $ms ms")
            }
        }
    }
}
```

§E `HighlighterApiTest`: each bullet is one `@Test`, and the values are exact.
- `"a\nbb\n\nccc"`: `lineCount == 4`; `lineStart(1) == 2`; `lineEnd(1) == 4`; `lineStart(2) == 5`; `lineEnd(2) == 5`;
  `lineEnd(3) == 9`; `lineIndexOf(0) == 0`, `lineIndexOf(2) == 1`, `lineIndexOf(4) == 1` (the `\n` belongs to its
  line), `lineIndexOf(5) == 2`, `lineIndexOf(9) == 3`; `lineInfoAt(3) == lineInfo(1)`.
- `"# A\n\n*b*"`: `spansForLines(2, 3).map { it.toString() } == listOf("EMPHASIS[5,8)", "EMPHASIS_MARKER[5,6)",
  "EMPHASIS_MARKER[7,8)")` (absolute offsets, same order as `spans()`); `spansForLines(1, 2)` is empty;
  `spansForLines(0, 1)` contains only `HEADING`/`HEADING_MARKER` spans; `spansForLines(0, lineCount).toSet() == spans().toSet()`.
  If the ordering assertion is wrong only because of ordering, compare as sets instead and say so in STATUS.
- `"# A\n\n## B"`: `headings().map { it.level to it.line } == listOf(1 to 0, 2 to 2)`.
- `"```\ncode"`: `isFenceUnclosed(0)`; `"```\ncode\n```"`: `!isFenceUnclosed(0)`.
- Delta: fullScan `"para one\n\npara two"`, then insert `"x"` at offset 3 and `update(new, 3, 0, 1)`. Expect
  `!d.full`, `d.firstLine == 0`, `d.endLine >= 1`, and `d.startOffset <= 3 && d.endOffset >= 4`. `fullScan(...)` of
  any text returns `full == true`.
- No-`android` guard: `assertFalse(File("src/main").walk().any { it.isFile && it.readText().contains("import android.") })`
  (Gradle runs tests with the module directory as the working dir).

## Acceptance criteria
1. `core/markdown/build/test-results/test/TEST-dev.mdwriter.markdown.HighlighterCasesTest.xml` has `tests="57"`
   `failures="0" errors="0"`.
2. `SpecDifferentialTest` passes: 652 examples parsed, failures ⊆ {6,193,195,196,198,217,259,541,571,602,606}, agree ≥ 641.
3. `IncrementalFuzzTest.incrementalEqualsFullScanSeed7` passes with 0 span / 0 LineInfo / 0 outside-delta mismatches.
4. `PathologicalTimingTest` has 14 dynamic tests, all passing (each < 500 ms).
5. `HighlighterApiTest` passes, including the no-`android.` guard.
6. `grep -rn 'import android' core/markdown/src` prints nothing.
7. Only formatting changed. For each of the 3 main files, review
   `git diff --no-index -w --word-diff plans/reference/markdown/<F>.kt core/markdown/src/main/kotlin/dev/mdwriter/markdown/<F>.kt`.
   The only allowed differences are the package line, line breaks, dropped `;`, trailing commas, KDoc/comments and
   `@Suppress` annotations. Record "reviewed: only formatting" in STATUS.
8. `./gradlew spotlessCheck` passes. With a warm daemon, `time ./gradlew :core:markdown:test --rerun` takes < 30 s.
9. `make check` is green.

## Verification commands
```sh
./gradlew :core:markdown:test --rerun
grep -ho 'testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  core/markdown/build/test-results/test/TEST-*.xml
grep -rn 'import android' core/markdown/src || echo "OK: no android imports"
make format && ./gradlew :core:markdown:test && ./gradlew spotlessCheck
time ./gradlew :core:markdown:test --rerun
make test
make check        # if it builds release: use the KEYSTORE_DIR=/tmp/... override from README "Rules for agents"
```
No emulator is needed for this task (pure JVM). Don't run `make install`.

## Pitfalls
- **Package rename:** the reference `test/*.kt` files have **no** package line and `import mdwriter.markdown.*`. Add
  `package dev.mdwriter.markdown` and delete that import, or the tests won't compile.
- **Explicit API:** the main sources must keep `public`/`internal` modifiers. `InlineScanner`, `Mode`, `LineState` and `IntList`
  stay `internal` (reference README). Don't make them public "to fix" a test; tests in the same module see internals.
- **Formatting ≠ refactoring.** The two quadratic-cost fixes (binary-search span distribution, memoised `<!--`/backtick
  scans) look like odd code. They are load-bearing (README "Bugs fixed" 1–2), and `PathologicalTimingTest` catches
  a regression.
- **`MdSpan` vs `MdStyleSpan`:** `MdSpan` is this module's data class. The Android marker interface `MdStyleSpan` is T06's
  (HARD RULE §10.9). Don't add any Android-facing type here (§2: no `android.*`).
- **JUnit 6 on Gradle 9** needs `testRuntimeOnly(libs.junit.platform.launcher)`, which is already in the reference build
  file. Don't remove it. "Failed to load JUnit Platform" means the build file was changed.
- The fuzz test must alternate **both** overloads (`r % 2`). Testing only the 4-arg overload hides diff-fallback bugs.
- `spec-cases.txt` must stay byte-identical (`cmp` with the reference): the regex needs `\n@@@@END\n` and LF endings.
- Warm-up in the timing test is required. A cold JIT can push the first case past 500 ms on a slow machine.
- Don't read `specseed.md` or `spec-0.31.2.json` into your context; just copy them (token budget).

## Definition of done
- [ ] 3 main files + 8 test files + 3 resources in place; package `dev.mdwriter.markdown`; no placeholder left.
- [ ] Acceptance 1–9 met; test counts pasted into the STATUS entry.
- [ ] Every ktlint suppression listed in STATUS (file, rule, reason); any `.editorconfig` change recorded as a Deviation.
- [ ] KDoc on all public API in `MdModel.kt` and `MarkdownHighlighter.kt`.
- [ ] `make check` green.
- [ ] `plans/STATUS.md` T03 entry appended (what, evidence, deviations, what T04/T05/T06 can rely on).
- [ ] Commit `T03: port markdown highlighter + JUnit 6 suite` (no push).
