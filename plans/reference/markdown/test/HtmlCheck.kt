import mdwriter.markdown.*
fun main() {
    val md = "---\ntitle: T\n---\n# Head *x*\n\n| a | b |\n|:-|-:|\n| 1 | 2 |\n\n- [x] done\n\nNote[^1] ~~s~~ www.x.com\n\n[^1]: fn\n\n<script>alert(1)</script>\n\n[js](javascript:alert(1)) ![i](img/a.png)\n\n> [!NOTE]\n> alert\n"
    val html = MarkdownHtml()
    val page = html.renderPage(md, "dark", mapOf("--font-size" to "18px"), "T")
    println(page)
    val t0 = System.nanoTime(); val big = java.io.File(argsOrDefault()).readText(); repeat(20) { html.renderBody(big) }
    val t1 = System.nanoTime(); repeat(20) { html.renderBody(big) }; println("renderBody 100KB: %.2f ms".format((System.nanoTime() - t1) / 1e6 / 20))
}
fun argsOrDefault() = "exp/doc100k.md"
