package dev.mdwriter.markdown

/** Block mode carried from one line to the next. */
internal enum class Mode { NONE, PARAGRAPH, FENCE, HTML, TABLE, FRONT_MATTER }

/**
 * Entry state of a line = everything the scanner needs to know about previous lines.
 * Must be cheap to compare: incremental re-highlighting stops as soon as the freshly computed
 * entry state of a line equals the stored one (and no paragraph is pending).
 *
 * containers: open container blocks outermost-first. QUOTE (= -1) for a block quote, otherwise the
 * absolute content column of an open list item.
 */
internal data class LineState(
    val mode: Mode = Mode.NONE,
    val containers: List<Int> = emptyList(),
    val fenceChar: Char = ' ',
    val fenceLen: Int = 0,
    val fenceIndent: Int = 0,
    val htmlType: Int = 0,
    val tableCols: Int = 0,
) {
    companion object {
        const val QUOTE = -1
        val EMPTY = LineState()
    }
}

internal class IntList(
    cap: Int = 64,
) {
    var a = IntArray(cap)
    var size = 0

    operator fun get(i: Int) = a[i]

    operator fun set(
        i: Int,
        v: Int,
    ) {
        a[i] = v
    }

    fun add(v: Int) {
        if (size == a.size) a = a.copyOf(size * 2)
        a[size++] = v
    }

    fun clear() {
        size = 0
    }

    fun replaceRange(
        from: Int,
        toExcl: Int,
        values: IntArray,
    ) {
        val newSize = size - (toExcl - from) + values.size
        val b = if (newSize > a.size) IntArray(maxOf(newSize, a.size * 2)) else a
        if (b !== a) System.arraycopy(a, 0, b, 0, from)
        System.arraycopy(a, toExcl, b, from + values.size, size - toExcl)
        System.arraycopy(values, 0, b, from, values.size)
        a = b
        size = newSize
    }
}

/**
 * Incremental, line-state based Markdown highlighter (CommonMark + GFM approximation for styling).
 *
 * **Not thread-safe.** It keeps a reference to the last text passed to [fullScan]/[update] (it never
 * copies it). After a document is installed, use an instance from the main thread only; the initial
 * [fullScan] of a newly opened document may run on a background dispatcher before the text is handed
 * to the view, as long as there is no concurrent use.
 *
 * All offsets are UTF-16 char indices into that text. `'\n'` is the only line break (callers normalize
 * CRLF/CR before calling in); no [MdSpan] crosses a `'\n'`.
 */
