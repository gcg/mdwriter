package mdwriter.markdown

/**
 * Pure, platform-free editing commands. Each returns a [TextEdit] (or null = "do nothing, let the
 * default behaviour happen"). The UI layer applies the edit to its text state in ONE undoable step.
 */
public data class TextEdit(val start: Int, val end: Int, val replacement: String, val selStart: Int, val selEnd: Int = selStart) {
    public fun applyTo(text: String): Pair<String, IntRange> =
        (text.substring(0, start) + replacement + text.substring(end)) to (selStart..selEnd)

    /**
     * Same edit with the common prefix/suffix of old and new text trimmed, so the UI replaces as few
     * chars as possible (keeps spans/IME state of untouched text). Selection is unchanged (absolute, new text).
     */
    public fun minimize(text: CharSequence): TextEdit {
        var a = start; var r0 = 0; var r1 = replacement.length; var b = end
        while (a < b && r0 < r1 && text[a] == replacement[r0]) { a++; r0++ }
        while (b > a && r1 > r0 && text[b - 1] == replacement[r1 - 1]) { b--; r1-- }
        return TextEdit(a, b, replacement.substring(r0, r1), selStart, selEnd)
    }
}

/** Parsed container prefix of a line: indentation, blockquote markers, list marker, task box. */
public data class LinePrefix(
    val lineStart: Int,
    val lineEnd: Int,
    val indent: String,          // leading whitespace before any marker
    val quote: String,           // e.g. "> > " (may be empty)
    val listIndent: String,      // whitespace between quote prefix and list marker
    val bullet: Char?,           // '-', '*', '+' or null
    val number: Int?,            // ordered list number or null
    val delimiter: Char?,        // '.' or ')' for ordered lists
    val afterMarker: String,     // spaces after the list marker
    val task: String?,           // "[ ] " / "[x] " (with its trailing space) or null
    val contentStart: Int,       // absolute offset where the item's text starts
) {
    val isList: Boolean get() = bullet != null || number != null
    val isEmptyItem: Boolean get() = contentStart >= lineEnd
    val prefixLen: Int get() = contentStart - lineStart
}

public object SmartEdit {
    private val PREFIX = Regex("^([ \\t]*)((?:>[ \\t]?)*)([ \\t]*)(?:([-*+])|(\\d{1,9})([.)]))?([ \\t]+|$)?(\\[[ xX]\\](?:[ \\t]|$))?")

    public fun lineBounds(text: CharSequence, pos: Int): IntRange {
        var s = pos; while (s > 0 && text[s - 1] != '\n') s--
        var e = pos; while (e < text.length && text[e] != '\n') e++
        return s..e // inclusive start, exclusive end in .last
    }

    public fun parsePrefix(text: CharSequence, pos: Int): LinePrefix {
        val b = lineBounds(text, pos)
        val line = text.subSequence(b.first, b.last).toString()
        val m = PREFIX.find(line)!!
        val g = m.groupValues
        var bullet: Char? = g[4].firstOrNull()
        var number: Int? = g[5].takeIf { it.isNotEmpty() }?.toInt()
        var delim: Char? = g[6].firstOrNull()
        var after = g[7]
        var task: String? = g[8].takeIf { it.isNotEmpty() }
        // a marker must be followed by whitespace or end of line, and "---"/"***" are thematic breaks
        val markerPresent = bullet != null || number != null
        val markerEndInLine = g[1].length + g[2].length + g[3].length + g[4].length + g[5].length + g[6].length
        val validMarker = markerPresent && (after.isNotEmpty() || markerEndInLine == line.length) &&
            !(bullet != null && Regex("^[ \\t]*([-*_])([ \\t]*\\1){2,}[ \\t]*$").matches(line.substring(g[1].length + g[2].length)))
        if (!validMarker) { bullet = null; number = null; delim = null; after = ""; task = null }
        val listLen = if (validMarker) g[3].length + g[4].length + g[5].length + g[6].length + after.length + (task?.length ?: 0) else 0
        val contentStart = b.first + g[1].length + g[2].length + listLen
        return LinePrefix(b.first, b.last, g[1], g[2], if (validMarker) g[3] else "", bullet, number, delim, after, task, contentStart)
    }

