package dev.mdwriter.editor.spans

import android.graphics.Paint
import android.text.TextPaint
import kotlin.math.ceil

/** Turns a pure [SpanSpec] into the real Android [MdStyleSpan] object it describes. */
class SpanMaterializer(
    private val s: EditorStyle,
) {
    fun create(spec: SpanSpec): MdStyleSpan =
        when (spec.kind) {
            SpanKind.HEADING -> HeadingSpan(spec.arg, s)
            SpanKind.STRONG -> StrongSpan(s)
            SpanKind.EMPHASIS -> EmphasisSpan(s)
            SpanKind.CODE -> CodeSpan(s)
            SpanKind.CODE_BLOCK -> CodeBlockSpan(s)
            SpanKind.MARKER -> MarkerSpan(s)
            SpanKind.STRIKE -> StrikeSpan()
            SpanKind.MARK -> MarkSpan(s)
            SpanKind.DONE_TASK -> DoneTaskSpan(s)
            SpanKind.LINK_UNDERLINE -> LinkUnderlineSpan()
            SpanKind.TABLE_ROW -> TableRowSpan(spec.arg, s)
            SpanKind.TASK -> TaskSpan(spec.arg)
            SpanKind.HEADING_HANG -> HeadingHangSpan(spec.arg, s)
            SpanKind.HANGING_INDENT -> HangingIndentSpan(spec.arg)
            SpanKind.MONO -> MonoSpan(s)
            else -> error("unknown SpanKind ${spec.kind}")
        }
}

/**
 * Measures body-size, regular-face text width for [SpanFactory]'s hang/indent widths. Not thread-safe (owns one
 * mutable [TextPaint]): use one instance per document build (`Dispatchers.Default`) and a separate one for the
 * main-thread restyler (T07) — never share one between the two.
 */
class PaintTextMeasurer(
    private val s: EditorStyle,
) : TextMeasurer {
    private val p = TextPaint(Paint.ANTI_ALIAS_FLAG)

    override fun widthPx(
        text: CharSequence,
        start: Int,
        end: Int,
    ): Int {
        p.textSize = s.textSizePx
        p.typeface = s.fonts.regular
        return ceil(p.measureText(text, start, end)).toInt()
    }
}
