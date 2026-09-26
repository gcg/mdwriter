package dev.mdwriter.markdown

/**
 * CommonMark/GFM inline scanner for ONE inline container (a paragraph, a heading's content, a table cell).
 *
 * Input `s` is the container's inline text with continuation lines joined by '\n' (container
 * prefixes such as "> " and continuation indentation already removed). Output spans are in `s`
 * coordinates; the caller maps them back to document offsets.
 *
 * Implements: backslash escapes, code spans (backtick-run matching), emphasis / strong with the
 * CommonMark delimiter-stack algorithm (flanking rules, rule of 3, openers_bottom), GFM strikethrough
 * (~ / ~~, equal-length matching), optional ==highlight==, inline links / images with destination +
 * title, full / collapsed / shortcut reference links (only when the label is defined), footnote refs,
 * <autolinks>, GFM bare URL autolinks (www., http://, https://), inline HTML (tags, comments,
 * processing instructions, declarations, CDATA), entities, hard line breaks, GFM bare e-mail autolinks.
 *
 * Linear-time guards (cf. commonmark-java 0.30.0 fixes for pathological input): failed searches for a
 * closing backtick run of length L, for "-->", "?>", "]]>", ">" and quote chars, and for link-title
 * closers are memoised, so repeated unfinished openers cost O(1) each instead of re-scanning to the end.
 */
