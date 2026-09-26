package dev.mdwriter.editor.spans

import com.google.common.truth.Truth.assertThat
import dev.mdwriter.markdown.MarkdownHighlighter
import org.junit.Test

/**
 * One `@Test` per `plans/02-design-spec.md` §4 row (Acceptance 1): a real [MarkdownHighlighter] + a fake
 * [TextMeasurer] (`{ _, s, e -> (e - s) * 10 }`), asserting the exact [SpanSpec] list in the order
 * [SpanFactory.specsForLine] produces it. Pure JVM (no Robolectric needed — [SpanFactory] has no `android.*`
 * import).
 */
class SpanFactoryTest {
    private val fakeMeasurer = TextMeasurer { _, s, e -> (e - s) * 10 }

    private fun specs(
        text: String,
        enableHighlight: Boolean = true,
        fromLine: Int = 0,
        endLine: Int? = null,
    ): List<SpanSpec> {
        val hl = MarkdownHighlighter(enableHighlight = enableHighlight, enableFrontMatter = true)
        hl.fullScan(text)
        val out = ArrayList<SpanSpec>()
        SpanFactory(fakeMeasurer).specsForLines(text, hl, fromLine, endLine ?: hl.lineCount, out)
        return out
    }

    @Test
    fun atxHeadingContentOnly() {
        assertThat(specs("# Title"))
            .containsExactly(
                SpanSpec(SpanKind.HEADING, 1, 2, 7),
                SpanSpec(SpanKind.HEADING_HANG, 20, 0, 7),
            ).inOrder()
    }

    @Test
    fun emptyAtxHeadingHasNoHeadingSpan() {
        // "# " alone: the trimmed content is empty, so no HEADING span — the marker stays body size (02 §3, C5).
        assertThat(specs("# ")).containsExactly(
            SpanSpec(SpanKind.HEADING_HANG, 20, 0, 2),
        )
    }

    @Test
    fun closingHashesExcluded() {
        assertThat(specs("## Hi ##"))
            .containsExactly(
                SpanSpec(SpanKind.HEADING, 2, 3, 5),
                SpanSpec(SpanKind.HEADING_HANG, 30, 0, 8),
            ).inOrder()
    }

    @Test
    fun setextHeadingAndUnderline() {
        assertThat(specs("Title\n====="))
            .containsExactly(
                SpanSpec(SpanKind.HEADING, 1, 0, 5),
                SpanSpec(SpanKind.MARKER, 0, 6, 11),
            ).inOrder()
    }

    @Test
    fun emphasisMarkersUnstyled() {
        // `* _ ** __` stay text colour (iA-faithful, DIM_EMPHASIS_MARKERS = false): no MARKER on either delimiter.
        assertThat(specs("*a* **b**"))
            .containsExactly(
                SpanSpec(SpanKind.EMPHASIS, 0, 0, 3),
                SpanSpec(SpanKind.STRONG, 0, 4, 9),
            ).inOrder()
    }

    @Test
    fun strikeMarkersGrey() {
        assertThat(specs("~~a~~"))
            .containsExactly(
                SpanSpec(SpanKind.STRIKE, 0, 0, 5),
                SpanSpec(SpanKind.MARKER, 0, 0, 2),
                SpanSpec(SpanKind.MARKER, 0, 3, 5),
            ).inOrder()
    }

    @Test
    fun highlightOnlyWhenEnabled() {
        assertThat(specs("==a==", enableHighlight = true))
            .containsExactly(
                SpanSpec(SpanKind.MARK, 0, 0, 5),
                SpanSpec(SpanKind.MARKER, 0, 0, 2),
                SpanSpec(SpanKind.MARKER, 0, 3, 5),
            ).inOrder()
        assertThat(specs("==a==", enableHighlight = false)).isEmpty()
    }

    @Test
    fun inlineCode() {
        assertThat(specs("`code`"))
            .containsExactly(
                SpanSpec(SpanKind.CODE, 0, 0, 6),
                SpanSpec(SpanKind.MARKER, 0, 0, 1),
                SpanSpec(SpanKind.MARKER, 0, 5, 6),
            ).inOrder()
    }