public class MarkdownHighlighter(
    private val enableHighlight: Boolean = true,
    private val enableFrontMatter: Boolean = true,
) {
    private var text: CharSequence = ""

    // Mirrors `text.length`, maintained by this class's own arithmetic rather than re-read from `text` at
    // guard-check time: `text` is a REFERENCE to the caller's own CharSequence (see `currentText`'s KDoc), and a
    // real editor passes the SAME mutable Editable on every call (TextWatcher.onTextChanged's `s` is not a fresh
    // snapshot) — by the time a later `update()` call runs, `text.length` already reflects that call's own
    // (already-applied) edit, not "the length as of the end of the previous update()". Comparing against a
    // separately tracked Int avoids that aliasing trap; see `plans/STATUS.md` T07 for the incident this fixed
    // (every keystroke after the first was silently falling back to a full rescan).
    private var textLength: Int = 0
    private val lineStarts = IntList()
    private val entry = ArrayList<LineState?>()
    private val lineSpans = ArrayList<ArrayList<MdSpan>>() // offsets RELATIVE to line start
    private val defLabel = ArrayList<String?>() // normalized link-ref-def label defined on this line
    private val meta = ArrayList<Long>() // packed LineInfo: type | quote<<8 | list<<16 | contentRel<<32
    private val labelCounts = HashMap<String, Int>()
    private var scanFrom = 0
    private var scanEnd = 0

    /** Statistics of the last update (for tests / benchmarks). */
    public var lastRescannedLines: Int = 0
        private set

    /** Number of lines of the last scan (a document with no trailing newline still has at least 1 line). */
    public val lineCount: Int get() = lineStarts.size

    /** Absolute start offset of line [i]. */
    public fun lineStart(i: Int): Int = lineStarts[i]

    /**
     * Absolute end offset of line [i], exclusive of its trailing `'\n'` (or [currentText]'s length for
     * the last line).
     */
    public fun lineEnd(i: Int): Int = if (i + 1 < lineStarts.size) lineStarts[i + 1] - 1 else textLength

    /** The text of the last scan (the highlighter keeps a reference, it does not copy). */
    public val currentText: CharSequence get() = text

    /** Full (re)highlight. */
    public fun fullScan(newText: CharSequence): HighlightDelta {
        text = newText
        textLength = newText.length
        lineStarts.clear()
        entry.clear()
        lineSpans.clear()
        defLabel.clear()
        meta.clear()
        labelCounts.clear()
        lineStarts.add(0)
        for (i in newText.indices) if (newText[i] == '\n') lineStarts.add(i + 1)
        repeat(lineStarts.size) {
            entry.add(null)
            lineSpans.add(ArrayList(0))
            defLabel.add(null)
            meta.add(0L)
        }
        entry[0] = LineState.EMPTY
        scan(0, lineStarts.size - 1, force = true)
        // reference definitions may appear after their uses: one more pass once labels are known
        if (labelCounts.isNotEmpty()) scan(0, lineStarts.size - 1, force = true)
        return fullDelta()
    }

    /** Same as [fullScan]. */
    public fun setText(newText: CharSequence) {
        fullScan(newText)
    }

    private fun fullDelta() = HighlightDelta(0, lineStarts.size, 0, textLength, full = true)

    /**
     * Incremental update for a known edit (e.g. from `TextWatcher.onTextChanged(s, start, before, count)`):
     * [newText] is the text AFTER the change; the [removedLen] chars at [changeStart] were replaced by
     * [addedLen] chars. O(1) amortized for a single-character edit inside a paragraph/table; falls back to
     * a full rescan ([HighlightDelta.full] `== true`) when the arguments are inconsistent with the current
     * text or when the set of link reference definitions changes.
     */
    public fun update(
        newText: CharSequence,
        changeStart: Int,
        removedLen: Int,
        addedLen: Int,
    ): HighlightDelta {
        if (lineStarts.size == 0 || changeStart < 0 || removedLen < 0 || addedLen < 0 ||
            changeStart + removedLen > textLength || newText.length != textLength - removedLen + addedLen
        ) {
            return fullScan(newText)
        }
        if (removedLen == 0 &&
            addedLen == 0
        ) {
            text = newText
            textLength = newText.length
            lastRescannedLines = 0
            return HighlightDelta(0, 0, 0, 0, false)
        }
        return applyChange(newText, changeStart, changeStart + removedLen, changeStart + addedLen)
    }

    /** Incremental update that finds the change itself (common prefix/suffix diff, O(n) compare). */
    public fun update(newText: CharSequence): HighlightDelta {
        val old = text
        if (lineStarts.size == 0) return fullScan(newText)
        val minLen = minOf(old.length, newText.length)
        var p = 0
        while (p < minLen && old[p] == newText[p]) p++
        if (p == old.length &&
            p == newText.length
        ) {
            text = newText
            textLength = newText.length
            lastRescannedLines = 0
            return HighlightDelta(0, 0, 0, 0, false)
        }
        var so = old.length
        var sn = newText.length
        while (so > p && sn > p && old[so - 1] == newText[sn - 1]) {
            so--
            sn--
        }
        return applyChange(newText, p, so, sn)
    }

    /** [p] = change start, [so] = end of the replaced range in the old text, [sn] = end of the new chars in the new text. */
    private fun applyChange(
        newText: CharSequence,
        p: Int,
        so: Int,
        sn: Int,
    ): HighlightDelta {
        val labelsBefore = HashSet(labelCounts.keys)
        val delta = sn - so
        val first = lineIndexOf(p)
        val lastOld = lineIndexOf(so)
        // new line starts for lines first..(line containing sn in new text)
        val newStarts = IntList()
        newStarts.add(lineStarts[first])
        var k = lineStarts[first]
        val stopAt = sn
        while (k < newText.length) {
            if (newText[k] == '\n') {
                if (k >= stopAt) break
                newStarts.add(k + 1)
            }
            k++
        }
        val newCount = newStarts.size
        val oldCount = lastOld - first + 1
        // shift following line starts
        for (i in lastOld + 1 until lineStarts.size) lineStarts[i] = lineStarts[i] + delta
        lineStarts.replaceRange(first, lastOld + 1, newStarts.a.copyOf(newCount))
        // splice per-line tables; keep entry[first] (unchanged: depends only on previous lines)
        val keepEntry = entry[first]
        for (i in first..lastOld) defLabel[i]?.let { decLabel(it) }
        spliceLists(first, oldCount, newCount)
        entry[first] = keepEntry
        text = newText
        textLength = newText.length
        // restart at the beginning of the enclosing block
        var r = first
        while (r > 0) {
            val m = entry[r]!!.mode
            if (m == Mode.PARAGRAPH || m == Mode.TABLE || m == Mode.FRONT_MATTER) r-- else break
        }
        if (enableFrontMatter && r > 0 && first < 256 && isFence3(0, "---")) r = 0
        if (r < first) {
            for (i in r until first) {
                defLabel[i]?.let {
                    decLabel(it)
                    defLabel[i] = null
                }
            }
        }
        scan(r, first + newCount - 1, force = false)
        if (labelCounts.keys != labelsBefore) {
            // the set of link reference definitions changed: reference links anywhere may change
            scan(0, lineStarts.size - 1, force = true)
            return fullDelta()
        }
        return HighlightDelta(scanFrom, scanEnd, lineStarts[scanFrom], lineEnd(scanEnd - 1), full = false)
    }

    private fun spliceLists(
        first: Int,
        oldCount: Int,
        newCount: Int,
    ) {
        val common = minOf(oldCount, newCount)
        for (i in first + 1 until first + common) {
            entry[i] = null
            lineSpans[i] = ArrayList(0)
            defLabel[i] = null
        }
        lineSpans[first] = ArrayList(0)
        defLabel[first] = null
        if (newCount > oldCount) {
            val add = newCount - oldCount
            val at = first + oldCount
            entry.addAll(at, List(add) { null })
            lineSpans.addAll(at, List(add) { ArrayList<MdSpan>(0) })
            defLabel.addAll(at, List(add) { null })
            meta.addAll(at, List(add) { 0L })
        } else if (oldCount > newCount) {
            val from = first + newCount
            entry.subList(from, from + (oldCount - newCount)).clear()
            lineSpans.subList(from, from + (oldCount - newCount)).clear()
            defLabel.subList(from, from + (oldCount - newCount)).clear()
            meta.subList(from, from + (oldCount - newCount)).clear()
        }
    }

    /** Index of the line containing absolute [offset] (a line's trailing `'\n'` belongs to that line). */
    public fun lineIndexOf(offset: Int): Int {
        var lo = 0
        var hi = lineStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun incLabel(l: String) {
        labelCounts[l] = (labelCounts[l] ?: 0) + 1
    }

    private fun decLabel(l: String) {
        val c =
            (labelCounts[l] ?: 0) - 1
        if (c <= 0) labelCounts.remove(l) else labelCounts[l] = c
    }

    /** All spans in absolute document offsets, sorted by start. */
    public fun spans(): List<MdSpan> {
        val res = ArrayList<MdSpan>()
        for (i in 0 until lineStarts.size) {
            val base = lineStarts[i]
            for (sp in lineSpans[i]) res += MdSpan(sp.kind, sp.start + base, sp.end + base, sp.arg)
        }
        res.sortWith(SPAN_ORDER)
        return res
    }

    /**
     * Spans of lines [fromLine] until [endLine], **exclusive** (e.g. the range of a [HighlightDelta]).
     * Returned in absolute document offsets, sorted by [SPAN_ORDER], same as [spans].
     */
    public fun spansForLines(
        fromLine: Int,
        endLine: Int,
    ): List<MdSpan> {
        val res = ArrayList<MdSpan>()
        for (i in maxOf(0, fromLine) until minOf(endLine, lineStarts.size)) {
            val base = lineStarts[i]
            for (sp in lineSpans[i]) res += MdSpan(sp.kind, sp.start + base, sp.end + base, sp.arg)
        }
        res.sortWith(SPAN_ORDER)
        return res
    }

    /** Structure of line [line] (block type, container prefixes, quote/list depth). See [LineInfo]. */
    public fun lineInfo(line: Int): LineInfo {
        val m = meta[line]
        val start = lineStarts[line]
        return LineInfo(
            line = line,
            start = start,
            end = lineEnd(line),
            contentStart = start + (m ushr 32).toInt(),
            type = BlockType.entries[(m and 0xFF).toInt()],
            quoteDepth = ((m ushr 8) and 0xFF).toInt(),
            listDepth = ((m ushr 16) and 0xFF).toInt(),
        )
    }

    /** [lineInfo] of the line containing absolute [offset]. */
    public fun lineInfoAt(offset: Int): LineInfo = lineInfo(lineIndexOf(offset))

    /** Document outline (ATX + setext headings) in document order. */
    public fun headings(): List<HeadingItem> {
        val res = ArrayList<HeadingItem>()
        for (i in 0 until lineStarts.size) {
            val t = (meta[i] and 0xFF).toInt()
            if (t != BlockType.ATX_HEADING.ordinal && t != BlockType.SETEXT_HEADING.ordinal) continue
            val h = lineSpans[i].firstOrNull { it.kind == MdKind.HEADING } ?: continue
            // multi-line setext: first line only
            if (t == BlockType.SETEXT_HEADING.ordinal && i > 0 && (meta[i - 1] and 0xFF).toInt() == t) continue
            val base = lineStarts[i]
            val marker = lineSpans[i].filter { it.kind == MdKind.HEADING_MARKER }
            var s = base + h.start
            var e = base + h.end
            for (mk in marker) {
                val ms =
                    mk.start + base
                if (ms == s) {
                    s = mk.end + base
                } else if (ms > s) {
                    e = minOf(e, ms)
                }
            }
            res += HeadingItem(h.arg, i, base + h.start, text.subSequence(s, maxOf(s, e)).toString().trim())
        }
        return res
    }

    /**
     * True when [line] opens a fenced code block that is never closed (it runs to the end of the
     * document). Used by smart Enter to auto-insert the closing fence. O(lines after [line]).
     */
    public fun isFenceUnclosed(line: Int): Boolean {
        if (line !in 0 until lineStarts.size ||
            (meta[line] and 0xFF).toInt() != BlockType.FENCE_OPEN.ordinal
        ) {
            return false
        }
        for (i in line + 1 until lineStarts.size) {
            // closed, or ended by its container
            if ((meta[i] and 0xFF).toInt() != BlockType.FENCED_CODE.ordinal) return false
        }
        return true
    }

    private fun setMeta(
        line: Int,
        type: BlockType,
        contentAbs: Int,
        quote: Int,
        list: Int,
    ) {
        val rel = (contentAbs - lineStarts[line]).coerceIn(0, Int.MAX_VALUE)
        meta[line] = type.ordinal.toLong() or (quote.coerceAtMost(255).toLong() shl 8) or
            (list.coerceAtMost(255).toLong() shl 16) or (rel.toLong() shl 32)
    }

    private fun setType(
        line: Int,
        type: BlockType,
    ) {
        meta[line] = (meta[line] and 0xFF.toLong().inv()) or type.ordinal.toLong()
    }

    // =====================================================================================
    // Line scanning
    // =====================================================================================

    private class ParaLine(
        val line: Int,
        val contentStart: Int,
        val end: Int,
    )

    private val para = ArrayList<ParaLine>()
    private var paraContainers: List<Int> = emptyList()

    private fun scan(
        from: Int,
        minEnd: Int,
        force: Boolean,
    ) {
        para.clear()
        var state = entry[from] ?: LineState.EMPTY
        var i = from
        val n = lineStarts.size
        var scanned = 0
        while (i < n) {
            if (!force && i > minEnd && para.isEmpty() && state == entry[i]) break
            entry[i] = state
            lineSpans[i] = ArrayList(4)
            defLabel[i]?.let {
                decLabel(it)
                defLabel[i] = null
            }
            lt = BlockType.PARAGRAPH
            lContent = -1
            val next = scanLine(i, state)
            val cs = if (lContent < 0) lineStarts[i] else lContent
            val cont = if (lt == BlockType.PARAGRAPH && lazyLine) state.containers else next.containers
            setMeta(i, lt, cs, quoteDepthOf(cont), cont.size - quoteDepthOf(cont))
            lazyLine = false
            state = next
            scanned++
            i++
        }
        if (i >= n) flushParagraph()
        lastRescannedLines = scanned
        scanFrom = from
        scanEnd = i
    }

    // per-line classification written by scanLine
    private var lt = BlockType.PARAGRAPH
    private var lContent = -1
    private var lazyLine = false

    private fun addAbs(
        line: Int,
        kind: MdKind,
        start: Int,
        end: Int,
        arg: Int = 0,
    ) {
        val base = lineStarts[line]
        lineSpans[line].add(MdSpan(kind, start - base, end - base, arg))
    }

    // --- whitespace / column helpers -------------------------------------------------------
    private var col = 0 // visual column of `pos` during scanLine

    /** Advances over spaces/tabs starting at pos with visual column `col`; returns new pos, updates col. */
    private fun skipWs(
        pos0: Int,
        end: Int,
        maxCols: Int = Int.MAX_VALUE,
    ): Int {
        var pos = pos0
        val limit = if (maxCols == Int.MAX_VALUE) Int.MAX_VALUE else col + maxCols
        while (pos < end) {
            val c = text[pos]
            if (c == ' ') {
                if (col + 1 > limit) break
                col++
                pos++
            } else if (c == '\t') {
                val nc = col + 4 - (col % 4)
                if (nc > limit) break
                col = nc
                pos++
            } else {
                break
            }
        }
        return pos
    }

    private fun isBlank(
        from: Int,
        to: Int,
    ): Boolean {
        for (x in from until to) {
            val c = text[x]
            if (c != ' ' && c != '\t' && c != '\r') return false
        }
        return true
    }

    private fun trimEnd(
        from: Int,
        to: Int,
    ): Int {
        var e = to
        while (e > from && (text[e - 1] == ' ' || text[e - 1] == '\t' || text[e - 1] == '\r')) e--
        return e
    }

    private fun isFence3(
        line: Int,
        marker: String,
    ): Boolean {
        val ls = lineStarts[line]
        val le = lineEnd(line)
        val e = trimEnd(ls, le)
        if (e - ls != marker.length) return false
        for (x in marker.indices) if (text[ls + x] != marker[x]) return false
        return true
    }

    private fun frontMatterCloses(): Boolean {
        val max = minOf(lineStarts.size, 256)
        for (li in 1 until max) if (isFence3(li, "---") || isFence3(li, "...")) return true
        return false
    }

    // --- the per-line state machine ----------------------------------------------------------
    private fun scanLine(
        li: Int,
        st: LineState,
    ): LineState {
        val ls = lineStarts[li]
        val le = lineEnd(li)

        // YAML front matter (only at the very start of the document, only when closed)
        if (st.mode == Mode.FRONT_MATTER) {
            if (isFence3(li, "---") || isFence3(li, "...")) {
                lt = BlockType.FRONT_MATTER_FENCE
                addAbs(li, MdKind.FRONT_MATTER_FENCE, ls, trimEnd(ls, le))
                return LineState.EMPTY
            }
            lt = BlockType.FRONT_MATTER
            if (le > ls) addAbs(li, MdKind.FRONT_MATTER, ls, le)
            return st
        }
        if (li == 0 && enableFrontMatter && isFence3(0, "---") && frontMatterCloses()) {
            lt = BlockType.FRONT_MATTER_FENCE
            addAbs(li, MdKind.FRONT_MATTER_FENCE, ls, ls + 3)
            return LineState(mode = Mode.FRONT_MATTER)
        }

        // ---- 1. match open containers -------------------------------------------------------
        var pos = ls
        col = 0
        var matched = 0
        var quoteDepth = 0
        val lineBlank = isBlank(ls, le)
        val openC = st.containers
        while (matched < openC.size) {
            val c = openC[matched]
            if (c == LineState.QUOTE) {
                val saveCol = col
                val p2 = skipWs(pos, le, 3)
                if (p2 < le && text[p2] == '>') {
                    quoteDepth++
                    val mStart = p2
                    pos = p2 + 1
                    col++
                    if (pos < le && (text[pos] == ' ' || text[pos] == '\t')) {
                        pos++
                        col++
                    }
                    addAbs(li, MdKind.QUOTE_MARKER, mStart, pos)
                    matched++
                } else {
                    col = saveCol
                    break
                }
            } else {
                if (lineBlank) {
                    matched++
                    continue
                }
                val saveCol = col
                val p2 = skipWs(pos, le, c - col)
                if (col >= c) {
                    pos = p2
                    matched++
                } else {
                    col = saveCol
                    break
                }
            }
        }
        val allMatched = matched == openC.size
        lContent = pos

        // ---- 2. continuation of multi-line leaf blocks (fence / html) ---------------------------
        if (allMatched && st.mode == Mode.FENCE) {
            val baseCol = col
            val p2 = skipWs(pos, le, 3)
            val rel = col - baseCol
            if (rel <= 3 && p2 < le && text[p2] == st.fenceChar) {
                var q = p2
                while (q < le && text[q] == st.fenceChar) q++
                if (q - p2 >= st.fenceLen && isBlank(q, le)) {
                    lt = BlockType.FENCE_CLOSE
                    addAbs(li, MdKind.CODE_FENCE, p2, q)
                    return st.copy(mode = Mode.NONE, fenceChar = ' ', fenceLen = 0, fenceIndent = 0)
                }
            }
            // strip up to fenceIndent columns of indentation
            lt = BlockType.FENCED_CODE
            col = baseCol
            val cStart = skipWs(pos, le, st.fenceIndent)
            if (le > cStart) addAbs(li, MdKind.CODE_BLOCK, cStart, le)
            if (quoteDepth > 0) addAbs(li, MdKind.BLOCKQUOTE, firstQuote(li), le, quoteDepth)
            return st
        }
        if (allMatched && st.mode == Mode.HTML) {
            if (st.htmlType >= 6 &&
                isBlank(pos, le)
            ) {
                lt = BlockType.BLANK
                return st.copy(mode = Mode.NONE, htmlType = 0)
            }
            lt = BlockType.HTML
            if (le > pos) addAbs(li, MdKind.HTML_BLOCK, pos, le, st.htmlType)
            if (st.htmlType <= 5 && htmlEndsOnLine(st.htmlType, pos, le)) return st.copy(mode = Mode.NONE, htmlType = 0)
            return st
        }

        // ---- 3. open new containers ------------------------------------------------------------
        val containers = ArrayList<Int>(openC.subList(0, matched))
        var opened = false
        var listDepth = containers.count { it != LineState.QUOTE }
        var paragraphOpen = st.mode == Mode.PARAGRAPH || st.mode == Mode.TABLE
        while (true) {
            val baseCol = col
            val saveCol = col
            val p2 = skipWs(pos, le, 3)
            if (col - baseCol > 3 || p2 >= le) {
                col = saveCol
                break
            }
            val ch = text[p2]
            if (ch == '>') {
                quoteDepth++
                pos = p2 + 1
                col++
                if (pos < le && (text[pos] == ' ' || text[pos] == '\t')) {
                    pos++
                    col++
                }
                addAbs(li, MdKind.QUOTE_MARKER, p2, pos)
                containers.add(LineState.QUOTE)
                opened = true
                continue
            }
            // list item?
            val markerCol = col
            var mEnd = -1
            var ordered = false
            var orderedStart = 0
            if ((ch == '-' || ch == '*' || ch == '+') && !isThematicBreak(p2, le) &&
                !(paragraphOpen && allMatched && !opened && ch != '+' && isSetextUnderline(p2, le))
            ) {
                mEnd = p2 + 1
            } else if (ch in '0'..'9') {
                var q = p2
                while (q < le && q - p2 < 9 && text[q] in '0'..'9') q++
                if (q < le && q > p2 && (text[q] == '.' || text[q] == ')')) {
                    orderedStart = text.subSequence(p2, q).toString().toInt()
                    mEnd = q + 1
                    ordered = true
                }
            }
            if (mEnd < 0) {
                col = saveCol
                break
            }
            if (mEnd < le && text[mEnd] != ' ' && text[mEnd] != '\t') {
                col = saveCol
                break
            }
            val emptyItem = isBlank(mEnd, le)
            // a list can interrupt a paragraph only if non-empty and (bullet or ordered starting at 1)
            if (paragraphOpen && allMatched && !opened &&
                (emptyItem || (ordered && orderedStart != 1))
            ) {
                col = saveCol
                break
            }
            col = markerCol + (mEnd - p2)
            val markerEndCol = col
            var contentPos = mEnd
            val afterMarker = skipWs(mEnd, le)
            val spaces = col - markerEndCol
            val contentCol: Int
            if (emptyItem) {
                contentCol = markerEndCol + 1
                contentPos = le
            } else if (spaces in 1..4) {
                contentCol = col
                contentPos = afterMarker
            } else {
                col = markerEndCol
                contentPos = skipWs(mEnd, le, 1)
                contentCol = markerEndCol + 1
            }
            addAbs(li, MdKind.LIST_MARKER, p2, mEnd, listDepth)
            listDepth++
            containers.add(contentCol)
            opened = true
            pos = contentPos
            // GFM task list marker
            if (pos + 3 <= le && text[pos] == '[' && text[pos + 2] == ']' &&
                (text[pos + 1] == ' ' || text[pos + 1] == 'x' || text[pos + 1] == 'X') &&
                (pos + 3 == le || text[pos + 3] == ' ' || text[pos + 3] == '\t')
            ) {
                addAbs(li, MdKind.TASK_MARKER, pos, pos + 3, if (text[pos + 1] == ' ') 0 else 1)
                pos += 3
                col += 3
                pos = skipWs(pos, le)
            }
            if (emptyItem) break
        }

        // lazy continuation: unmatched containers, paragraph open, and line is plain text
        lContent = pos
        val restBlank = isBlank(pos, le)
        if (!allMatched && !opened && st.mode == Mode.PARAGRAPH && !restBlank && !startsBlock(pos, le, true)) {
            // keep the old containers; paragraph continues
            if (quoteDepthOf(st.containers) >
                0
            ) {
                addAbs(
                    li,
                    MdKind.BLOCKQUOTE,
                    if (quoteDepth >
                        0
                    ) {
                        firstQuote(li)
                    } else {
                        firstNonWs(pos, le)
                    },
                    le,
                    quoteDepthOf(st.containers),
                )
            }
            para += ParaLine(li, skipWsPlain(pos, le), le)
            lazyLine = true
            return st.copy(mode = Mode.PARAGRAPH)
        }
        if (!allMatched || opened) {
            // containers changed: close open paragraph / table
            flushParagraph()
            paragraphOpen = false
        }
        val baseState = LineState(containers = containers)
        if (quoteDepth > 0) addAbs(li, MdKind.BLOCKQUOTE, firstQuote(li), le, quoteDepth)

        // ---- 4. leaf blocks ---------------------------------------------------------------------
        if (restBlank) {
            lt = BlockType.BLANK
            flushParagraph()
            return baseState
        }

        val baseCol = col
        val p = skipWs(pos, le)
        val indent = col - baseCol
        if (indent >= 4 && !paragraphOpen) {
            col = baseCol
            val cStart = skipWs(pos, le, 4)
            lt = BlockType.INDENTED_CODE
            addAbs(li, MdKind.CODE_BLOCK, cStart, le)
            return baseState
        }
        if (indent < 4) {
            val c0 = text[p]
            // ATX heading
            if (c0 == '#') {
                var q = p
                while (q < le && text[q] == '#') q++
                val level = q - p
                if (level <= 6 && (q == le || text[q] == ' ' || text[q] == '\t')) {
                    flushParagraph()
                    lt = BlockType.ATX_HEADING
                    heading(li, p, q, le, level)
                    return baseState
                }
            }
            // fenced code opening
            if (c0 == '`' || c0 == '~') {
                var q = p
                while (q < le && text[q] == c0) q++
                val flen = q - p
                if (flen >= 3 && !(c0 == '`' && containsChar(q, le, '`'))) {
                    flushParagraph()
                    lt = BlockType.FENCE_OPEN
                    addAbs(li, MdKind.CODE_FENCE, p, q)
                    val infoS = skipWsPlain(q, le)
                    val infoE = trimEnd(infoS, le)
                    if (infoE > infoS) addAbs(li, MdKind.CODE_INFO, infoS, infoE)
                    return baseState.copy(mode = Mode.FENCE, fenceChar = c0, fenceLen = flen, fenceIndent = indent)
                }
            }
            // HTML block
            if (c0 == '<') {
                val t = htmlBlockStart(p, le, paragraphOpen)
                if (t > 0) {
                    flushParagraph()
                    lt = BlockType.HTML
                    addAbs(li, MdKind.HTML_BLOCK, p, le, t)
                    if (t <= 5 && htmlEndsOnLine(t, p, le)) return baseState
                    return baseState.copy(mode = Mode.HTML, htmlType = t)
                }
            }
            // setext underline
            if (paragraphOpen && st.mode == Mode.PARAGRAPH && (c0 == '=' || c0 == '-') && isSetextUnderline(p, le) &&
                para.isNotEmpty()
            ) {
                val level = if (c0 == '=') 1 else 2
                lt = BlockType.SETEXT_UNDERLINE
                addAbs(li, MdKind.HEADING_MARKER, p, trimEnd(p, le))
                setextHeading(level)
                return baseState
            }
            // thematic break
            if ((c0 == '-' || c0 == '*' || c0 == '_') && isThematicBreak(p, le)) {
                flushParagraph()
                lt = BlockType.THEMATIC_BREAK
                addAbs(li, MdKind.THEMATIC_BREAK, p, trimEnd(p, le))
                return baseState
            }
            // GFM table: delimiter row under a one-line header
            if (st.mode == Mode.PARAGRAPH && para.isNotEmpty() && (c0 == '|' || c0 == ':' || c0 == '-')) {
                val cols = delimiterRowCells(p, le)
                if (cols > 0) {
                    val header = para.last()
                    if (header.line == li - 1 && countCells(header.contentStart, header.end) == cols) {
                        para.removeAt(para.size - 1)
                        flushParagraph()
                        tableRow(header.line, header.contentStart, header.end, 0)
                        setType(header.line, BlockType.TABLE_HEADER)
                        lt = BlockType.TABLE_DELIMITER
                        tableDelimiterRow(li, p, le)
                        return baseState.copy(mode = Mode.TABLE, tableCols = cols)
                    }
                }
            }
            if (st.mode == Mode.TABLE && paragraphOpen) {
                lt = BlockType.TABLE_ROW
                tableRow(li, p, le, 2)
                return baseState.copy(mode = Mode.TABLE, tableCols = st.tableCols)
            }
            // link reference definition / footnote definition (cannot interrupt a paragraph)
            if (c0 == '[' && !paragraphOpen) {
                if (p + 1 < le && text[p + 1] == '^') {
                    val close = indexOfChar(p + 2, le, ']')
                    if (close > p + 2 && close + 1 < le && text[close + 1] == ':') {
                        lt = BlockType.FOOTNOTE_DEF
                        addAbs(li, MdKind.FOOTNOTE_REF, p, close + 1, 1)
                        addAbs(li, MdKind.LINK_MARKER, close + 1, close + 2)
                        val cs = skipWsPlain(close + 2, le)
                        if (cs < le) {
                            para += ParaLine(li, cs, le)
                            paraContainers = containers
                        }
                        return if (cs < le) baseState.copy(mode = Mode.PARAGRAPH) else baseState
                    }
                } else if (linkRefDef(li, p, le)) {
                    lt = BlockType.LINK_DEF
                    return baseState
                }
            }
        }
        // paragraph text
        if (!paragraphOpen || st.mode != Mode.PARAGRAPH) {
            flushParagraph()
            paraContainers = containers
        }
        para += ParaLine(li, p, le)
        return baseState.copy(mode = Mode.PARAGRAPH)
    }

    private fun quoteDepthOf(c: List<Int>) = c.count { it == LineState.QUOTE }

    private fun firstQuote(li: Int): Int {
        for (sp in lineSpans[li]) if (sp.kind == MdKind.QUOTE_MARKER) return sp.start + lineStarts[li]
        return lineStarts[li]
    }

    private fun firstNonWs(
        from: Int,
        to: Int,
    ): Int = skipWsPlain(from, to)

    private fun skipWsPlain(
        from: Int,
        to: Int,
    ): Int {
        var x = from
        while (x < to && (text[x] == ' ' || text[x] == '\t')) x++
        return x
    }

    private fun containsChar(
        from: Int,
        to: Int,
        c: Char,
    ): Boolean {
        for (x in from until to) if (text[x] == c) return true
        return false
    }

    private fun indexOfChar(
        from: Int,
        to: Int,
        c: Char,
    ): Int {
        var x = from
        while (x < to) {
            if (text[x] == '\\') {
                x += 2
                continue
            }
            if (text[x] == c) return x
            x++
        }
        return -1
    }

    /** Would this line content start a non-paragraph block? (used for lazy continuation) */
    private fun startsBlock(
        pos: Int,
        le: Int,
        forLazy: Boolean,
    ): Boolean {
        val saveCol = col
        val p = skipWs(pos, le, 3)
        col = saveCol
        if (p >= le) return true
        val c = text[p]
        if (c == '>') return true
        if (c ==
            '#'
        ) {
            var q = p
            while (q < le &&
                text[q] == '#'
            ) {
                q++
            }
            if (q - p <= 6 && (q == le || text[q] == ' ' || text[q] == '\t')) return true
        }
        if ((c == '`' || c == '~') && le - p >= 3 && text[p + 1] == c && text[p + 2] == c) return true
        if ((c == '-' || c == '*' || c == '_') && isThematicBreak(p, le)) return true
        if ((c == '-' || c == '*' || c == '+') && p + 1 < le && (text[p + 1] == ' ' || text[p + 1] == '\t') &&
            !isBlank(p + 1, le)
        ) {
            return true
        }
        if (c == '1' && p + 2 < le && (text[p + 1] == '.' || text[p + 1] == ')') && text[p + 2] == ' ') return true
        if (c == '<' && htmlBlockStart(p, le, true) in 1..6) return true
        return false
    }

    // --- headings -------------------------------------------------------------------------------
    private fun heading(
        li: Int,
        hashStart: Int,
        hashEnd: Int,
        le: Int,
        level: Int,
    ) {
        val contentStart = skipWsPlain(hashEnd, le)
        var contentEnd = trimEnd(contentStart, le)
        // optional closing sequence: spaces + #'s at end
        var q = contentEnd
        while (q > contentStart && text[q - 1] == '#') q--
        var closeStart = -1
        if (q < contentEnd && (q == contentStart || text[q - 1] == ' ' || text[q - 1] == '\t')) {
            closeStart = q
            contentEnd = trimEnd(contentStart, q)
        }
        addAbs(li, MdKind.HEADING, hashStart, le, level)
        addAbs(li, MdKind.HEADING_MARKER, hashStart, contentStart)
        if (closeStart >= 0) addAbs(li, MdKind.HEADING_MARKER, closeStart, trimEnd(closeStart, le))
        if (contentEnd > contentStart) inlineOver(listOf(ParaLine(li, contentStart, contentEnd)))
    }

    private fun setextHeading(level: Int) {
        val lines = ArrayList(para)
        para.clear()
        for (pl in lines) {
            addAbs(pl.line, MdKind.HEADING, pl.contentStart, trimEnd(pl.contentStart, pl.end), level)
            setType(pl.line, BlockType.SETEXT_HEADING)
        }
        inlineOver(lines.map { ParaLine(it.line, it.contentStart, trimEnd(it.contentStart, it.end)) })
    }

    private fun isSetextUnderline(
        p: Int,
        le: Int,
    ): Boolean {
        val c = text[p]
        if (c != '=' && c != '-') return false
        var q = p
        while (q < le && text[q] == c) q++
        return isBlank(q, le)
    }

    private fun isThematicBreak(
        p: Int,
        le: Int,
    ): Boolean {
        val c = text[p]
        if (c != '-' && c != '*' && c != '_') return false
        var count = 0
        for (x in p until le) {
            val d = text[x]
            if (d == c) {
                count++
            } else if (d != ' ' && d != '\t' && d != '\r') {
                return false
            }
        }
        return count >= 3
    }

    // --- tables ---------------------------------------------------------------------------------

    /** Returns the number of cells of a valid GFM delimiter row, or 0. */
    private fun delimiterRowCells(
        p: Int,
        le: Int,
    ): Int {
        val e = trimEnd(p, le)
        if (!containsChar(p, e, '|')) return 0
        var cells = 0
        var x = p
        if (x < e && text[x] == '|') x++
        while (x < e) {
            x = skipWsPlain(x, e)
            var hasDash = false
            if (x < e && text[x] == ':') x++
            while (x < e && text[x] == '-') {
                x++
                hasDash = true
            }
            if (x < e && text[x] == ':') x++
            x = skipWsPlain(x, e)
            if (!hasDash) return 0
            cells++
            if (x < e) {
                if (text[x] != '|') return 0
                x++
            }
        }
        return cells
    }

    private fun cellBoundaries(
        p: Int,
        e: Int,
    ): List<Int> {
        // positions of unescaped pipes
        val res = ArrayList<Int>()
        var x = p
        while (x < e) {
            val c = text[x]
            if (c == '\\') {
                x += 2
                continue
            }
            if (c == '|') res += x
            x++
        }
        return res
    }

    private fun countCells(
        p: Int,
        le: Int,
    ): Int {
        val e = trimEnd(p, le)
        val pipes = cellBoundaries(p, e)
        var cells = pipes.size + 1
        if (pipes.isNotEmpty() && pipes.first() == skipWsPlain(p, e)) cells--
        if (pipes.isNotEmpty() && pipes.last() == e - 1 && e - 1 != skipWsPlain(p, e)) {
            cells--
        } else if (pipes.size == 1 && pipes.first() == e - 1) {
            cells--
        }
        return cells
    }

    private fun tableRow(
        li: Int,
        p: Int,
        le: Int,
        kindArg: Int,
    ) {
        val e = trimEnd(p, le)
        addAbs(li, MdKind.TABLE_ROW, p, e, kindArg)
        val pipes = cellBoundaries(p, e)
        for (x in pipes) addAbs(li, MdKind.TABLE_PIPE, x, x + 1)
        var cellStart = p
        val bounds = pipes + e
        for (b in bounds) {
            val cs = skipWsPlain(cellStart, b)
            val ce = trimEnd(cs, b)
            if (ce > cs) inlineOver(listOf(ParaLine(li, cs, ce)))
            cellStart = b + 1
        }
    }

    private fun tableDelimiterRow(
        li: Int,
        p: Int,
        le: Int,
    ) {
        val e = trimEnd(p, le)
        addAbs(li, MdKind.TABLE_ROW, p, e, 1)
        for (x in cellBoundaries(p, e)) addAbs(li, MdKind.TABLE_PIPE, x, x + 1)
    }

    // --- link reference definitions ---------------------------------------------------------------
    private fun linkRefDef(
        li: Int,
        p: Int,
        le: Int,
    ): Boolean {
        val close = indexOfChar(p + 1, le, ']')
        if (close <= p + 1 || close + 1 >= le || text[close + 1] != ':') return false
        if (containsUnescaped(p + 1, close, '[')) return false
        var x = skipWsPlain(close + 2, le)
        if (x >= le) return false
        val ds: Int
        val de: Int
        var angle = false
        if (text[x] == '<') {
            val g = indexOfChar(x + 1, le, '>')
            if (g < 0) return false
            ds = x + 1
            de = g
            angle = true
            x = g + 1
        } else {
            ds = x
            while (x < le && text[x] != ' ' && text[x] != '\t') x++
            de = x
        }
        val afterDest = x
        x = skipWsPlain(x, le)
        var ts = -1
        var te = -1
        if (x < le && x > afterDest && (text[x] == '"' || text[x] == '\'' || text[x] == '(')) {
            val cc = if (text[x] == '(') ')' else text[x]
            val tc = indexOfChar(x + 1, le, cc)
            if (tc < 0) return false
            ts = x
            te = tc + 1
            x = skipWsPlain(te, le)
        }
        if (x < le && !isBlank(x, le)) return false
        val label = InlineScanner.normalizeLabel(text.subSequence(p + 1, close))
        if (label.isEmpty()) return false
        addAbs(li, MdKind.LINK_DEF, p, trimEnd(p, le))
        addAbs(li, MdKind.LINK_MARKER, p, p + 1)
        addAbs(li, MdKind.LINK_LABEL, p + 1, close)
        addAbs(li, MdKind.LINK_MARKER, close, close + 2)
        if (angle) {
            addAbs(li, MdKind.LINK_MARKER, ds - 1, ds)
            addAbs(li, MdKind.LINK_MARKER, de, de + 1)
        }
        if (de > ds) addAbs(li, MdKind.LINK_URL, ds, de)
        if (ts >= 0) addAbs(li, MdKind.LINK_TITLE, ts, te)
        defLabel[li] = label
        incLabel(label)
        return true
    }

    private fun containsUnescaped(
        from: Int,
        to: Int,
        c: Char,
    ): Boolean {
        var x = from
        while (x < to) {
            if (text[x] == '\\') {
                x += 2
                continue
            }
            if (text[x] == c) return true
            x++
        }
        return false
    }

    // --- HTML blocks --------------------------------------------------------------------------------
    private fun htmlBlockStart(
        p: Int,
        le: Int,
        paragraphOpen: Boolean,
    ): Int {
        val rest = text.subSequence(p, minOf(le, p + 64)).toString().lowercase()
        for (t in listOf("<pre", "<script", "<style", "<textarea")) {
            if (rest.startsWith(t) &&
                (rest.length == t.length || rest[t.length] == ' ' || rest[t.length] == '\t' || rest[t.length] == '>')
            ) {
                return 1
            }
        }
        if (rest.startsWith("<!--")) return 2
        if (rest.startsWith("<?")) return 3
        if (rest.startsWith("<![cdata[")) return 5
        if (rest.startsWith("<!") && rest.length > 2 && rest[2].isAsciiLetter()) return 4
        val nameStart = if (rest.startsWith("</")) 2 else 1
        var q = nameStart
        while (q < rest.length && rest[q].isAsciiLetterOrDigit()) q++
        val name = rest.substring(nameStart, q)
        if (name in BLOCK_TAGS &&
            (q == rest.length || rest[q] == ' ' || rest[q] == '\t' || rest[q] == '>' || rest.startsWith("/>", q))
        ) {
            return 6
        }
        if (!paragraphOpen && name.isNotEmpty() && name !in setOf("pre", "script", "style", "textarea")) {
            // type 7: a complete open or closing tag alone on the line
            val probe = InlineScanner(emptySet())
            val out = ArrayList<MdSpan>()
            val line = text.subSequence(p, le)
            probe.scan(line, out)
            val tag = out.firstOrNull { it.kind == MdKind.HTML_INLINE && it.start == 0 }
            if (tag != null && isBlank(p + tag.end, le)) return 7
        }
        return 0
    }

    private fun htmlEndsOnLine(
        type: Int,
        from: Int,
        to: Int,
    ): Boolean {
        val s = text.subSequence(from, to).toString().lowercase()
        return when (type) {
            1 -> s.contains("</pre>") || s.contains("</script>") || s.contains("</style>") || s.contains("</textarea>")
            2 -> s.contains("-->")
            3 -> s.contains("?>")
            4 -> s.contains(">")
            5 -> s.contains("]]>")
            else -> false
        }
    }

    // --- paragraphs & inline pass ------------------------------------------------------------------
    private fun flushParagraph() {
        if (para.isEmpty()) return
        val lines = ArrayList(para)
        para.clear()
        // last line: trailing spaces are not a hard break
        inlineOver(
            lines.mapIndexed { idx, pl ->
                if (idx ==
                    lines.size - 1
                ) {
                    ParaLine(pl.line, pl.contentStart, trimEnd(pl.contentStart, pl.end))
                } else {
                    pl
                }
            },
        )
    }

    private val sb = StringBuilder()
    private var map = IntArray(256)

    private fun inlineOver(lines: List<ParaLine>) {
        sb.setLength(0)
        var total = 0
        for (pl in lines) total += (pl.end - pl.contentStart) + 1
        if (map.size < total + 1) map = IntArray(total + 64)
        val segStart = IntArray(lines.size)
        for ((idx, pl) in lines.withIndex()) {
            if (idx > 0) {
                map[sb.length] = lines[idx - 1].end
                sb.append('\n')
            }
            segStart[idx] = sb.length
            for (x in pl.contentStart until pl.end) {
                map[sb.length] = x
                sb.append(text[x])
            }
        }
        map[sb.length] = if (lines.isEmpty()) 0 else lines.last().end
        val out = ArrayList<MdSpan>()
        InlineScanner(labelCounts.keys, enableHighlight).scan(sb, out)
        // distribute to lines (split spans that cross line boundaries). Binary search for the first
        // segment: O(spans * log(lines) + pieces) instead of O(spans * lines).
        for (sp in out) {
            var lo = 0
            var hi = lines.size - 1
            while (lo <
                hi
            ) {
                val mid = (lo + hi + 1) ushr 1
                if (segStart[mid] <= sp.start) lo = mid else hi = mid - 1
            }
            var idx = lo
            while (idx < lines.size) {
                val pl = lines[idx]
                val a = segStart[idx]
                if (a >= sp.end) break
                val b = a + (pl.end - pl.contentStart)
                val s0 = maxOf(sp.start, a)
                val e0 = minOf(sp.end, b)
                if (s0 < e0) addAbs(pl.line, sp.kind, map[s0], map[e0 - 1] + 1, sp.arg)
                idx++
            }
        }
    }

    public companion object {
        /** Application order: content styles first (outer before inner), markers last. */
        public val SPAN_ORDER: Comparator<MdSpan> =
            compareBy<MdSpan>({ it.kind.isMarker }, { it.start }, { -it.end }, { it.kind.ordinal })
        internal val BLOCK_TAGS: Set<String> =
            setOf(
                "address",
                "article",
                "aside",
                "base",
                "basefont",
                "blockquote",
                "body",
                "caption",
                "center",
                "col",
                "colgroup",
                "dd",
                "details",
                "dialog",
                "dir",
                "div",
                "dl",
                "dt",
                "fieldset",
                "figcaption",
                "figure",
                "footer",
                "form",
                "frame",
                "frameset",
                "h1",
                "h2",
                "h3",
                "h4",
                "h5",
                "h6",
                "head",
                "header",
                "hr",
                "html",
                "iframe",
                "legend",
                "li",
                "link",
                "main",
                "menu",
                "menuitem",
                "nav",
                "noframes",
                "ol",
                "optgroup",
                "option",
                "p",
                "param",
                "search",
                "section",
                "summary",
                "table",
                "tbody",
                "td",
                "tfoot",
                "th",
                "thead",
                "title",
                "tr",
                "track",
                "ul",
            )
    }
}
