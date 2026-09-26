package dev.mdwriter.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Ports `Harness.fuzz`: random edit fuzz, incremental result must always equal a fresh full highlight. */
class IncrementalFuzzTest {
    @Test
    fun incrementalEqualsFullScanSeed7() {
        val base = resourceText("specseed.md").take(6000)
        val rnd = java.util.Random(7)
        val alphabet =
            listOf(
                "*",
                "**",
                "_",
                "`",
                "```",
                "\n",
                "\n\n",
                "# ",
                "- ",
                "1. ",
                "> ",
                "[",
                "](u)",
                "|",
                "---",
                "~~",
                " ",
                "a",
                "word ",
                "<b>",
                "<!--",
                "-->",
                "    ",
                "[x]: http://x",
                "[x]",
                "===",
                "- [ ] ",
                "\\",
            )
        val h = MarkdownHighlighter()
        var cur = base
        h.fullScan(cur)
        var bad = 0
        var badInfo = 0
        var deltaMiss = 0
        val log = StringBuilder()
        for (r in 0 until 3000) {
            val p = rnd.nextInt(cur.length + 1)
            var removed = 0
            var added = 0
            cur =
                if (rnd.nextInt(3) == 0 && cur.isNotEmpty()) {
                    val e = minOf(cur.length, p + rnd.nextInt(8))
                    removed = e - p
                    cur.substring(0, p) + cur.substring(e)
                } else {
                    val ins = alphabet[rnd.nextInt(alphabet.size)]
                    added = ins.length
                    cur.substring(0, p) + ins + cur.substring(p)
                }
            val before = h.spans().toSet()
            val d = if (r % 2 == 0) h.update(cur, p, removed, added) else h.update(cur)
            val ref = MarkdownHighlighter()
            ref.fullScan(cur)
            // every span that changed must lie inside the reported delta
            if (!d.full) {
                val after = h.spans().toSet()
                val shift = added - removed
                val moved =
                    before
                        .map {
                            if (it.start >= p + removed) {
                                MdSpan(it.kind, it.start + shift, it.end + shift, it.arg)
                            } else {
                                it
                            }
                        }.toSet()
                // skip spans that overlapped the deleted text (old coords)
                val gone = (moved - after).filter { !(it.start < p + removed && it.end > p) }
                val changed = (after - moved) + gone
                val outside = changed.filter { it.start < d.startOffset || it.end > d.endOffset + 1 }
                if (outside.isNotEmpty()) {
                    deltaMiss++
                    if (deltaMiss <= 3) {
                        log.append("DELTA MISS r=$r edit@$p -$removed +$added delta=$d outside=${outside.take(4)}\n")
                    }
                }
            }
            if ((0 until ref.lineCount).any { ref.lineInfo(it) != h.lineInfo(it) }) badInfo++
            if (h.spans() != ref.spans()) {
                bad++
                if (bad <= 3) {
                    val a = h.spans().toSet()
                    val b = ref.spans().toSet()
                    log.append("FUZZ MISMATCH round $r: incr-full=${(a - b).take(5)} full-incr=${(b - a).take(5)}\n")
                }
                h.fullScan(cur)
            }
        }
        assertEquals(0, bad, "span mismatches\n$log")
        assertEquals(0, badInfo, "LineInfo mismatches\n$log")
        assertEquals(0, deltaMiss, "changes outside HighlightDelta\n$log")
    }
}
