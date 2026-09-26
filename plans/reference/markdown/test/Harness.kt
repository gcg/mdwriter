import mdwriter.markdown.*
import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.*
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import java.io.File

fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t") + "\""

fun dump(md: String) {
    val h = MarkdownHighlighter()
    h.setText(md)
    println("### ${q(md)}")
    for (sp in h.spans()) println("  $sp ${q(md.substring(sp.start, sp.end))}")
}

val exts: List<Extension> = listOf(
    TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(),
    AutolinkExtension.create(), FootnotesExtension.create(), YamlFrontMatterExtension.create(),
)
val cm: Parser = Parser.builder().extensions(exts).includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build()

data class Key(val kind: String, val start: Int, val end: Int)

fun oracle(md: String): Triple<Set<Key>, Set<Key>, Set<Int>> {
    val inl = HashSet<Key>(); val heads = HashSet<Key>(); val codeLines = HashSet<Int>()
    val lines = md.split("\n")
    fun walk(n: Node) {
        when (n) {
            is Emphasis -> n.sourceSpans.forEach { inl += Key("EMPHASIS", it.inputIndex, it.inputIndex + it.length) }
            is StrongEmphasis -> n.sourceSpans.forEach { inl += Key("STRONG", it.inputIndex, it.inputIndex + it.length) }
            is Strikethrough -> n.sourceSpans.forEach { inl += Key("STRIKETHROUGH", it.inputIndex, it.inputIndex + it.length) }
            is Code -> n.sourceSpans.forEach { inl += Key("CODE_SPAN", it.inputIndex, it.inputIndex + it.length) }
            is Link -> n.sourceSpans.forEach { inl += Key("LINK", it.inputIndex, it.inputIndex + it.length) }
            is Image -> n.sourceSpans.forEach { inl += Key("LINK", it.inputIndex, it.inputIndex + it.length) }
            is Heading -> {
                val sp = n.sourceSpans
                val use = if (sp.size > 1) sp.dropLast(1) else sp
                use.forEach { heads += Key("H${n.level}", it.lineIndex, it.lineIndex) }
            }
            is FencedCodeBlock, is IndentedCodeBlock -> n.sourceSpans.forEach {
                if (lines.getOrNull(it.lineIndex)?.isNotBlank() == true) codeLines += it.lineIndex
            }
            else -> {}
        }
        var c = n.firstChild
        while (c != null) { walk(c); c = c.next }
    }
    walk(cm.parse(md))
    return Triple(inl, heads, codeLines)
}

fun ours(md: String): Triple<Set<Key>, Set<Key>, Set<Int>> {
    val h = MarkdownHighlighter(enableHighlight = false)
    h.setText(md)
    val inl = HashSet<Key>(); val heads = HashSet<Key>(); val codeLines = HashSet<Int>()
    val lineStartsList = ArrayList<Int>().apply { add(0); md.forEachIndexed { i, c -> if (c == '\n') add(i + 1) } }
    fun lineOf(off: Int): Int { var lo = 0; var hi = lineStartsList.size - 1; while (lo < hi) { val m = (lo + hi + 1) / 2; if (lineStartsList[m] <= off) lo = m else hi = m - 1 }; return lo }
    for (sp in h.spans()) {
        when (sp.kind) {
            MdKind.EMPHASIS, MdKind.STRONG, MdKind.STRIKETHROUGH, MdKind.CODE_SPAN, MdKind.LINK -> inl += Key(sp.kind.name, sp.start, sp.end)
            MdKind.AUTOLINK -> inl += Key("LINK", sp.start, sp.end)
            MdKind.HEADING -> heads += Key("H${sp.arg}", lineOf(sp.start), lineOf(sp.start))
            MdKind.CODE_BLOCK, MdKind.CODE_FENCE -> if (md.substring(h.lineStart(lineOf(sp.start)), h.lineEnd(lineOf(sp.start))).isNotBlank()) codeLines += lineOf(sp.start)
            else -> {}
        }
    }
    return Triple(inl, heads, codeLines)
}

