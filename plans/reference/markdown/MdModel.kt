package mdwriter.markdown

/**
 * Token model for live in-editor Markdown styling (pure Kotlin, no Android types).
 *
 * Every span is a half-open range [start, end) in UTF-16 char offsets of the document.
 * `isMarker == true` kinds are Markdown syntax characters (to be dimmed / hung / hidden);
 * the others are "content" styles (bigger heading text, italics, code background, ...).
 * Content spans of inline constructs cover the WHOLE construct including its markers
 * (same convention as commonmark-java source spans), so markers inherit e.g. bold/italic and
 * the marker style (dim colour) is layered on top.
 *
 * Spans never cross a line break: multi-line constructs (emphasis over a soft break, code blocks,
 * block quotes) are emitted as one span per line. This lets the editor re-apply spans line by line.
 */
public enum class MdKind(public val isMarker: Boolean) {
    // ---- block-level content ----
    HEADING(false),          // arg = level 1..6. ATX: whole line from first '#' to line end. Setext: each content line.
    BLOCKQUOTE(false),       // arg = depth. From the first '>' to line end (also lazy continuation lines).
    CODE_BLOCK(false),       // one per fenced-code body line or indented-code line (line content range)
    CODE_INFO(false),        // info string of an opening fence ("kotlin")
    HTML_BLOCK(false),       // one per HTML block line; arg = CommonMark HTML block type 1..7 (2 = comment)
    TABLE_ROW(false),        // arg = 0 header row, 1 delimiter row, 2 body row; line content range
    FRONT_MATTER(false),     // one per YAML front-matter body line
    LINK_DEF(false),         // whole link reference definition line `[x]: url "t"`

    // ---- block-level markers ----
    HEADING_MARKER(true),    // ATX '#' run incl. following blanks; ATX closing '#' run; setext underline
    QUOTE_MARKER(true),      // '>' plus at most one following space
    LIST_MARKER(true),       // arg = nesting depth (0 = top). "-", "*", "+", "1.", "1)" (no trailing space)
    TASK_MARKER(true),       // "[ ]", "[x]", "[X]"; arg = 1 when checked
    CODE_FENCE(true),        // the ``` / ~~~ run of an opening or closing fence
    THEMATIC_BREAK(true),    // whole "***" / "---" / "___" line content
    TABLE_PIPE(true),        // each unescaped '|' in a table row
    FRONT_MATTER_FENCE(true),// the "---" / "..." lines around front matter

    // ---- inline content ----
    EMPHASIS(false),         // *x* or _x_  (range includes delimiters)
    STRONG(false),           // **x** or __x__
    STRIKETHROUGH(false),    // ~x~ or ~~x~~
    HIGHLIGHT(false),        // ==x== (iA Writer extension, optional, off by default)
    CODE_SPAN(false),        // `x` (range includes backtick runs)
    LINK(false),             // arg = 0 link, 1 image. Whole construct "[t](u "x")" / "![a](u)" / "[t][r]" / "[r]"
    LINK_TEXT(false),        // text between the brackets
    LINK_URL(false),         // destination (inline link, autolink body or definition)
    LINK_TITLE(false),       // title incl. its quotes/parens
    LINK_LABEL(false),       // reference label (in "[t][label]", or the "[label]" of a definition)
    AUTOLINK(false),         // "<https://x>" (range incl. <>), GFM bare URL "https://x", "www.x.y", bare e-mail "a@b.cd"
    FOOTNOTE_REF(false),     // "[^1]" (arg = 1 when it is the label of a footnote definition line)
    HTML_INLINE(false),      // inline tag / comment
    ENTITY(false),           // &amp; &#123; &#x1F;

    // ---- inline markers ----
    EMPHASIS_MARKER(true),   // the delimiter chars actually used: * _ ** __ ~ ~~ ==
    CODE_SPAN_MARKER(true),  // backtick runs of a code span
    LINK_MARKER(true),       // "!" "[" "]" "(" ")" and "<" ">" around a destination, ":" of a definition
    ESCAPE_MARKER(true),     // the backslash of a backslash escape
    HARD_BREAK(true),        // 2+ trailing spaces or the backslash before a line break
}

public data class MdSpan(val kind: MdKind, val start: Int, val end: Int, val arg: Int = 0) {
    override fun toString(): String = if (arg != 0) "$kind($arg)[$start,$end)" else "$kind[$start,$end)"
}

/** Leaf-block classification of one line (for hanging indents, smart editing, focus mode, outline). */
public enum class BlockType {
    BLANK, PARAGRAPH, ATX_HEADING, SETEXT_HEADING, SETEXT_UNDERLINE, THEMATIC_BREAK,
    FENCE_OPEN, FENCE_CLOSE, FENCED_CODE, INDENTED_CODE, HTML,
    TABLE_HEADER, TABLE_DELIMITER, TABLE_ROW, FRONT_MATTER_FENCE, FRONT_MATTER, LINK_DEF, FOOTNOTE_DEF;

    /** Lines where smart editing must not continue lists/quotes and inline toggles should be disabled. */
    public val isVerbatim: Boolean
        get() = this == FENCE_OPEN || this == FENCE_CLOSE || this == FENCED_CODE || this == INDENTED_CODE ||
            this == HTML || this == FRONT_MATTER || this == FRONT_MATTER_FENCE
}

/**
 * Per-line structure. [contentStart] is the absolute offset after all container prefixes of this line
 * (block-quote markers, list marker + spacing, task box), i.e. [start, contentStart) is the "prefix"
 * the editor may hang into the margin / use as the wrap indent.
 */
public data class LineInfo(
    val line: Int,
    val start: Int,
    val end: Int,             // exclusive, before the '\n'
    val contentStart: Int,
    val type: BlockType,
    val quoteDepth: Int,
    val listDepth: Int,
)

/**
 * Result of a (re)highlight: lines [firstLine, endLine) of the NEW text have new spans / line info;
 * [startOffset, endOffset) is the same region in chars (endOffset = end of line endLine-1, excl. '\n').
 * Spans outside this region are unchanged (they only moved with the text).
 * [full] = everything was rescanned (first scan, or the set of link reference definitions changed).
 */
public data class HighlightDelta(
    val firstLine: Int,
    val endLine: Int,
    val startOffset: Int,
    val endOffset: Int,
    val full: Boolean,
) {
    val isEmpty: Boolean get() = endLine <= firstLine
}

/** One heading of the document outline. */
public data class HeadingItem(val level: Int, val line: Int, val start: Int, val text: String)