    @Test
    fun fencedBlockBandsEveryLineIncludingEmpty() {
        val result = specs("```kt\n\nx\n```")
        val codeBlocks = result.filter { it.kind == SpanKind.CODE_BLOCK }
        assertThat(codeBlocks)
            .containsExactly(
                SpanSpec(SpanKind.CODE_BLOCK, 0, 0, 6), // "```kt\n"
                SpanSpec(SpanKind.CODE_BLOCK, 0, 6, 7), // the empty line
                SpanSpec(SpanKind.CODE_BLOCK, 0, 7, 9), // "x\n"
                SpanSpec(SpanKind.CODE_BLOCK, 0, 9, 12), // closing "```"
            ).inOrder()
        assertThat(result)
            .containsExactly(
                SpanSpec(SpanKind.CODE_BLOCK, 0, 0, 6),
                SpanSpec(SpanKind.MARKER, 0, 3, 5), // "kt" info string
                SpanSpec(SpanKind.MARKER, 0, 0, 3), // opening fence "```"
                SpanSpec(SpanKind.CODE_BLOCK, 0, 6, 7),
                SpanSpec(SpanKind.CODE_BLOCK, 0, 7, 9),
                SpanSpec(SpanKind.CODE_BLOCK, 0, 9, 12),
                SpanSpec(SpanKind.MARKER, 0, 9, 12), // closing fence "```"
            ).inOrder()
    }

    @Test
    fun indentedCode() {
        assertThat(specs("    code line")).containsExactly(
            SpanSpec(SpanKind.CODE_BLOCK, 0, 0, 13),
        )
    }

    @Test
    fun quoteMarkerGreyAndHangingIndent() {
        assertThat(specs("> quoted text"))
            .containsExactly(
                SpanSpec(SpanKind.HANGING_INDENT, 20, 0, 13),
                SpanSpec(SpanKind.MARKER, 0, 0, 2),
            ).inOrder()
    }

    @Test
    fun listMarkerPlainWithHangingIndent() {
        // LIST_MARKER itself gets no spec (text colour) — only the hanging indent for the wrapped line.
        assertThat(specs("- item text")).containsExactly(
            SpanSpec(SpanKind.HANGING_INDENT, 20, 0, 11),
        )
    }

    @Test
    fun taskOpen() {
        assertThat(specs("- [ ] open task"))
            .containsExactly(
                SpanSpec(SpanKind.HANGING_INDENT, 60, 0, 15),
                SpanSpec(SpanKind.MARKER, 0, 2, 5),
                SpanSpec(SpanKind.TASK, 0, 2, 5),
            ).inOrder()
    }

    @Test
    fun taskDoneGreyStrike() {
        assertThat(specs("- [x] done task"))
            .containsExactly(
                SpanSpec(SpanKind.HANGING_INDENT, 60, 0, 15),
                SpanSpec(SpanKind.MARKER, 0, 2, 5),
                SpanSpec(SpanKind.TASK, 1, 2, 5),
                SpanSpec(SpanKind.DONE_TASK, 0, 6, 15),
            ).inOrder()
    }

    @Test
    fun inlineLinkSyntaxGreyTextPlain() {
        // "text" (the LINK_TEXT between brackets) gets no spec at all — plain body colour.
        assertThat(specs("[text](https://example.com)"))
            .containsExactly(
                SpanSpec(SpanKind.MARKER, 0, 7, 26), // the URL
                SpanSpec(SpanKind.MARKER, 0, 0, 1), // "["
                SpanSpec(SpanKind.MARKER, 0, 5, 6), // "]"
                SpanSpec(SpanKind.MARKER, 0, 6, 7), // "("
                SpanSpec(SpanKind.MARKER, 0, 26, 27), // ")"
            ).inOrder()
    }

    @Test
    fun autolinkUnderlinedUrlNotGrey() {
        // The inner LINK_URL is NOT also greyed (its range is inside the AUTOLINK) — only `<` `>` are markup.
        assertThat(specs("<https://example.org>"))
            .containsExactly(
                SpanSpec(SpanKind.LINK_UNDERLINE, 0, 1, 20),
                SpanSpec(SpanKind.MARKER, 0, 0, 1),
                SpanSpec(SpanKind.MARKER, 0, 20, 21),
            ).inOrder()
    }

    @Test
    fun bareUrlUnderlined() {
        assertThat(specs("https://example.org")).containsExactly(
            SpanSpec(SpanKind.LINK_UNDERLINE, 0, 0, 19),
        )
    }

