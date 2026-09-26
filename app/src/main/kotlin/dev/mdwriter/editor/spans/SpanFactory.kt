package dev.mdwriter.editor.spans

import dev.mdwriter.markdown.BlockType
import dev.mdwriter.markdown.LineInfo
import dev.mdwriter.markdown.MarkdownHighlighter
import dev.mdwriter.markdown.MdKind
import dev.mdwriter.markdown.MdSpan

/** One planned span (before it becomes a real Android object): [end] is exclusive, like [MdSpan]. */
data class SpanSpec(
    val kind: Int,
    val arg: Int,
    val start: Int,
    val end: Int,
)

/** Measures a run of [text] in `[start, end)` at BODY size, regular face — used only for hang/indent widths. */
fun interface TextMeasurer {
    fun widthPx(
        text: CharSequence,
        start: Int,
        end: Int,
    ): Int
}

/**
 * Pure Kotlin (no `android.*` import — JVM-testable): turns a [MarkdownHighlighter]'s per-line structure and
 * [MdSpan]s into the [SpanSpec]s the Android-side [SpanMaterializer] turns into real spans. Implements
 * `plans/02-design-spec.md` §4 exactly; see `plans/tasks/T06-styling-spans.md` Reference §C for the rule
 * numbering this file's KDoc follows.
 */
