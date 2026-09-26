package dev.mdwriter.markdown

/**
 * Shared helper for [SmartEdit.clearFormatting] and [DocTitle]: given a set of [MdSpan]s, computes
 * the char ranges that carry pure Markdown syntax (marker runs, link/image punctuation and
 * destination/title, autolink brackets) inside a scope, and can delete them and remap offsets
 * through the deletion.
 */
internal object MarkupStrip {
    /** Adds [start, end) ranges (as `start until end`) to delete inside [from, to) so only visible text stays. */
    fun inlineDeletions(
        text: CharSequence,
        spans: List<MdSpan>,
        from: Int,
        to: Int,
        out: MutableList<IntRange>,
    ) {
        val inside = spans.filter { it.start >= from && it.end <= to }
        for (s in inside) {
            when (s.kind) {
                MdKind.EMPHASIS_MARKER, MdKind.CODE_SPAN_MARKER -> {
                    out += s.start until s.end
                }

                MdKind.LINK -> {
                    // keep only the link text / alt text; drop brackets, destination and title
                    val t = inside.firstOrNull { it.kind == MdKind.LINK_TEXT && it.start >= s.start && it.end <= s.end }
                    if (t != null) {
                        out += s.start until t.start
                        out += t.end until s.end
                    } else {
                        inside
                            .filter { it.kind == MdKind.LINK_MARKER && it.start >= s.start && it.end <= s.end }
                            .forEach { out += it.start until it.end }
                    }
                }

                MdKind.AUTOLINK -> {
                    if (text[s.start] == '<') {
                        out += s.start until s.start + 1
                        out += s.end - 1 until s.end
                    }
                }

                else -> {}
            }
        }
    }

    /** Sorts [del] and merges touching/overlapping ranges (ascending, non-overlapping). */
    fun merge(del: List<IntRange>): List<IntRange> {
        val sorted = del.filterNot { it.isEmpty() }.sortedBy { it.first }
        val out = ArrayList<IntRange>(sorted.size)
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.last + 1) {
                if (r.last > last.last) out[out.size - 1] = last.first..r.last
            } else {
                out += r
            }
        }
        return out
    }

    /** Sorts + merges [del] and returns [text] without them. */
    fun deleteAll(
        text: CharSequence,
        del: List<IntRange>,
    ): String {
        val merged = merge(del)
        val sb = StringBuilder(text.length)
        var pos = 0
        for (r in merged) {
            if (r.first > pos) sb.append(text, pos, r.first)
            pos = maxOf(pos, r.last + 1)
        }
        if (pos < text.length) sb.append(text, pos, text.length)
        return sb.toString()
    }

    /** Maps an old offset through the (already-[merge]d) deletions (inside a range -> range start). */
    fun map(
        pos: Int,
        merged: List<IntRange>,
    ): Int {
        var shift = 0
        for (r in merged) {
            if (pos > r.last) {
                shift += r.last - r.first + 1
                continue
            }
            if (pos < r.first) break
            return r.first - shift
        }
        return pos - shift
    }
}
