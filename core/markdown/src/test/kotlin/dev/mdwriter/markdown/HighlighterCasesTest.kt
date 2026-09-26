package dev.mdwriter.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/** Ports [CasesCheck.kt]'s plain-`main` runner of `HIGHLIGHTER_CASES` / `TYPING_CASES` into JUnit 6. */
class HighlighterCasesTest {
    @TestFactory
    fun staticCases(): List<DynamicTest> =
        HIGHLIGHTER_CASES.map { c ->
            dynamicTest("${c.id} ${c.note}") {
                val h = MarkdownHighlighter(enableHighlight = c.highlight)
                h.fullScan(c.input)
                assertEquals(c.expectedSpans, h.spans().joinToString(" "), "spans")
                assertEquals(
                    c.expectedLines,
                    (0 until h.lineCount).joinToString(" ") { h.lineInfo(it).type.name },
                    "lines",
                )
            }
        }

    @TestFactory
    fun typingCases(): List<DynamicTest> =
        TYPING_CASES.map { t ->
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