class SpanFactory(
    private val measurer: TextMeasurer,
) {
    /**
     * Specs for lines `[fromLine, endLine)` — endLine EXCLUSIVE, like [MarkdownHighlighter.spansForLines].
     *
     * [MarkdownHighlighter.spansForLines] is sorted by `SPAN_ORDER` (content spans first, across the WHOLE
     * range, then marker spans, each group ordered by start) — NOT by ascending `start` alone. A span's line is
     * therefore found with [MarkdownHighlighter.lineIndexOf] and bucketed, rather than assumed via a single
     * increasing pointer (which would silently drop a heading's own marker into a later line's bucket, since
     * that marker sorts after every content span in the whole range, not just this line's).
     */
    fun specsForLines(
        text: CharSequence,
        hl: MarkdownHighlighter,
        fromLine: Int,
        endLine: Int,
        out: MutableList<SpanSpec>,
    ) {
        if (endLine <= fromLine) return
        val byLine = HashMap<Int, MutableList<MdSpan>>()
        for (span in hl.spansForLines(fromLine, endLine)) {
            byLine.getOrPut(hl.lineIndexOf(span.start)) { ArrayList() } += span
        }
        for (line in fromLine until endLine) {
            specsForLine(text, hl.lineInfo(line), byLine[line] ?: emptyList(), out)
        }
    }

    /** One line; [lineSpans] = that line's [MdSpan]s (sorted by start, as returned by [MarkdownHighlighter.spansForLines]). */
    fun specsForLine(
        text: CharSequence,
        info: LineInfo,
        lineSpans: List<MdSpan>,
        out: MutableList<SpanSpec>,
    ) {
        val paraEnd = minOf(info.end + 1, text.length)

        fun add(
            kind: Int,
            arg: Int,
            start: Int,
            end: Int,
        ) {
            if (end > start) out += SpanSpec(kind, arg, start, end)
        }

        // 1. Line-level, from the block type.
        when (info.type) {
            BlockType.FENCE_OPEN, BlockType.FENCE_CLOSE, BlockType.FENCED_CODE, BlockType.INDENTED_CODE -> {
                add(SpanKind.CODE_BLOCK, 0, info.start, paraEnd)
            }

            BlockType.FRONT_MATTER, BlockType.FRONT_MATTER_FENCE -> {
                add(SpanKind.MONO, 0, info.start, paraEnd)
            }

            else -> {}
        }

        // 2. Hanging indent: quote/list container prefix, or the wrap indent of a quote-lazy-continuation line.
        val hasListMarker = lineSpans.any { it.kind == MdKind.LIST_MARKER }
        if (info.contentStart > info.start && !info.type.isVerbatim &&
            (info.quoteDepth > 0 || info.listDepth > 0 || hasListMarker)
        ) {
            val w = measurer.widthPx(text, info.start, info.contentStart)
            add(SpanKind.HANGING_INDENT, w, info.start, paraEnd)
        }

        // 3. ATX heading: size span starts after the marker run; markers stay body size/colour (C4/C5).
        if (info.type == BlockType.ATX_HEADING) {
            val heading = lineSpans.firstOrNull { it.kind == MdKind.HEADING }
            val open = lineSpans.firstOrNull { it.kind == MdKind.HEADING_MARKER && it.start == heading?.start }
            if (heading != null && open != null) {
                val close = lineSpans.firstOrNull { it.kind == MdKind.HEADING_MARKER && it.start > open.start }
                var contentEnd = close?.start ?: heading.end
                while (contentEnd > open.end && (text[contentEnd - 1] == ' ' || text[contentEnd - 1] == '\t')) {
                    contentEnd--
                }
                add(SpanKind.HEADING, heading.arg, open.end, contentEnd)
                if (info.quoteDepth == 0 && info.listDepth == 0) {
                    val hangWidth = measurer.widthPx(text, info.start, open.end)
                    add(SpanKind.HEADING_HANG, hangWidth, info.start, paraEnd)
                }
            }
        }

        // 4. Setext heading: content line gets the size span; the underline line's marker is greyed.
        if (info.type == BlockType.SETEXT_HEADING) {
            lineSpans.firstOrNull { it.kind == MdKind.HEADING }?.let { h ->
                add(SpanKind.HEADING, h.arg, h.start, h.end)
            }
        }
        if (info.type == BlockType.SETEXT_UNDERLINE) {
            for (m in lineSpans) if (m.kind == MdKind.HEADING_MARKER) add(SpanKind.MARKER, 0, m.start, m.end)
        }

        // 5. Inline / remaining per-MdSpan mapping (exhaustive over MdKind so a new kind fails compile).
        for (span in lineSpans) {
            when (span.kind) {
                // Handled above (rules 3/4) — never re-emitted here.
                MdKind.HEADING, MdKind.HEADING_MARKER -> {}

                MdKind.STRONG -> {
                    add(SpanKind.STRONG, 0, span.start, span.end)
                }

                MdKind.EMPHASIS -> {
                    add(SpanKind.EMPHASIS, 0, span.start, span.end)
                }

                MdKind.STRIKETHROUGH -> {
                    add(SpanKind.STRIKE, 0, span.start, span.end)
                }

                MdKind.HIGHLIGHT -> {
                    add(SpanKind.MARK, 0, span.start, span.end)
                }

                MdKind.CODE_SPAN -> {
                    add(SpanKind.CODE, 0, span.start, span.end)
                }

                MdKind.EMPHASIS_MARKER -> {
                    if (SpanKind.DIM_EMPHASIS_MARKERS || text[span.start] == '~' || text[span.start] == '=') {
                        add(SpanKind.MARKER, 0, span.start, span.end)
                    }
                }

                MdKind.CODE_SPAN_MARKER, MdKind.CODE_FENCE, MdKind.CODE_INFO, MdKind.QUOTE_MARKER,
                MdKind.LINK_MARKER, MdKind.LINK_TITLE, MdKind.LINK_LABEL, MdKind.FOOTNOTE_REF,
                MdKind.THEMATIC_BREAK, MdKind.HTML_BLOCK, MdKind.HTML_INLINE, MdKind.ESCAPE_MARKER,
                MdKind.LINK_DEF, MdKind.TABLE_PIPE, MdKind.FRONT_MATTER, MdKind.FRONT_MATTER_FENCE,
                -> {
                    add(SpanKind.MARKER, 0, span.start, span.end)
                }

                MdKind.LINK_URL -> {
                    val insideAutolink =
                        lineSpans.any {
                            it.kind == MdKind.AUTOLINK && span.start >= it.start && span.end <= it.end
                        }
                    if (!insideAutolink) add(SpanKind.MARKER, 0, span.start, span.end)
                }

                MdKind.AUTOLINK -> {
                    if (text[span.start] == '<') {
                        add(SpanKind.LINK_UNDERLINE, 0, span.start + 1, span.end - 1)
                    } else {
                        add(SpanKind.LINK_UNDERLINE, 0, span.start, span.end)
                    }
                }

                MdKind.TASK_MARKER -> {
                    add(SpanKind.MARKER, 0, span.start, span.end)
                    add(SpanKind.TASK, span.arg, span.start, span.end)
                    if (span.arg == 1) {
                        var textStart = span.end
                        while (textStart < info.end && (text[textStart] == ' ' || text[textStart] == '\t')) {
                            textStart++
                        }
                        add(SpanKind.DONE_TASK, 0, textStart, info.end)
                    }
                }

                MdKind.TABLE_ROW -> {
                    add(SpanKind.TABLE_ROW, span.arg, span.start, span.end)
                    if (span.arg == 1) add(SpanKind.MARKER, 0, span.start, span.end)
                }

                // No span at all (fewer spans = faster; C4): body-coloured markers, and constructs already
                // covered by their containing content span or by rule 1.
                MdKind.BLOCKQUOTE, MdKind.LIST_MARKER, MdKind.LINK, MdKind.LINK_TEXT,
                MdKind.CODE_BLOCK, MdKind.ENTITY, MdKind.HARD_BREAK,
                -> {}
            }
        }
    }
}
