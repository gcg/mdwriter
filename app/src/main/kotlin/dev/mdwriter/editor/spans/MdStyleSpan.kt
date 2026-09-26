package dev.mdwriter.editor.spans

/**
 * Marker interface for every Android span object the editor places (01 §6.2 rule 9; not to be confused with
 * `dev.mdwriter.markdown.MdSpan`, the `:core:markdown` data class — factcheck C3). T07's reconcile only ever
 * removes spans that implement this interface; it never touches composing spans, `SuggestionSpan`,
 * `SpellCheckSpan`, selection or watcher spans (rule 5).
 *
 * [HangRoomSpan] is the one span the editor places that deliberately does NOT implement this interface: the
 * document-wide gutter span is set once on install and never reconciled (rule 7 exception).
 */
interface MdStyleSpan {
    val kind: Int
    val arg: Int
}

/** Stable integer ids for every [MdStyleSpan] subclass, used as the reconcile key (kind, arg, start, end). */
object SpanKind {
    const val HEADING = 1 // arg = level 1..6
    const val STRONG = 2
    const val EMPHASIS = 3
    const val CODE = 4
    const val CODE_BLOCK = 5
    const val MARKER = 6
    const val STRIKE = 7
    const val MARK = 8
    const val DONE_TASK = 9
    const val LINK_UNDERLINE = 10
    const val TABLE_ROW = 11 // arg 0 header, 1 delimiter, 2 body
    const val TASK = 12 // arg 1 checked
    const val HEADING_HANG = 13 // arg = marker width px
    const val HANGING_INDENT = 14 // arg = prefix width px
    const val MONO = 15

    /** 02 §4: `* _ ** __` stay text colour (iA-faithful) — never flip this without updating the design spec. */
    const val DIM_EMPHASIS_MARKERS = false
}
