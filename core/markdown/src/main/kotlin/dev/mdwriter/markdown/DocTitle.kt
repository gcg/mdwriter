package dev.mdwriter.markdown

/**
 * Pure helpers for the library and file system: the document title from its content, THE excerpt
 * rule (library rows, T12), and safe file-name sanitizing (T10/T12). None of this touches Android.
 */
public object DocTitle {
    /** Base name used when [sanitizeFileName] would otherwise return an empty string. */
    public const val FALLBACK_NAME: String = "Untitled"

    /** Cap for [sanitizeFileName], in UTF-16 units; a dangling high surrogate at the cut is dropped. */
    public const val MAX_NAME_LENGTH: Int = 80

    /** Cap for [excerpt], in UTF-16 units (a longer line is cut to 119 chars + `"…"`). */
    public const val EXCERPT_MAX: Int = 120

    private const val FORBIDDEN = "/\\:*?\"<>|"
    private val FENCE_LINE = Regex("^[ \\t]*(?:>[ \\t]?)*[ \\t]*(?:`{3,}|~{3,})")

    /**
     * First line with visible text, markup stripped; null if none.
     * Example: `fromContent("# Hello World\n\nBody")` is `"Hello World"`.
     */
    public fun fromContent(text: String): String? = contentLines(text).firstOrNull()

    /**
     * First line with visible text AFTER the title line, at most [EXCERPT_MAX] chars; null if none.
     * Example: `excerpt("# Title\n\nFirst **para** line\nsecond")` is `"First para line"`.
     */
    public fun excerpt(text: String): String? = contentLines(text).drop(1).firstOrNull()?.let(::cap)

    /**
     * Base name for [name] with no extension; never empty (falls back to [FALLBACK_NAME]).
     * In order: control characters become a space; [FORBIDDEN] characters (`/\:*?"<>|`) are
     * dropped; whitespace runs collapse to one space and the ends are trimmed; leading dots and
     * trailing dots/spaces are stripped; the result is capped at [MAX_NAME_LENGTH] UTF-16 units
     * (a dangling high surrogate at the cut is dropped); trailing dots/spaces are stripped again.
     * Example: `sanitizeFileName("My: Note/Draft?")` is `"My NoteDraft"`.
     */
    public fun sanitizeFileName(name: String): String {
        val noControl =
            buildString(name.length) {
                for (ch in name) append(if (Character.isISOControl(ch)) ' ' else ch)
            }
        var s = noControl.filterNot { it in FORBIDDEN }
        s = s.replace(WHITESPACE_RUN, " ").trim()
        s = s.trimStart('.')
        s = s.trimEnd('.', ' ')
        if (s.length > MAX_NAME_LENGTH) {
            var cut = MAX_NAME_LENGTH
            if (Character.isHighSurrogate(s[cut - 1])) cut--
            s = s.substring(0, cut)
        }
        s = s.trimEnd('.', ' ')
        return s.ifEmpty { FALLBACK_NAME }
    }

    private val WHITESPACE_RUN = Regex("\\s+")

    /** Cuts [s] to [EXCERPT_MAX] chars (119 + an ellipsis) when it's longer. */
    private fun cap(s: String): String = if (s.length <= EXCERPT_MAX) s else s.take(EXCERPT_MAX - 1) + "…"

    /**
     * Lazily yields every line with visible text, in document order: skips a leading YAML front
     * matter block (only when it actually closes, matching [MarkdownHighlighter]'s own rule), skips
     * fenced-code delimiter lines (by shape; a fence body line is otherwise plain text), and skips
     * lines whose [plain] text is blank.
     */
    private fun contentLines(text: String): Sequence<String> =
        sequence {
            val lines = text.split('\n')
            val closesAt = frontMatterClosesAt(lines)
            var i = if (closesAt >= 0) closesAt + 1 else 0
            while (i < lines.size) {
                val line = lines[i]
                if (FENCE_LINE.find(line) == null) {
                    val p = plain(line)
                    if (p.isNotEmpty()) yield(p)
                }
                i++
            }
        }

    /** Index of the line closing a front-matter block opened by [lines][0], or -1 if it never closes. */
    private fun frontMatterClosesAt(lines: List<String>): Int {
        if (lines.isEmpty() || lines[0].trimEnd() != "---") return -1
        val max = minOf(lines.size, 256)
        for (i in 1 until max) {
            val t = lines[i].trimEnd()
            if (t == "---" || t == "...") return i
        }
        return -1
    }

    /**
     * Plain text of a single line, parsed in isolation: front matter is off (irrelevant for one
     * line) and highlighting is off. A link-reference-definition or HTML-block line is "" outright;
     * otherwise every marker span, [MdKind.HTML_INLINE] and [MdKind.FOOTNOTE_REF] is deleted, link/
     * image punctuation and destination/title are dropped (keeping only the link text), autolink
     * `<`/`>` are dropped, then whitespace runs collapse to one space and the ends are trimmed.
     */
    private fun plain(line: String): String {
        val hl = MarkdownHighlighter(enableHighlight = false, enableFrontMatter = false)
        hl.fullScan(line)
        val info = hl.lineInfo(0)
        if (info.type == BlockType.LINK_DEF || info.type == BlockType.HTML) return ""
        val spans = hl.spans()
        val del = ArrayList<IntRange>()
        for (sp in spans) {
            if (sp.kind.isMarker || sp.kind == MdKind.HTML_INLINE ||
                sp.kind == MdKind.FOOTNOTE_REF
            ) {
                del += sp.start until sp.end
            }
        }
        MarkupStrip.inlineDeletions(line, spans, 0, line.length, del)
        return MarkupStrip.deleteAll(line, del).replace(WHITESPACE_RUN, " ").trim()
    }
}