fun differential(specFile: String, verbose: Boolean) {
    val text = File(specFile).readText()
    val rx = Regex("@@@@ (\\d+) ([^\\n]*)\\n([\\s\\S]*?)\\n@@@@END\\n")
    var total = 0; var okInl = 0; var okHead = 0; var okCode = 0; var okAll = 0
    val failBySection = HashMap<String, Int>(); val totalBySection = HashMap<String, Int>()
    for (m in rx.findAll(text)) {
        val ex = m.groupValues[1].toInt(); val section = m.groupValues[2]; val md = m.groupValues[3]
        total++
        totalBySection[section] = (totalBySection[section] ?: 0) + 1
        val o = try { oracle(md) } catch (e: Throwable) { continue }
        val u = try { ours(md) } catch (e: Throwable) { println("CRASH ex $ex: $e"); e.printStackTrace(); continue }
        val a = o.first == u.first; val b = o.second == u.second; val c = o.third == u.third
        if (a) okInl++; if (b) okHead++; if (c) okCode++
        if (a && b && c) okAll++ else {
            failBySection[section] = (failBySection[section] ?: 0) + 1
            if (verbose) {
                println("--- ex $ex [$section] ${q(md)}")
                if (!a) println("   inline oracle-ours=${o.first - u.first} ours-oracle=${u.first - o.first}")
                if (!b) println("   heads  oracle=${o.second} ours=${u.second}")
                if (!c) println("   code   oracle=${o.third} ours=${u.third}")
            }
        }
    }
    println("SPEC DIFFERENTIAL: total=$total allAgree=$okAll inlineAgree=$okInl headingAgree=$okHead codeLinesAgree=$okCode")
    for ((s, t) in totalBySection.entries.sortedBy { it.key }) println("   %-45s %3d/%3d agree".format(s, t - (failBySection[s] ?: 0), t))
}

fun bench(file: String) {
    val md = File(file).readText()
    val h = MarkdownHighlighter()
    repeat(40) { h.setText(md); h.spans() }
    val t0 = System.nanoTime(); val iters = 20
    repeat(iters) { h.setText(md) }
    val full = (System.nanoTime() - t0) / 1e6 / iters
    val tf = System.nanoTime(); repeat(iters) { h.spans() }; val flat = (System.nanoTime() - tf) / 1e6 / iters
    println("  flatten+sort spans(): %.2fms".format(flat))
    val spans = h.spans().size
    // incremental: type characters in the middle of the document, one at a time
    val sb = StringBuilder(md)
    var pos = md.length / 2
    while (sb[pos] != '\n') pos++ // end of a line
    val typed = " and **more"
    h.setText(sb)
    var cur = sb.toString()
    // warm-up
    repeat(200) { i -> val c = typed[i % typed.length]; cur = cur.substring(0, pos) + c + cur.substring(pos); h.update(cur); cur = cur.substring(0, pos) + cur.substring(pos + 1); h.update(cur) }
    val times = ArrayList<Double>(); var maxLines = 0
    val times2 = ArrayList<Double>()
    for (i in 0 until 400) {
        val c = typed[i % typed.length]
        val at = pos + (i % 50)
        val next = cur.substring(0, at) + c + cur.substring(at)
        if (i % 2 == 0) { val s = System.nanoTime(); h.update(next, at, 0, 1); times2 += (System.nanoTime() - s) / 1e6 }
        else { val s = System.nanoTime(); h.update(next); times += (System.nanoTime() - s) / 1e6 }
        maxLines = maxOf(maxLines, h.lastRescannedLines)
        cur = next
    }
    times.sort(); times2.sort()
    println("  change-info API update(): p50=%.3fms p95=%.3fms max=%.3fms".format(times2[times2.size / 2], times2[(times2.size * 95) / 100], times2.last()))
    // worst case: open an unclosed fence at the top
    val fenced = "```\n" + cur
    h.setText(cur)
    val s2 = System.nanoTime(); h.update(fenced); val fenceMs = (System.nanoTime() - s2) / 1e6
    val fenceLines = h.lastRescannedLines
    // correctness: incremental result must equal full result
    val h2 = MarkdownHighlighter(); h2.setText(fenced)
    val eq = h.spans() == h2.spans()
    println("BENCH %s chars=%d lines=%d spans=%d full=%.2fms diff-API incr p50=%.3fms p95=%.3fms max=%.3fms maxRescannedLines=%d openFenceAtTop=%.2fms(%d lines) incrementalEqualsFull=%s".format(
        file, md.length, h2.lineCount, spans, full, times[times.size / 2], times[(times.size * 95) / 100], times.last(), maxLines, fenceMs, fenceLines, eq))
}