    /**
     * Enter / newline at a collapsed cursor. Returns null for "insert a plain newline".
     * - list item with content: split line, continue with same marker (ordered +1, task unchecked)
     * - empty list item: nested -> outdent one level; top-level -> remove marker (ends the list)
     * - blockquote line: continue "> " prefix; empty quote line -> remove the prefix (ends quote)
     * [inCode] must be true when the cursor line is inside a fenced/indented code block or front matter
     * (from the highlighter); then only the leading indentation is kept.
     */
    public fun onEnter(
        text: String,
        cursor: Int,
        lineType: BlockType? = null,     // highlighter.lineInfoAt(cursor).type
        fenceUnclosed: Boolean = false,  // highlighter.isFenceUnclosed(line) (only read when lineType == FENCE_OPEN)
        renumber: Boolean = true,
    ): TextEdit? {
        val p = parsePrefix(text, cursor)
        if (lineType == BlockType.FENCE_OPEN && fenceUnclosed && cursor == p.lineEnd) {
            // auto-close the fence: "```kotlin|" -> "```kotlin\n|\n```"
            val line = text.substring(p.lineStart, p.lineEnd)
            val m = Regex("^([ \\t]*(?:>[ \\t]?)*[ \\t]*)(`{3,}|~{3,})").find(line)
            if (m != null) {
                val lead = m.groupValues[1].replace(Regex("[^ \\t>]"), " ")
                val ins = "\n" + lead + "\n" + lead + m.groupValues[2]
                return TextEdit(cursor, cursor, ins, cursor + 1 + lead.length)
            }
        }
        if (lineType != null && lineType.isVerbatim) {
            val ws = Regex("^[ \\t]*(?:>[ \\t]?)*").find(text.substring(p.lineStart, p.lineEnd))!!.value
            return if (ws.isEmpty()) null else TextEdit(cursor, cursor, "\n" + ws, cursor + 1 + ws.length)
        }
        if (!p.isList && p.quote.isEmpty()) return null
        if (cursor < p.contentStart) return null // cursor inside the prefix: plain newline
        if (p.isEmptyItem && cursor == p.lineEnd) {
            if (p.isList) {
                val nested = (p.indent + p.listIndent).isNotEmpty()
                if (nested) {
                    val o = outdentLine(text, p)
                    return TextEdit(p.lineStart, p.lineEnd, o, p.lineStart + o.length)
                }
                // top-level empty item: drop the marker, keep quote prefix (so "> - " -> "> ")
                val keep = p.indent + p.quote
                return TextEdit(p.lineStart, p.lineEnd, keep, p.lineStart + keep.length)
            }
            // empty quote line: end the quote
            return TextEdit(p.lineStart, p.lineEnd, "", p.lineStart)
        }
        // "lazy" numbering (1. 1. 1.): if the previous sibling has the same number, keep it
        val lazyNumbers = p.number != null && p.lineStart > 0 && parsePrefix(text, p.lineStart - 1).let {
            it.number == p.number && it.delimiter == p.delimiter && it.indent == p.indent && it.quote == p.quote
        }
        val nextNumber = if (p.number == null) 0 else if (lazyNumbers) p.number else p.number + 1
        val marker = when {
            p.bullet != null -> p.bullet.toString()
            p.number != null -> "$nextNumber${p.delimiter}"
            else -> ""
        }
        val afterMarker = if (p.isList) (if (p.afterMarker.isEmpty() || p.afterMarker.length > 4) " " else p.afterMarker) else ""
        val task = if (p.task != null) "[ ] " else ""
        val prefix = p.indent + p.quote + p.listIndent + marker + afterMarker + task
        val insert = "\n" + prefix
        if (renumber && p.number != null && !lazyNumbers) {
            val (renum, end) = renumberFollowing(text, p.lineEnd, p, p.number + 2)
            if (renum.isNotEmpty()) {
                val rep = insert + text.substring(cursor, p.lineEnd) + renum
                return TextEdit(cursor, end, rep, cursor + insert.length)
            }
        }
        return TextEdit(cursor, cursor, insert, cursor + insert.length)
    }

    /** Renumbers consecutive ordered siblings after [from] (the end of the current line). Returns (newText, endOffset). */
    private fun renumberFollowing(text: String, from: Int, p: LinePrefix, startAt: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var pos = from
        var n = startAt
        var changed = false
        while (pos < text.length && text[pos] == '\n') {
            val ls = pos + 1
            if (ls > text.length) break
            val q = parsePrefix(text, ls)
            if (q.number == null || q.indent != p.indent || q.quote != p.quote || q.listIndent != p.listIndent || q.delimiter != p.delimiter) break
            val line = text.substring(q.lineStart, q.lineEnd)
            val numStart = q.indent.length + q.quote.length + q.listIndent.length
            val numStr = q.number.toString()
            val newLine = line.substring(0, numStart) + n + line.substring(numStart + numStr.length)
            if (newLine != line) changed = true
            sb.append('\n').append(newLine)
            pos = q.lineEnd
            n++
        }
        return if (changed) sb.toString() to pos else "" to from
    }

