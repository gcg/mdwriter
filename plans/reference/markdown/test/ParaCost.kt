import mdwriter.markdown.*
fun main() {
    for (lines in listOf(100, 500, 2000, 5000)) {
        val md = "Intro para.\n\n" + (0 until lines).joinToString("\n") { "Line $it of a long paragraph with *some* words and **bold** text" } + "\n\nTail.\n"
        val h = MarkdownHighlighter(); h.setText(md)
        var cur = md
        val pos = md.indexOf("Line ${lines / 2} ") + 5
        repeat(300) { cur = cur.substring(0, pos) + "x" + cur.substring(pos); h.update(cur); cur = cur.substring(0, pos) + cur.substring(pos + 1); h.update(cur) }
        val t = ArrayList<Double>()
        repeat(200) { cur = cur.substring(0, pos) + "x" + cur.substring(pos); val s = System.nanoTime(); h.update(cur); t += (System.nanoTime() - s) / 1e6 }
        t.sort()
        println("paragraph lines=%5d chars=%7d incr p50=%.3fms p95=%.3fms rescanned=%d".format(lines, md.length, t[100], t[190], h.lastRescannedLines))
    }
}
