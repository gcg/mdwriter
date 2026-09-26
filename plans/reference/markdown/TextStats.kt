package mdwriter.markdown

public data class Stats(val words: Int, val chars: Int, val charsNoSpaces: Int, val sentences: Int, val tasks: Int, val tasksDone: Int) {
    public operator fun plus(o: Stats): Stats = Stats(words + o.words, chars + o.chars, charsNoSpaces + o.charsNoSpaces, sentences + o.sentences, tasks + o.tasks, tasksDone + o.tasksDone)
    /** Minutes at [wpm] words per minute (238 = Brysbaert 2019 non-fiction silent reading average). */
    public fun readingMinutes(wpm: Int = 238): Double = words.toDouble() / wpm

    /** Whole minutes for display: 0 words -> 0, otherwise at least 1 (ceil). */
    public fun readingMinutesRounded(wpm: Int = 238): Int = if (words == 0) 0 else maxOf(1, kotlin.math.ceil(readingMinutes(wpm)).toInt())
    public companion object { public val ZERO: Stats = Stats(0, 0, 0, 0, 0, 0) }
}

/**
 * Markdown-aware, deterministic text statistics (same results on JVM unit tests and Android).
 * Words: runs of letters/digits/marks (inner ' ’ - . , allowed between letters/digits);
 * every Han / Hiragana / Katakana code point counts as one word (MS Word convention).
 * Excluded from words: syntax markers, link/image destinations and titles, HTML tags and comments
 * (text between tags of an HTML block still counts), front matter, link reference definitions,
 * entities, task boxes, footnote labels, fence info strings. Code (spans and blocks) counts.
 * Characters: Unicode code points excluding line breaks (grapheme counting is a UI-layer option).
 */
public object TextStats {
    private val EXCLUDE = setOf(
        MdKind.LINK_URL, MdKind.LINK_TITLE, MdKind.HTML_INLINE, MdKind.FRONT_MATTER,
        MdKind.LINK_DEF, MdKind.ENTITY, MdKind.FOOTNOTE_REF, MdKind.CODE_INFO,
    )

    public fun compute(text: CharSequence, from: Int, to: Int, spans: List<MdSpan>): Stats {
        // build an exclusion mask for [from, to)
        val len = to - from
        val excluded = BooleanArray(len)
        var tasks = 0; var done = 0
        for (sp in spans) {
            if (sp.kind == MdKind.TASK_MARKER && sp.start >= from && sp.start < to) { tasks++; if (sp.arg == 1) done++ }
            if (sp.kind.isMarker || sp.kind in EXCLUDE) {
                val s = maxOf(sp.start, from) - from; val e = minOf(sp.end, to) - from
                for (x in s until e) excluded[x] = true
            } else if (sp.kind == MdKind.HTML_BLOCK) {
                val s = maxOf(sp.start, from); val e = minOf(sp.end, to)
                if (sp.arg == 2) { for (x in s until e) excluded[x - from] = true } // comment block
                else {                                                             // exclude <...> only
                    var inTag = false
                    for (x in s until e) {
                        val c = text[x]
                        if (c == '<') inTag = true
                        if (inTag) excluded[x - from] = true
                        if (c == '>') inTag = false
                    }
                }
            }
        }
        var words = 0; var chars = 0; var charsNs = 0; var sentences = 0
        var inWord = false; var sentenceHasWord = false
        var i = from
        while (i < to) {
            val cp = Character.codePointAt(text, i)
            val n = Character.charCount(cp)
            val ex = excluded[i - from]
            if (cp != '\n'.code && cp != '\r'.code) { chars++; if (!Character.isWhitespace(cp)) charsNs++ }
            if (!ex) {
                if (isCjk(cp)) { words++; inWord = false; sentenceHasWord = true }
                else if (isWordChar(cp)) { if (!inWord) { words++; inWord = true }; sentenceHasWord = true }
                else if (inWord && isJoiner(cp) && i + n < to && !excluded[i + n - from] && isWordChar(Character.codePointAt(text, i + n)) &&
                    (cp != ','.code || (Character.isDigit(Character.codePointBefore(text, i)) && Character.isDigit(Character.codePointAt(text, i + n))))) { /* stay in word */ }
                else {
                    inWord = false
                    if ((cp == '.'.code || cp == '!'.code || cp == '?'.code || cp == '。'.code || cp == '！'.code || cp == '？'.code) && sentenceHasWord) { sentences++; sentenceHasWord = false }
                }
            } else inWord = false
            i += n
        }
        if (sentenceHasWord) sentences++
        return Stats(words, chars, charsNs, sentences, tasks, done)
    }

    private fun isWordChar(cp: Int): Boolean = Character.isLetterOrDigit(cp) || when (Character.getType(cp)) {
        Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }

    private fun isJoiner(cp: Int) = cp == '\''.code || cp == '’'.code || cp == '-'.code || cp == '.'.code || cp == ','.code || cp == '_'.code

    private fun isCjk(cp: Int): Boolean {
        val sc = Character.UnicodeScript.of(cp)
        return sc == Character.UnicodeScript.HAN || sc == Character.UnicodeScript.HIRAGANA || sc == Character.UnicodeScript.KATAKANA
    }
}