    /** Indent (nest) the list item on the cursor line under its previous sibling. */
    public fun indentListItem(text: String, cursor: Int): TextEdit? {
        val p = parsePrefix(text, cursor)
        if (!p.isList) return null
        // unit = width of the previous sibling's marker + spacing (aligns the marker under the parent's text)
        val prevLineEnd = p.lineStart - 1
        var unit = 4
        if (prevLineEnd > 0) {
            val prev = parsePrefix(text, prevLineEnd)
            if (prev.isList && prev.indent + prev.listIndent == p.indent + p.listIndent) unit = prev.contentStart - prev.lineStart - (prev.indent.length + prev.quote.length + prev.listIndent.length) - (prev.task?.length ?: 0)
        }
        val ins = " ".repeat(unit)
        val at = p.lineStart + p.indent.length + p.quote.length
        var line = text.substring(p.lineStart, p.lineEnd)
        val rel = at - p.lineStart
        line = line.substring(0, rel) + ins + line.substring(rel)
        // a newly nested ordered item restarts at 1
        if (p.number != null) {
            val numAt = rel + ins.length + p.listIndent.length
            line = line.substring(0, numAt) + "1" + line.substring(numAt + p.number.toString().length)
        }
        val delta = line.length - (p.lineEnd - p.lineStart)
        return TextEdit(p.lineStart, p.lineEnd, line, cursor + delta)
    }

    public fun outdentListItem(text: String, cursor: Int): TextEdit? {
        val p = parsePrefix(text, cursor)
        if (!p.isList || (p.indent + p.listIndent).isEmpty()) return null
        val o = outdentLine(text, p)
        val delta = o.length - (p.lineEnd - p.lineStart)
        return TextEdit(p.lineStart, p.lineEnd, o, maxOf(p.lineStart, cursor + delta))
    }

    private fun outdentLine(text: String, p: LinePrefix): String {
        val line = text.substring(p.lineStart, p.lineEnd)
        // remove up to one parent's content width (find the nearest less-indented list item above)
        val myIndent = (p.indent + p.listIndent).replace("\t", "    ").length
        var target = 0
        var pos = p.lineStart - 1
        while (pos > 0) {
            val q = parsePrefix(text, pos)
            if (q.isList) {
                val qi = (q.indent + q.listIndent).replace("\t", "    ").length
                if (qi < myIndent) { target = qi; break }
            } else if (q.lineEnd == q.lineStart) break
            pos = q.lineStart - 1
        }
        val rest = line.substring(p.indent.length + p.quote.length + p.listIndent.length)
        return p.quote.let { if (it.isEmpty()) " ".repeat(target) + rest else p.indent + it + " ".repeat(target) + rest }
    }

    // ------------------------------------------------------------------ inline wrap toggles
    /**
     * Toggle a symmetric inline marker ("**", "*", "~~", "==", "`") around the selection or, when the
     * selection is collapsed, around the word under the cursor. Un-toggles when already wrapped.
     * The same logical text stays selected afterwards, so toggling twice is the identity.
     */
    public fun toggleWrap(text: String, selStart: Int, selEnd: Int, marker: String): TextEdit {
        var a = selStart; var b = selEnd
        if (a == b) {
            val w = wordAt(text, a)
                ?: return TextEdit(a, a, marker + marker, a + marker.length) // empty pair, cursor inside
            a = w.first; b = w.last
        }
        // exclude surrounding whitespace (emphasis cannot start or end with whitespace)
        while (a < b && text[a].isWhitespace()) a++
        while (b > a && text[b - 1].isWhitespace()) b--
        if (a == b) return TextEdit(selStart, selEnd, text.substring(selStart, selEnd), selStart, selEnd)
        val c = marker[0]; val k = marker.length
        val star = c == '*' || c == '_'
        fun has(run: Int) = if (!star) run >= k else if (k == 1) run == 1 || run >= 3 else run >= 2
        if (marker != "`" && text.substring(a, b).contains('\n')) return toggleLines(text, a, b, marker, ::has)
        // 1. markers just outside the selection -> remove them
        val out = minOf(countBack(text, a, c), countFwd(text, b, c))
        if (has(out)) return TextEdit(a - k, b + k, text.substring(a, b), a - k, b - k)
        // 2. selection includes the markers -> strip them
        if (b - a > 2 * k) {
            val inn = minOf(countFwd(text, a, c), countBack(text, b, c), (b - a - 1) / 2)
            if (has(inn)) {
                val inner = text.substring(a + k, b - k)
                return TextEdit(a, b, inner, a, a + inner.length)
            }
        }
        // 3. wrap (line by line, so multi-line selections stay valid Markdown)
        val sel = text.substring(a, b)
        if (marker == "`") {
            val w = codeWrap(sel); val off = w.indexOf(sel)
            return TextEdit(a, b, w, a + off, a + off + sel.length)
        }
        val wrapped = sel.split('\n').joinToString("\n") { line ->
            val t = line.trim()
            if (t.isEmpty()) line else {
                val lead = line.substring(0, line.indexOf(t[0]))
                lead + marker + t + marker + line.substring(lead.length + t.length)
            }
        }
        return TextEdit(a, b, wrapped, a + k, a + wrapped.length - k)
    }