    @Test
    fun footnoteHrHtmlEscapeLinkDefGrey() {
        assertThat(specs("***")).containsExactly(
            SpanSpec(SpanKind.MARKER, 0, 0, 3), // THEMATIC_BREAK
        )
        assertThat(specs("[x]: /url \"title\""))
            .containsExactly(
                SpanSpec(SpanKind.MARKER, 0, 0, 17), // LINK_DEF, whole line
                SpanSpec(SpanKind.MARKER, 0, 1, 2), // LINK_LABEL "x"
                SpanSpec(SpanKind.MARKER, 0, 5, 9), // LINK_URL "/url"
                SpanSpec(SpanKind.MARKER, 0, 10, 17), // LINK_TITLE "\"title\""
                SpanSpec(SpanKind.MARKER, 0, 0, 1), // "["
                SpanSpec(SpanKind.MARKER, 0, 2, 4), // "]:"
            ).inOrder()
        assertThat(specs("\\*not emphasis\\*"))
            .containsExactly(
                SpanSpec(SpanKind.MARKER, 0, 0, 1), // first ESCAPE_MARKER backslash
                SpanSpec(SpanKind.MARKER, 0, 14, 15), // second ESCAPE_MARKER backslash
            ).inOrder()
        assertThat(specs("text[^1]\n\n[^1]: note"))
            .containsExactly(
                SpanSpec(SpanKind.MARKER, 0, 4, 8), // FOOTNOTE_REF
                SpanSpec(SpanKind.MARKER, 0, 10, 14), // FOOTNOTE_REF (arg 1, the definition's own label)
                SpanSpec(SpanKind.MARKER, 0, 14, 15), // ":" LINK_MARKER
            ).inOrder()
    }

    @Test
    fun tableRowsAndPipes() {
        assertThat(specs("| a | b |\n|---|---|\n| 1 | 2 |"))
            .containsExactly(
                SpanSpec(SpanKind.TABLE_ROW, 0, 0, 9), // header
                SpanSpec(SpanKind.MARKER, 0, 0, 1),
                SpanSpec(SpanKind.MARKER, 0, 4, 5),
                SpanSpec(SpanKind.MARKER, 0, 8, 9),
                SpanSpec(SpanKind.TABLE_ROW, 1, 10, 19), // delimiter
                SpanSpec(SpanKind.MARKER, 0, 10, 19), // delimiter row also greyed
                SpanSpec(SpanKind.MARKER, 0, 10, 11),
                SpanSpec(SpanKind.MARKER, 0, 14, 15),
                SpanSpec(SpanKind.MARKER, 0, 18, 19),
                SpanSpec(SpanKind.TABLE_ROW, 2, 20, 29), // body
                SpanSpec(SpanKind.MARKER, 0, 20, 21),
                SpanSpec(SpanKind.MARKER, 0, 24, 25),
                SpanSpec(SpanKind.MARKER, 0, 28, 29),
            ).inOrder()
    }

    @Test
    fun frontMatterMonoGrey() {
        assertThat(specs("---\ntitle: x\n---\nbody"))
            .containsExactly(
                SpanSpec(SpanKind.MONO, 0, 0, 4), // "---\n"
                SpanSpec(SpanKind.MARKER, 0, 0, 3), // opening "---"
                SpanSpec(SpanKind.MONO, 0, 4, 13), // "title: x\n"
                SpanSpec(SpanKind.MARKER, 0, 4, 12), // "title: x"
                SpanSpec(SpanKind.MONO, 0, 13, 17), // closing "---\n"
                SpanSpec(SpanKind.MARKER, 0, 13, 16), // closing "---"
            ).inOrder()
        // "body" (outside the front matter) gets nothing.
        assertThat(specs("---\ntitle: x\n---\nbody").none { it.start >= 17 }).isTrue()
    }

    @Test
    fun entityAndHardBreakUnstyled() {
        assertThat(specs("a &amp; b  \nnext")).isEmpty()
    }

    @Test
    fun h6Heading() {
        assertThat(specs("###### H6"))
            .containsExactly(
                SpanSpec(SpanKind.HEADING, 6, 7, 9),
                SpanSpec(SpanKind.HEADING_HANG, 70, 0, 9),
            ).inOrder()
    }

    @Test
    fun multiLineRangeGrouping() {
        val text = "**a**\n*b*\n`c`"
        assertThat(specs(text, fromLine = 0, endLine = 3))
            .containsExactly(
                SpanSpec(SpanKind.STRONG, 0, 0, 5),
                SpanSpec(SpanKind.EMPHASIS, 0, 6, 9),
                SpanSpec(SpanKind.CODE, 0, 10, 13),
                SpanSpec(SpanKind.MARKER, 0, 10, 11),
                SpanSpec(SpanKind.MARKER, 0, 12, 13),
            ).inOrder()
        // specsForLines(1, 3) must return only lines 1-2: no STRONG from line 0.
        assertThat(specs(text, fromLine = 1, endLine = 3))
            .containsExactly(
                SpanSpec(SpanKind.EMPHASIS, 0, 6, 9),
                SpanSpec(SpanKind.CODE, 0, 10, 13),
                SpanSpec(SpanKind.MARKER, 0, 10, 11),
                SpanSpec(SpanKind.MARKER, 0, 12, 13),
            ).inOrder()
    }

    @Test
    fun dimEmphasisMarkersIsFalse() {
        assertThat(SpanKind.DIM_EMPHASIS_MARKERS).isFalse()
    }
}
