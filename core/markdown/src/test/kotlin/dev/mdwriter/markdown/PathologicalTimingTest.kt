package dev.mdwriter.markdown

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/** Ports `Patho.kt`'s 14 pathological inputs into a timing test (each must scan well under 500 ms). */
class PathologicalTimingTest {
    private fun cases(n: Int): Map<String, String> =
        linkedMapOf(
            "html-comment-unclosed" to "x <!--".repeat(n),
            "lt-run" to "<".repeat(n),
            "emph-a**b" to "a**b" + "c* ".repeat(n),
            "backtick-runs" to (1..n).joinToString(" ") { "`".repeat(1 + it % 50) },
            "brackets-open" to "[".repeat(n) + "a",
            "link-title-unclosed" to "[a](b \"".repeat(n),
            "tag-quote-unclosed" to "<a href=\"".repeat(n),
            "star-nest" to "*a ".repeat(n) + " b*".repeat(n),
            "image-nest" to "![".repeat(n) + "x" + "](u)".repeat(n),
            "underscores" to "_a ".repeat(n),
            "many-lines-para" to "word **x\n".repeat(n),
            "table-wide" to "|" + "a|".repeat(n) + "\n|" + "-|".repeat(n) + "\n",
            "quote-deep" to ">".repeat(n) + " x",
            "list-deep" to (0 until minOf(n, 2000)).joinToString("\n") { " ".repeat(it * 2) + "- x" },
        )

    @TestFactory
    fun eachInputScansUnder500ms(): List<DynamicTest> {
        cases(500).values.forEach { MarkdownHighlighter().fullScan(it) } // JIT warm-up
        return cases(20_000).map { (name, md) ->
            dynamicTest(name) {
                val h = MarkdownHighlighter()
                val t0 = System.nanoTime()
                h.fullScan(md)
                val ms = (System.nanoTime() - t0) / 1e6
                h.spans() // must not throw
                assertTrue(ms < 500.0, "$name len=${md.length} took $ms ms")
            }
        }
    }
}