    /** Multi-line selection: wrap every non-empty line, or unwrap all lines when all are wrapped. */
    private fun toggleLines(text: String, a: Int, b: Int, m: String, has: (Int) -> Boolean): TextEdit {
        val k = m.length; val c = m[0]
        val lines = text.substring(a, b).split('\n')
        fun wrapped(t: String) = t.length > 2 * k && t.startsWith(m) && t.endsWith(m) &&
            has(minOf(countFwd(t, 0, c), countBack(t, t.length, c), (t.length - 1) / 2))
        val all = lines.filter { it.isNotBlank() }.all { wrapped(it.trim()) }
        val out = lines.joinToString("\n") { line ->
            val t = line.trim()
            if (t.isEmpty()) return@joinToString line
            val lead = line.substring(0, line.indexOf(t[0])); val trail = line.substring(lead.length + t.length)
            lead + (if (all) t.substring(k, t.length - k) else if (wrapped(t)) t else m + t + m) + trail
        }
        return TextEdit(a, b, out, a, a + out.length)
    }

    private fun countBack(text: String, end: Int, c: Char): Int { var n = 0; var j = end - 1; while (j >= 0 && text[j] == c) { n++; j-- }; return n }
    private fun countFwd(text: String, start: Int, c: Char): Int { var n = 0; var j = start; while (j < text.length && text[j] == c) { n++; j++ }; return n }

    private fun codeWrap(sel: String): String {
        var longest = 0; var run = 0
        for (ch in sel) { if (ch == '`') { run++; longest = maxOf(longest, run) } else run = 0 }
        val fence = "`".repeat(longest + 1)
        val pad = if (sel.startsWith("`") || sel.endsWith("`")) " " else ""
        return fence + pad + sel + pad + fence
    }

    /** Word under / adjacent to the cursor (letters, digits, '_' and inner apostrophes). Returns [start, end). */
    public fun wordAt(text: String, pos: Int): IntRange? {
        fun isW(c: Char) = c.isLetterOrDigit() || c == '_' || c == '\''
        var s = pos; var e = pos
        while (s > 0 && isW(text[s - 1])) s--
        while (e < text.length && isW(text[e])) e++
        if (s == e) return null
        return s..e
    }

    // ------------------------------------------------------------------ links
    public fun insertLink(text: String, selStart: Int, selEnd: Int, image: Boolean = false): TextEdit {
        val sel = text.substring(selStart, selEnd)
        val bang = if (image) "!" else ""
        return if (URL.matches(sel.trim())) {
            val rep = "$bang[](${sel.trim()})"
            TextEdit(selStart, selEnd, rep, selStart + bang.length + 1)
        } else {
            val rep = "$bang[$sel]()"
            TextEdit(selStart, selEnd, rep, selStart + rep.length - 1)
        }
    }
    private val URL = Regex("(?i)(https?://|www\\.|mailto:)\\S+")

    // ------------------------------------------------------------------ headings
    /** Cycle ATX heading level of the cursor line: none -> # -> ## -> ### -> none (levels 4-6 -> none). */
    public fun cycleHeading(text: String, cursor: Int): TextEdit {
        val p = parsePrefix(text, cursor)
        val cs = p.contentStart
        val line = text.substring(cs, p.lineEnd)
        val m = Regex("^ {0,3}(#{1,6})(?:[ \\t]+|$)").find(line)
        val level = m?.groupValues?.get(1)?.length ?: 0
        val next = when (level) { 0 -> 1; 1 -> 2; 2 -> 3; else -> 0 }
        val oldPrefixLen = m?.value?.length ?: 0
        val newPrefix = if (next == 0) "" else "#".repeat(next) + " "
        val delta = newPrefix.length - oldPrefixLen
        val newCursor = if (cursor >= cs + oldPrefixLen) cursor + delta else cs + newPrefix.length
        return TextEdit(cs, cs + oldPrefixLen, newPrefix, newCursor)
    }

