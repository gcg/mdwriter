package dev.mdwriter.markdown

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.StrongEmphasis
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/**
 * commonmark-java oracle for the CommonMark spec differential, ported verbatim from `Harness.kt`
 * (`exts`, `cm`, `Key`, `oracle`, `ours`) with `h.setText(md)` replaced by `h.fullScan(md)`.
 */
internal object SpecOracle {
    val exts: List<Extension> =
        listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create(),
            AutolinkExtension.create(),
            FootnotesExtension.create(),
            YamlFrontMatterExtension.create(),
        )
    val cm: Parser =
        Parser
            .builder()
            .extensions(exts)
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build()

    data class Key(
        val kind: String,
        val start: Int,
        val end: Int,
    )

    fun oracle(md: String): Triple<Set<Key>, Set<Key>, Set<Int>> {
        val inl = HashSet<Key>()
        val heads = HashSet<Key>()
        val codeLines = HashSet<Int>()
        val lines = md.split("\n")

        fun walk(n: Node) {
            when (n) {
                is Emphasis -> {
                    n.sourceSpans.forEach { inl += Key("EMPHASIS", it.inputIndex, it.inputIndex + it.length) }
                }

                is StrongEmphasis -> {
                    n.sourceSpans.forEach {
                        inl +=
                            Key("STRONG", it.inputIndex, it.inputIndex + it.length)
                    }
                }

                is Strikethrough -> {
                    n.sourceSpans.forEach {
                        inl +=
                            Key("STRIKETHROUGH", it.inputIndex, it.inputIndex + it.length)
                    }
                }

                is Code -> {
                    n.sourceSpans.forEach { inl += Key("CODE_SPAN", it.inputIndex, it.inputIndex + it.length) }
                }

                is Link -> {
                    n.sourceSpans.forEach { inl += Key("LINK", it.inputIndex, it.inputIndex + it.length) }
                }

                is Image -> {
                    n.sourceSpans.forEach { inl += Key("LINK", it.inputIndex, it.inputIndex + it.length) }
                }

                is Heading -> {
                    val sp = n.sourceSpans
                    val use = if (sp.size > 1) sp.dropLast(1) else sp
                    use.forEach { heads += Key("H${n.level}", it.lineIndex, it.lineIndex) }
                }

                is FencedCodeBlock, is IndentedCodeBlock -> {
                    n.sourceSpans.forEach {
                        if (lines.getOrNull(it.lineIndex)?.isNotBlank() == true) codeLines += it.lineIndex
                    }
                }

                else -> {}
            }
            var c = n.firstChild
            while (c != null) {
                walk(c)
                c = c.next
            }
        }
        walk(cm.parse(md))
        return Triple(inl, heads, codeLines)
    }

    fun ours(md: String): Triple<Set<Key>, Set<Key>, Set<Int>> {
        val h = MarkdownHighlighter(enableHighlight = false)
        h.fullScan(md)
        val inl = HashSet<Key>()
        val heads = HashSet<Key>()
        val codeLines = HashSet<Int>()
        val lineStartsList =
            ArrayList<Int>().apply {
                add(0)
                md.forEachIndexed { i, c -> if (c == '\n') add(i + 1) }
            }

        fun lineOf(off: Int): Int {
            var lo = 0
            var hi = lineStartsList.size - 1
            while (lo < hi) {
                val m = (lo + hi + 1) / 2
                if (lineStartsList[m] <= off) lo = m else hi = m - 1
            }
            return lo
        }
        for (sp in h.spans()) {
            when (sp.kind) {
                MdKind.EMPHASIS, MdKind.STRONG, MdKind.STRIKETHROUGH, MdKind.CODE_SPAN, MdKind.LINK -> {
                    inl += Key(sp.kind.name, sp.start, sp.end)
                }

                MdKind.AUTOLINK -> {
                    inl += Key("LINK", sp.start, sp.end)
                }

                MdKind.HEADING -> {
                    heads += Key("H${sp.arg}", lineOf(sp.start), lineOf(sp.start))
                }

                MdKind.CODE_BLOCK, MdKind.CODE_FENCE -> {
                    if (md.substring(h.lineStart(lineOf(sp.start)), h.lineEnd(lineOf(sp.start))).isNotBlank()) {
                        codeLines += lineOf(sp.start)
                    }
                }

                else -> {}
            }
        }
        return Triple(inl, heads, codeLines)
    }
}
