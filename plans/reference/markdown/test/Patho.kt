import mdwriter.markdown.*
fun main(args: Array<String>) {
    val n = args.getOrNull(0)?.toInt() ?: 20000
    val cases = linkedMapOf(
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
    for ((name, md) in cases) {
        val h = MarkdownHighlighter()
        val t0 = System.nanoTime()
        try { h.setText(md) } catch (e: Throwable) { println("%-24s len=%7d CRASH %s".format(name, md.length, e.toString().take(80))); continue }
        val ms = (System.nanoTime() - t0) / 1e6
        println("%-24s len=%7d full=%.1fms".format(name, md.length, ms))
    }
}