internal class InlineScanner(
    private val refLabels: Set<String>,
    private val enableHighlight: Boolean = true,
    private val enableFootnotes: Boolean = true,
) {
    private class Delim(
        val ch: Char,
        var start: Int,
        var len: Int,
        val origLen: Int,
        val canOpen: Boolean,
        val canClose: Boolean,
    ) {
        var prev: Delim? = null
        var next: Delim? = null
    }

    private class Bracket(
        val pos: Int,
        val image: Boolean,
        val delimBottom: Delim?,
    ) {
        var active = true
    }

    private lateinit var s: CharSequence
    private var n = 0
    private lateinit var out: MutableList<MdSpan>
    private var top: Delim? = null
    private val brackets = ArrayList<Bracket>()
    private val noBacktickCloser = HashSet<Int>() // run lengths with no closing run later in the input
    private val noMatchFrom = HashMap<String, Int>() // indexOf(str, from) failed for this `from`
    private val noTitleCloseFrom = HashMap<Char, Int>() // link title closer search failed from here
    private var firstOut = 0

    fun scan(
        input: CharSequence,
        output: MutableList<MdSpan>,
    ) {
        s = input
        n = input.length
        out = output
        top = null
        brackets.clear()
        noBacktickCloser.clear()
        noMatchFrom.clear()
        noTitleCloseFrom.clear()
        firstOut = output.size
        var i = 0
        while (i < n) {
            val c = s[i]
            i =
                when (c) {
                    '\\' -> {
                        backslash(i)
                    }

                    '`' -> {
                        backticks(i)
                    }

                    '*', '_' -> {
                        delimRun(i, c)
                    }

                    '~' -> {
                        delimRun(i, c)
                    }

                    '=' -> {
                        if (enableHighlight) delimRun(i, c) else i + 1
                    }

                    '!' -> {
                        if (i + 1 < n && s[i + 1] == '[') {
                            brackets.add(Bracket(i, true, top))
                            i + 2
                        } else {
                            i + 1
                        }
                    }

                    '[' -> {
                        openBracket(i)
                    }

                    ']' -> {
                        closeBracket(i)
                    }

                    '<' -> {
                        angle(i)
                    }

                    '&' -> {
                        entity(i)
                    }

                    '\n' -> {
                        hardBreakSpaces(i)
                        i + 1
                    }

                    'h', 'H', 'w', 'W' -> {
                        bareUrl(i)
                    }

                    else -> {
                        i + 1
                    }
                }
        }
        processEmphasis(null)
        emailAutolinks()
    }

    // ------------------------------------------------------------------ escapes, code, breaks
    private fun backslash(i: Int): Int {
        if (i + 1 < n) {
            val d = s[i + 1]
            if (d == '\n') {
                out += MdSpan(MdKind.HARD_BREAK, i, i + 1)
                return i + 1
            }
            if (isAsciiPunct(d)) {
                out += MdSpan(MdKind.ESCAPE_MARKER, i, i + 1)
                return i + 2
            }
        }
        return i + 1
    }

    private fun hardBreakSpaces(nl: Int) {
        var j = nl
        while (j > 0 && s[j - 1] == ' ') j--
        if (nl - j >= 2) out += MdSpan(MdKind.HARD_BREAK, j, nl)
    }

    private fun backticks(i: Int): Int {
        val len = runLength(i, '`')
        if (len in noBacktickCloser) return i + len
        var j = i + len
        while (j < n) {
            if (s[j] == '`') {
                val l2 = runLength(j, '`')
                if (l2 == len) {
                    out += MdSpan(MdKind.CODE_SPAN, i, j + l2)
                    out += MdSpan(MdKind.CODE_SPAN_MARKER, i, i + len)
                    out += MdSpan(MdKind.CODE_SPAN_MARKER, j, j + l2)
                    return j + l2
                }
                j += l2
            } else {
                j++
            }
        }
        noBacktickCloser.add(len)
        return i + len // no closer: literal backticks
    }

    private fun runLength(
        i: Int,
        c: Char,
    ): Int {
        var j = i
        while (j < n && s[j] == c) j++
        return j - i
    }

    // ------------------------------------------------------------------ emphasis delimiters
    private fun delimRun(
        i: Int,
        c: Char,
    ): Int {
        val len = runLength(i, c)
        if (c == '~' && len > 2) return i + len
        if (c == '=' && len != 2) return i + len
        val before = if (i == 0) '\n'.code else Character.codePointBefore(s, i)
        val after = if (i + len >= n) '\n'.code else Character.codePointAt(s, i + len)
        val wsA = isUnicodeWs(after)
        val wsB = isUnicodeWs(before)
        val pA = isUnicodePunct(after)
        val pB = isUnicodePunct(before)
        val left = !wsA && (!pA || wsB || pB)
        val right = !wsB && (!pB || wsA || pA)
        val canOpen: Boolean
        val canClose: Boolean
        if (c == '_') {
            canOpen = left && (!right || pB)
            canClose = right && (!left || pA)
        } else {
            canOpen = left
            canClose = right
        }
        if (canOpen || canClose) {
            val d = Delim(c, i, len, len, canOpen, canClose)
            d.prev = top
            top?.next = d
            top = d
        }
        return i + len
    }

    private fun remove(d: Delim) {
        d.prev?.next = d.next
        d.next?.prev = d.prev
        if (top === d) top = d.prev
        d.prev = null
        d.next = null
    }

    private fun processEmphasis(bottom: Delim?) {
        // first delimiter above bottom
        val first: Delim? =
            if (bottom == null) {
                var f = top
                while (f?.prev != null) f = f.prev
                f
            } else {
                bottom.next
            }
        val openersBottom = HashMap<Int, Delim?>()
        var closer = first
        while (closer != null) {
            if (!closer.canClose) {
                closer = closer.next
                continue
            }
            val key = key(closer)
            val limit: Delim? = if (openersBottom.containsKey(key)) openersBottom[key] else bottom
            var opener = closer.prev
            var found: Delim? = null
            while (opener != null && opener !== bottom && opener !== limit) {
                if (opener.ch == closer.ch && opener.canOpen && matches(opener, closer)) {
                    found = opener
                    break
                }
                opener = opener.prev
            }
            if (found != null) {
                val o: Delim = found
                val use =
                    when (closer.ch) {
                        '*', '_' -> if (o.len >= 2 && closer.len >= 2) 2 else 1
                        else -> closer.len
                    }
                val kind =
                    when (closer.ch) {
                        '~' -> MdKind.STRIKETHROUGH
                        '=' -> MdKind.HIGHLIGHT
                        else -> if (use == 2) MdKind.STRONG else MdKind.EMPHASIS
                    }
                val oStart = o.start + o.len - use
                val cEnd = closer.start + use
                out += MdSpan(kind, oStart, cEnd)
                out += MdSpan(MdKind.EMPHASIS_MARKER, oStart, oStart + use)
                out += MdSpan(MdKind.EMPHASIS_MARKER, closer.start, cEnd)
                o.len -= use
                closer.start += use
                closer.len -= use
                var d = o.next
                while (d != null && d !== closer) {
                    val nx = d.next
                    remove(d)
                    d = nx
                }
                if (o.len == 0) remove(o)
                if (closer.len == 0) {
                    val nx = closer.next
                    remove(closer)
                    closer = nx
                }
            } else {
                openersBottom[key] = closer.prev
                val nx = closer.next
                if (!closer.canOpen) remove(closer)
                closer = nx
            }
        }
        while (top != null && top !== bottom) remove(top!!)
    }

    private fun key(d: Delim): Int =
        when (d.ch) {
            '*' -> 0
            '_' -> 6
            '~' -> 12
            else -> 18
        } + (if (d.canOpen) 3 else 0) + d.origLen % 3

    private fun matches(
        o: Delim,
        c: Delim,
    ): Boolean =
        when (c.ch) {
            '~' -> o.len == c.len
            '=' -> o.len == 2 && c.len == 2
            else -> !((o.canClose || c.canOpen) && c.origLen % 3 != 0 && (o.origLen + c.origLen) % 3 == 0)
        }

    // ------------------------------------------------------------------ links & images
    private fun openBracket(i: Int): Int {
        if (enableFootnotes && i + 1 < n && s[i + 1] == '^') {
            var j = i + 2
            while (j < n && s[j] != ']' && s[j] != '[' && !s[j].isWhitespace()) j++
            if (j < n && s[j] == ']' && j > i + 2) {
                out += MdSpan(MdKind.FOOTNOTE_REF, i, j + 1)
                return j + 1
            }
        }
        brackets.add(Bracket(i, false, top))
        return i + 1
    }

    private fun closeBracket(i: Int): Int {
        val b = brackets.lastOrNull() ?: return i + 1
        if (!b.active) {
            brackets.removeAt(brackets.size - 1)
            return i + 1
        }
        val textStart = b.pos + if (b.image) 2 else 1
        // 1. inline link: ](dest "title")
        if (i + 1 < n && s[i + 1] == '(') {
            val tail = parseInlineTail(i + 1)
            if (tail != null) {
                emitLinkHead(b, textStart, i)
                out += MdSpan(MdKind.LINK_MARKER, i + 1, i + 2) // (
                if (tail.destEnd > tail.destStart) out += MdSpan(MdKind.LINK_URL, tail.destStart, tail.destEnd)
                if (tail.angle) {
                    out += MdSpan(MdKind.LINK_MARKER, tail.destStart - 1, tail.destStart)
                    out += MdSpan(MdKind.LINK_MARKER, tail.destEnd, tail.destEnd + 1)
                }
                if (tail.titleStart >= 0) out += MdSpan(MdKind.LINK_TITLE, tail.titleStart, tail.titleEnd)
                out += MdSpan(MdKind.LINK_MARKER, tail.end - 1, tail.end) // )
                out += MdSpan(MdKind.LINK, b.pos, tail.end, if (b.image) 1 else 0)
                return finishLink(b, tail.end)
            }
        }
        // 2. full / collapsed reference: ][label] / ][]
        if (i + 1 < n && s[i + 1] == '[') {
            val close = findLabelEnd(i + 2)
            if (close >= 0) {
                val label =
                    if (close ==
                        i + 2
                    ) {
                        normalizeLabel(s.subSequence(textStart, i))
                    } else {
                        normalizeLabel(s.subSequence(i + 2, close))
                    }
                if (label.isNotEmpty() && label in refLabels) {
                    emitLinkHead(b, textStart, i)
                    out += MdSpan(MdKind.LINK_MARKER, i + 1, i + 2)
                    if (close > i + 2) out += MdSpan(MdKind.LINK_LABEL, i + 2, close)
                    out += MdSpan(MdKind.LINK_MARKER, close, close + 1)
                    out += MdSpan(MdKind.LINK, b.pos, close + 1, if (b.image) 1 else 0)
                    return finishLink(b, close + 1)
                }
            }
        }
        // 3. shortcut reference: [label]
        val label = normalizeLabel(s.subSequence(textStart, i))
        if (label.isNotEmpty() && label in refLabels) {
            emitLinkHead(b, textStart, i)
            out += MdSpan(MdKind.LINK, b.pos, i + 1, if (b.image) 1 else 0)
            return finishLink(b, i + 1)
        }
        brackets.removeAt(brackets.size - 1)
        return i + 1
    }

    private fun emitLinkHead(
        b: Bracket,
        textStart: Int,
        closeIdx: Int,
    ) {
        out += MdSpan(MdKind.LINK_MARKER, b.pos, textStart) // "[" or "!["
        if (closeIdx > textStart) out += MdSpan(MdKind.LINK_TEXT, textStart, closeIdx)
        out += MdSpan(MdKind.LINK_MARKER, closeIdx, closeIdx + 1) // "]"
    }

    private fun finishLink(
        b: Bracket,
        end: Int,
    ): Int {
        processEmphasis(b.delimBottom)
        brackets.removeAt(brackets.size - 1)
        if (!b.image) for (x in brackets) if (!x.image) x.active = false
        return end
    }

    private fun findLabelEnd(from: Int): Int {
        var j = from
        while (j < n && j - from <= 999) {
            when (s[j]) {
                '\\' -> j += 2
                '[' -> return -1
                ']' -> return j
                else -> j++
            }
        }
        return -1
    }

    private class Tail(
        val destStart: Int,
        val destEnd: Int,
        val angle: Boolean,
        val titleStart: Int,
        val titleEnd: Int,
        val end: Int,
    )

    /** Parses "(dest "title")" starting at the '('. Returns null when it is not a valid inline link tail. */
    private fun parseInlineTail(open: Int): Tail? {
        var j = skipWsOneNewline(open + 1)
        if (j >= n) return null
        val destStart: Int
        val destEnd: Int
        var angle = false
        if (s[j] == '<') {
            var k = j + 1
            while (k < n && s[k] != '>' && s[k] != '\n' && s[k] != '<') {
                if (s[k] == '\\') k++
                k++
            }
            if (k >= n || s[k] != '>') return null
            destStart = j + 1
            destEnd = k
            angle = true
            j = k + 1
        } else {
            var depth = 0
            var k = j
            while (k < n) {
                val c = s[k]
                if (c == '\\' && k + 1 < n && isAsciiPunct(s[k + 1])) {
                    k += 2
                    continue
                }
                if (c == '(') {
                    depth++
                    if (depth > 32) return null
                } else if (c == ')') {
                    if (depth == 0) break
                    depth--
                } else if (c == ' ' || c.code < 0x20 || c.code == 0x7F) {
                    break
                }
                k++
            }
            if (depth != 0) return null
            destStart = j
            destEnd = k
            j = k
        }
        val beforeTitle = j
        j = skipWsOneNewline(j)
        var tStart = -1
        var tEnd = -1
        if (j < n && j > beforeTitle && (s[j] == '"' || s[j] == '\'' || s[j] == '(')) {
            val closeCh = if (s[j] == '(') ')' else s[j]
            val failedFrom = noTitleCloseFrom[closeCh]
            if (failedFrom != null && j + 1 >= failedFrom) return null
            var k = j + 1
            while (k < n && s[k] != closeCh) {
                if (s[k] == '\\') {
                    k++
                } else if (s[j] == '(' && s[k] == '(') {
                    return null
                }
                k++
            }
            if (k >= n) {
                noTitleCloseFrom[closeCh] = j + 1
                return null
            }
            tStart = j
            tEnd = k + 1
            j = skipWsOneNewline(k + 1)
        }
        if (j >= n || s[j] != ')') return null
        return Tail(destStart, destEnd, angle, tStart, tEnd, j + 1)
    }

    private fun skipWsOneNewline(from: Int): Int {
        var j = from
        var nl = false
        while (j < n) {
            val c = s[j]
            if (c == ' ' || c == '\t') {
                j++
            } else if (c == '\n' && !nl) {
                nl = true
                j++
            } else {
                break
            }
        }
        return j
    }

    // ------------------------------------------------------------------ <...>: autolinks & inline HTML
    private fun angle(i: Int): Int {
        // autolink <scheme:...> or <email>
        var k = i + 1
        while (k < n && k - i < 2048 && s[k] != '>' && s[k] != '<' && s[k] != ' ' && s[k] != '\n' &&
            s[k].code >= 0x20
        ) {
            k++
        }
        if (k < n && s[k] == '>' && k > i + 1) {
            val body = s.subSequence(i + 1, k)
            if (URI_AUTOLINK.matches(body) || EMAIL_AUTOLINK.matches(body)) {
                out += MdSpan(MdKind.AUTOLINK, i, k + 1)
                out += MdSpan(MdKind.LINK_MARKER, i, i + 1)
                out += MdSpan(MdKind.LINK_URL, i + 1, k)
                out += MdSpan(MdKind.LINK_MARKER, k, k + 1)
                return k + 1
            }
        }
        val end = htmlInlineEnd(i)
        if (end > 0) {
            out += MdSpan(MdKind.HTML_INLINE, i, end)
            return end
        }
        return i + 1
    }

    private fun startsWithAt(
        i: Int,
        str: String,
    ): Boolean {
        if (i + str.length > n) return false
        for (x in str.indices) if (s[i + x] != str[x]) return false
        return true
    }

    private fun indexOf(
        str: String,
        from: Int,
    ): Int {
        val failed = noMatchFrom[str]
        if (failed != null && from >= failed) return -1
        var j = from
        while (j + str.length <= n) {
            if (startsWithAt(j, str)) return j
            j++
        }
        noMatchFrom[str] = if (failed == null) from else minOf(failed, from)
        return -1
    }

    /** Returns the end offset (exclusive) of an inline HTML construct starting at i, or -1. */
    private fun htmlInlineEnd(i: Int): Int {
        if (startsWithAt(i, "<!--")) {
            val e = indexOf("-->", i + 2)
            return if (e < 0) -1 else e + 3
        }
        if (startsWithAt(i, "<?")) {
            val e = indexOf("?>", i + 2)
            return if (e < 0) -1 else e + 2
        }
        if (startsWithAt(i, "<![CDATA[")) {
            val e = indexOf("]]>", i + 9)
            return if (e < 0) -1 else e + 3
        }
        if (startsWithAt(i, "<!") && i + 2 < n &&
            s[i + 2].isAsciiLetter()
        ) {
            val e = indexOf(">", i + 2)
            return if (e < 0) -1 else e + 1
        }
        if (startsWithAt(i, "</")) {
            var j = i + 2
            if (j >= n || !s[j].isAsciiLetter()) return -1
            while (j < n && (s[j].isAsciiLetterOrDigit() || s[j] == '-')) j++
            while (j < n && (s[j] == ' ' || s[j] == '\t' || s[j] == '\n')) j++
            return if (j < n && s[j] == '>') j + 1 else -1
        }
        return openTagEnd(i)
    }

    /** Complete open tag `<name attr="v" ...>` or `<name/>` starting at i; returns end or -1. */
    private fun openTagEnd(i: Int): Int {
        var j = i + 1
        if (j >= n || !s[j].isAsciiLetter()) return -1
        while (j < n && (s[j].isAsciiLetterOrDigit() || s[j] == '-')) j++
        while (true) {
            val wsStart = j
            while (j < n && (s[j] == ' ' || s[j] == '\t' || s[j] == '\n')) j++
            if (j >= n) return -1
            if (s[j] == '>') return j + 1
            if (s[j] == '/') return if (j + 1 < n && s[j + 1] == '>') j + 2 else -1
            if (j == wsStart) return -1 // attributes need leading whitespace
            if (!(s[j].isAsciiLetter() || s[j] == '_' || s[j] == ':')) return -1
            while (j < n && (s[j].isAsciiLetterOrDigit() || s[j] in "_.:-")) j++
            var k = j
            while (k < n && (s[k] == ' ' || s[k] == '\t' || s[k] == '\n')) k++
            if (k < n && s[k] == '=') {
                k++
                while (k < n && (s[k] == ' ' || s[k] == '\t' || s[k] == '\n')) k++
                if (k >= n) return -1
                val q = s[k]
                if (q == '"' || q == '\'') {
                    val e = indexOf(q.toString(), k + 1)
                    if (e < 0) return -1
                    j = e + 1
                } else {
                    val st = k
                    while (k < n && !(s[k] == ' ' || s[k] == '\t' || s[k] == '\n' || s[k] in "\"'=<>`")) k++
                    if (k == st) return -1
                    j = k
                }
            }
        }
    }

    private fun entity(i: Int): Int {
        var j = i + 1
        if (j < n && s[j] == '#') {
            j++
            val hex = j < n && (s[j] == 'x' || s[j] == 'X')
            if (hex) j++
            val st = j
            while (j < n && j - st < (if (hex) 6 else 7) && (if (hex) s[j].isHexDigitAscii() else s[j] in '0'..'9')) j++
            if (j > st && j < n && s[j] == ';') {
                out += MdSpan(MdKind.ENTITY, i, j + 1)
                return j + 1
            }
            return i + 1
        }
        val st = j
        while (j < n && j - st < 32 && s[j].isAsciiLetterOrDigit()) j++
        if (j - st >= 2 && s[st].isAsciiLetter() && j < n &&
            s[j] == ';'
        ) {
            out += MdSpan(MdKind.ENTITY, i, j + 1)
            return j + 1
        }
        return i + 1
    }

    // ------------------------------------------------------------------ GFM bare URLs
    private fun bareUrl(i: Int): Int {
        if (i > 0) {
            val p = s[i - 1]
            if (!(p.isWhitespace() || p == '*' || p == '_' || p == '~' || p == '(')) return i + 1
        }
        val schemeLen =
            when {
                startsWithIgnoreCase(i, "https://") -> 8
                startsWithIgnoreCase(i, "http://") -> 7
                startsWithIgnoreCase(i, "www.") -> 0
                else -> return i + 1
            }
        val domStart = i + schemeLen
        var j = domStart
        while (j < n) {
            val c = s[j]
            if (c.isAsciiLetterOrDigit() || c == '-' || c == '_' || c == '.' ||
                (c.code > 127 && Character.isLetterOrDigit(c))
            ) {
                j++
            } else {
                break
            }
        }
        if (j == domStart) return i + 1
        // path
        while (j < n && !s[j].isWhitespace() && s[j] != '<') j++
        var end = j
        // trailing punctuation trimming
        while (end > domStart) {
            val c = s[end - 1]
            if (c in "?!.,:*_~'\"") {
                end--
                continue
            }
            if (c == ')') {
                var open = 0
                var close = 0
                for (x in i until end) {
                    if (s[x] == '(') {
                        open++
                    } else if (s[x] == ')') {
                        close++
                    }
                }
                if (close > open) {
                    end--
                    continue
                }
            }
            if (c == ';') {
                var k = end - 2
                while (k > i && s[k].isAsciiLetterOrDigit()) k--
                if (k >= i && s[k] == '&' && k < end - 2) {
                    end = k
                    continue
                }
            }
            break
        }
        if (end <= domStart) return i + 1
        out += MdSpan(MdKind.AUTOLINK, i, end)
        return end
    }

    // ------------------------------------------------------------------ GFM bare e-mail autolinks

    /** Post-pass (like commonmark-java's autolink extension): e-mails in text not covered by other constructs. */
    private fun emailAutolinks() {
        var hasAt = false
        for (x in 0 until n) {
            if (s[x] == '@') {
                hasAt = true
                break
            }
        }
        if (!hasAt) return
        // 1 = inside an atomic construct (code, link, autolink, html, entity, footnote), 2 = emphasis-like marker
        val cover = ByteArray(n)
        for (x in firstOut until out.size) {
            val sp = out[x]
            val v: Byte =
                when (sp.kind) {
                    MdKind.CODE_SPAN, MdKind.LINK, MdKind.AUTOLINK, MdKind.HTML_INLINE, MdKind.ENTITY,
                    MdKind.FOOTNOTE_REF,
                    -> 1

                    MdKind.EMPHASIS_MARKER, MdKind.ESCAPE_MARKER -> 2

                    else -> 0
                }
            if (v.toInt() != 0) for (y in sp.start until sp.end) if (cover[y].toInt() == 0) cover[y] = v
        }
        var a = 0
        while (a < n) {
            if (s[a] != '@' || cover[a].toInt() != 0) {
                a++
                continue
            }
            var j = a
            while (j > 0 && isEmailLocal(s[j - 1]) && cover[j - 1].toInt() == 0) j--
            if (j == a || (j > 0 && s[j - 1] == '\\')) {
                a++
                continue
            }
            var k = a + 1
            var dots = 0
            var segLen = 0
            while (k < n && cover[k].toInt() == 0) {
                val c = s[k]
                if (c.isAsciiLetterOrDigit() || c == '-' || c == '_') {
                    segLen++
                    k++
                } else if (c == '.' && segLen > 0 && k + 1 < n && cover[k + 1].toInt() == 0 &&
                    (s[k + 1].isAsciiLetterOrDigit() || s[k + 1] == '-' || s[k + 1] == '_')
                ) {
                    dots++
                    segLen = 0
                    k++
                } else {
                    break
                }
            }
            val end = k
            if (dots > 0 && segLen > 0 && s[end - 1] != '-' && s[end - 1] != '_') out += MdSpan(MdKind.AUTOLINK, j, end)
            a = maxOf(a + 1, end)
        }
    }

    private fun isEmailLocal(c: Char) = c.isAsciiLetterOrDigit() || c == '.' || c == '-' || c == '_' || c == '+'

    private fun startsWithIgnoreCase(
        i: Int,
        str: String,
    ): Boolean {
        if (i + str.length > n) return false
        for (x in str.indices) if (s[i + x].lowercaseChar() != str[x]) return false
        return true
    }

    companion object {
        private val URI_AUTOLINK = Regex("[A-Za-z][A-Za-z0-9+.\\-]{1,31}:[^\\s<>]*")
        private val EMAIL_AUTOLINK =
            Regex(
                "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~\\-]+@[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9\\-]{0,61}[A-Za-z0-9])?)*",
            )

        fun isAsciiPunct(c: Char): Boolean = c in "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

        fun isUnicodeWs(cp: Int): Boolean =
            cp == 0x09 || cp == 0x0A || cp == 0x0C || cp == 0x0D ||
                Character.getType(cp) == Character.SPACE_SEPARATOR.toInt()

        fun isUnicodePunct(cp: Int): Boolean =
            when (Character.getType(cp)) {
                Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
                Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
                Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
                Character.OTHER_PUNCTUATION.toInt(), Character.MATH_SYMBOL.toInt(),
                Character.CURRENCY_SYMBOL.toInt(), Character.MODIFIER_SYMBOL.toInt(),
                Character.OTHER_SYMBOL.toInt(),
                -> true

                else -> false
            }

        /** CommonMark label normalisation: trim, collapse internal whitespace, case-fold. */
        fun normalizeLabel(cs: CharSequence): String {
            val sb = StringBuilder(cs.length)
            var ws = false
            for (c in cs) {
                if (c.isWhitespace()) {
                    ws = true
                    continue
                }
                if (ws && sb.isNotEmpty()) sb.append(' ')
                ws = false
                sb.append(c)
            }
            return sb
                .toString()
                .lowercase()
                .uppercase()
                .lowercase()
        }
    }
}

internal fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

internal fun Char.isAsciiLetterOrDigit() = isAsciiLetter() || this in '0'..'9'

internal fun Char.isHexDigitAscii() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
