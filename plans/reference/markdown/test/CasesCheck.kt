import mdwriter.markdown.*

/** Plain-main runner of HIGHLIGHTER_CASES / TYPING_CASES (the JUnit 6 version is a @TestFactory over the same lists). */
fun main() {
    var fail = 0
    for (c in HIGHLIGHTER_CASES) {
        val h = MarkdownHighlighter(enableHighlight = c.highlight); h.fullScan(c.input)
        val spans = h.spans().joinToString(" ")
        val lines = (0 until h.lineCount).joinToString(" ") { h.lineInfo(it).type.name }
        if (spans != c.expectedSpans || lines != c.expectedLines) { fail++; println("FAIL ${c.id}: got spans=$spans lines=$lines") }
    }
    for (t in TYPING_CASES) {
        val h = MarkdownHighlighter(enableHighlight = false); var cur = t.start; h.fullScan(cur); var pos = t.at
        t.typed.forEachIndexed { i, ch ->
            cur = cur.substring(0, pos) + ch + cur.substring(pos)
            val d = h.update(cur, pos, 0, 1); pos++
            val ref = MarkdownHighlighter(enableHighlight = false).also { it.fullScan(cur) }
            val got = h.spans().joinToString(" ")
            if (got != t.stepSpans[i] || h.spans() != ref.spans() || d.full != t.stepFull[i]) { fail++; println("FAIL ${t.id} step $i: $got full=${d.full}") }
        }
    }
    println("CASES: ${HIGHLIGHTER_CASES.size} static + ${TYPING_CASES.size} typing sequences, failures=$fail")
}
