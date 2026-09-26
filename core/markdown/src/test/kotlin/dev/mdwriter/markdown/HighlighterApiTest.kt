package dev.mdwriter.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Exercises the small surface not covered by the curated cases: line lookup, [spansForLines], headings, deltas. */
class HighlighterApiTest {
    @Test
    fun lineLookup() {
        val h = MarkdownHighlighter()
        h.fullScan("a\nbb\n\nccc")
        assertEquals(4, h.lineCount)
        assertEquals(2, h.lineStart(1))
        assertEquals(4, h.lineEnd(1))
        assertEquals(5, h.lineStart(2))
        assertEquals(5, h.lineEnd(2))
        assertEquals(9, h.lineEnd(3))
        assertEquals(0, h.lineIndexOf(0))
        assertEquals(1, h.lineIndexOf(2))
        assertEquals(1, h.lineIndexOf(4)) // the '\n' belongs to its line
        assertEquals(2, h.lineIndexOf(5))
        assertEquals(3, h.lineIndexOf(9))
        assertEquals(h.lineInfo(1), h.lineInfoAt(3))
    }

    @Test
    fun spansForLinesIsAbsoluteAndEndExclusive() {
        val h = MarkdownHighlighter()
        h.fullScan("# A\n\n*b*")
        assertEquals(
            listOf("EMPHASIS[5,8)", "EMPHASIS_MARKER[5,6)", "EMPHASIS_MARKER[7,8)"),
            h.spansForLines(2, 3).map { it.toString() },
        )
        assertTrue(h.spansForLines(1, 2).isEmpty())
        assertTrue(h.spansForLines(0, 1).all { it.kind == MdKind.HEADING || it.kind == MdKind.HEADING_MARKER })
        assertEquals(h.spans().toSet(), h.spansForLines(0, h.lineCount).toSet())
    }

    @Test
    fun headingsListsLevelAndLine() {
        val h = MarkdownHighlighter()
        h.fullScan("# A\n\n## B")
        assertEquals(listOf(1 to 0, 2 to 2), h.headings().map { it.level to it.line })
    }

    @Test
    fun fenceUnclosedDetection() {
        val open = MarkdownHighlighter()
        open.fullScan("```\ncode")
        assertTrue(open.isFenceUnclosed(0))

        val closed = MarkdownHighlighter()
        closed.fullScan("```\ncode\n```")
        assertFalse(closed.isFenceUnclosed(0))
    }

    @Test
    fun deltaReportsChangedRegion() {
        val h = MarkdownHighlighter()
        val full = h.fullScan("para one\n\npara two")
        assertTrue(full.full)

        val edited = "parxa one\n\npara two"
        val d = h.update(edited, 3, 0, 1)
        assertFalse(d.full)
        assertEquals(0, d.firstLine)
        assertTrue(d.endLine >= 1)
        assertTrue(d.startOffset <= 3 && d.endOffset >= 4)
    }

    @Test
    fun noAndroidImportsInMainSources() {
        assertFalse(
            File("src/main").walk().any { it.isFile && it.readText().contains("import android.") },
        )
    }
}
