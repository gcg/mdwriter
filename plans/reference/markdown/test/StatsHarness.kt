import mdwriter.markdown.*
import java.io.File
fun st(md: String): Stats { val h = MarkdownHighlighter(); h.setText(md); return TextStats.compute(md, 0, md.length, h.spans()) }
fun main(args: Array<String>) {
    val cases = listOf(
        "Hello world", "# Heading with **bold** text", "[link text](https://example.com/a/b \"Title\")",
        "- [x] done task\n- [ ] open task", "don't stop e-mail 3.14 1,000 snake_case", "日本語のテキスト", "中文 English 混合",
        "한국어 단어 세 개", "Café naïve résumé", "`inline code` and\n\n```kotlin\nval x = 1\n```", "---\ntitle: Front matter words\n---\nBody only.",
        "<!-- hidden comment --> visible", "Emoji 👍🏽 test", "Two sentences. Here! Right?", "[^1] footnote\n\n[^1]: note text",
        "&amp; &copy; entity", "[ref]: http://x.y \"t\"", "<div>\nInside div text\n</div>", "สวัสดีครับ ภาษาไทย",
    )
    println("| Input | words | chars | charsNoSpaces | sentences | tasks(done) | read min |")
    println("|---|---|---|---|---|---|---|")
    for (c in cases) { val s = st(c); println("| `${c.replace("\n", "⏎")}` | ${s.words} | ${s.chars} | ${s.charsNoSpaces} | ${s.sentences} | ${s.tasks}(${s.tasksDone}) | ${s.readingMinutesRounded()} |") }
    for (f in args) {
        val md = File(f).readText(); val h = MarkdownHighlighter(); h.setText(md); val sp = h.spans()
        repeat(20) { TextStats.compute(md, 0, md.length, sp) }
        val t = System.nanoTime(); repeat(20) { TextStats.compute(md, 0, md.length, sp) }
        println("STATS BENCH $f chars=${md.length} %.2fms/full pass".format((System.nanoTime() - t) / 1e6 / 20))
    }
}