/** Random edit fuzz: incremental result must always equal a fresh full highlight. */
fun fuzz(seedFile: String, rounds: Int) {
    val base = File(seedFile).readText().take(6000)
    val rnd = java.util.Random(7)
    val alphabet = listOf("*", "**", "_", "`", "```", "\n", "\n\n", "# ", "- ", "1. ", "> ", "[", "](u)", "|", "---", "~~", " ", "a", "word ", "<b>", "<!--", "-->", "    ", "[x]: http://x", "[x]", "===", "- [ ] ", "\\")
    val h = MarkdownHighlighter()
    var cur = base
    h.setText(cur)
    var bad = 0; var badInfo = 0; var deltaMiss = 0
    for (r in 0 until rounds) {
        val p = rnd.nextInt(cur.length + 1)
        var removed = 0; var added = 0
        cur = if (rnd.nextInt(3) == 0 && cur.isNotEmpty()) {
            val e = minOf(cur.length, p + rnd.nextInt(8)); removed = e - p; cur.substring(0, p) + cur.substring(e)
        } else { val ins = alphabet[rnd.nextInt(alphabet.size)]; added = ins.length; cur.substring(0, p) + ins + cur.substring(p) }
        val before = h.spans().toSet()
        val d = if (r % 2 == 0) h.update(cur, p, removed, added) else h.update(cur)
        val ref = MarkdownHighlighter(); ref.setText(cur)
        // every span that changed must lie inside the reported delta
        if (!d.full) {
            val after = h.spans().toSet()
            val shift = added - removed
            val moved = before.map { if (it.start >= p + removed) MdSpan(it.kind, it.start + shift, it.end + shift, it.arg) else it }.toSet()
            val gone = (moved - after).filter { !(it.start < p + removed && it.end > p) } // skip spans that overlapped the deleted text (old coords)
            val changed = (after - moved) + gone
            val outside = changed.filter { it.start < d.startOffset || it.end > d.endOffset + 1 }
            if (outside.isNotEmpty()) { deltaMiss++; if (deltaMiss <= 3) println("DELTA MISS r=$r edit@$p -$removed +$added delta=$d outside=${outside.take(4)}") }
        }
        if ((0 until ref.lineCount).any { ref.lineInfo(it) != h.lineInfo(it) }) badInfo++
        if (h.spans() != ref.spans()) {
            bad++
            if (bad <= 3) {
                val a = h.spans().toSet(); val b = ref.spans().toSet()
                println("FUZZ MISMATCH round $r: incr-full=${(a - b).take(5)} full-incr=${(b - a).take(5)}")
            }
            h.setText(cur)
        }
    }
    println("FUZZ rounds=$rounds spanMismatches=$bad lineInfoMismatches=$badInfo changesOutsideDelta=$deltaMiss")
}

fun main(args: Array<String>) {
    when (args[0]) {
        "dump" -> File(args[1]).readText().split("\n=====\n").forEach { dump(it.replace("\\t", "\t")) }
        "diff" -> differential(args[1], args.size > 2)
        "bench" -> args.drop(1).forEach { bench(it) }
        "fuzz" -> fuzz(args[1], args[2].toInt())
    }
}