    /** Set the ATX level of the cursor line (0 = plain paragraph); keyboard Ctrl+0..6. */
    public fun setHeading(text: String, cursor: Int, level: Int): TextEdit {
        require(level in 0..6)
        val p = parsePrefix(text, cursor)
        val cs = p.contentStart
        val m = Regex("^ {0,3}(#{1,6})(?:[ \\t]+|$)").find(text.substring(cs, p.lineEnd))
        val oldLen = m?.value?.length ?: 0
        val newPrefix = if (level == 0) "" else "#".repeat(level) + " "
        val delta = newPrefix.length - oldLen
        val newCursor = if (cursor >= cs + oldLen) cursor + delta else cs + newPrefix.length
        return TextEdit(cs, cs + oldLen, newPrefix, newCursor)
    }

    /**
     * Toggle a block quote on every line touched by the selection: if all non-blank lines already start
     * with ">", remove one level ("> " or ">"); otherwise add "> " after the leading indentation.
     */
    public fun toggleQuote(text: String, selStart: Int, selEnd: Int): TextEdit {
        val first = lineBounds(text, selStart).first
        val last = lineBounds(text, maxOf(selStart, if (selEnd > selStart && text.getOrNull(selEnd - 1) == '\n') selEnd - 1 else selEnd)).last
        val lines = text.substring(first, last).split('\n')
        val q = Regex("^([ \\t]{0,3})>( ?)")
        val allQuoted = lines.filter { it.isNotBlank() }.let { nb -> nb.isNotEmpty() && nb.all { q.containsMatchIn(it) } }
        val out = lines.joinToString("\n") { line ->
            if (allQuoted) q.find(line)?.let { line.substring(0, it.groupValues[1].length) + line.substring(it.value.length) } ?: line
            else if (line.isBlank() && lines.size > 1) ">"
            else { val ws = line.takeWhile { it == ' ' || it == '\t' }; ws + "> " + line.substring(ws.length) }
        }
        // keep the caret/selection on the same text: map both ends through the per-line shift
        fun map(pos: Int): Int {
            var oldOff = first; var newOff = first
            val newLines = out.split('\n')
            for (i in lines.indices) {
                val ol = lines[i]; val nl = newLines[i]
                if (pos <= oldOff + ol.length) {
                    val shift = nl.length - ol.length
                    val prefixOld = ol.length - ol.trimStart(' ', '\t', '>').length
                    val col = pos - oldOff
                    return newOff + if (col < prefixOld && !allQuoted) col else maxOf(0, col + shift)
                }
                oldOff += ol.length + 1; newOff += nl.length + 1
            }
            return newOff
        }
        return TextEdit(first, last, out, map(selStart), map(selEnd))
    }

    /**
     * Backspace at a collapsed cursor exactly at the content start of a list item (or quote line):
     * removes the list marker (+ task box), or one quote level, instead of one char. null = default.
     */
    public fun onBackspace(text: String, cursor: Int): TextEdit? {
        val p = parsePrefix(text, cursor)
        if (cursor != p.contentStart || cursor == p.lineStart) return null
        if (p.isList) {
            val markerStart = p.lineStart + p.indent.length + p.quote.length + p.listIndent.length
            return TextEdit(markerStart, p.contentStart, "", markerStart)
        }
        if (p.quote.isNotEmpty()) {
            val qs = p.lineStart + p.indent.length
            val lastGt = p.quote.lastIndexOf('>')
            return TextEdit(qs + lastGt, qs + p.quote.length, "", qs + lastGt)
        }
        return null
    }

    /** Toggle "[ ]" <-> "[x]" of the task item on the cursor line. */
    public fun toggleTask(text: String, cursor: Int): TextEdit? {
        val p = parsePrefix(text, cursor)
        val t = p.task ?: return null
        val boxAt = p.contentStart - t.length
        val checked = text[boxAt + 1] != ' '
        return TextEdit(boxAt + 1, boxAt + 2, if (checked) " " else "x", cursor)
    }
}
