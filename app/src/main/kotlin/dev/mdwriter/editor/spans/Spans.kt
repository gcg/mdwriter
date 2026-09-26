package dev.mdwriter.editor.spans

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.MetricAffectingSpan
import android.text.style.UpdateAppearance
import android.text.style.UpdateLayout

/*
 * Every span class the editor places (01 §6.2, §10 rules 4/6/7). Each is a plain subclass of
 * CharacterStyle/MetricAffectingSpan/LeadingMarginSpan/LineBackgroundSpan — never one of the framework's own
 * parcelable span types (rule 6) — and reads colours/sizes from the shared, mutable EditorStyle at draw/measure
 * time, so a style change needs no new span objects, only MarkdownEditText.reflowAll.
 */

/** `## Title` / setext heading content. Size + weight scale by [arg] (level 1..6); H6 also dims to [EditorColors.textSecondary]. */
class HeadingSpan(
    override val arg: Int,
    private val s: EditorStyle,
) : MetricAffectingSpan(),
    MdStyleSpan {
    override val kind get() = SpanKind.HEADING

    private fun apply(tp: TextPaint) {
        tp.textSize = s.textSizePx * s.headingScale[arg]
        tp.typeface = s.fonts.of(bold = true, italic = s.fonts.isItalic(tp.typeface))
    }

    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    override fun updateDrawState(tp: TextPaint) {
        apply(tp)
        if (arg == 6) tp.color = s.colors.textSecondary
    }
}

private fun isMonoFace(
    s: EditorStyle,
    t: Typeface?,
): Boolean = t === s.fonts.mono || t === s.fonts.monoBold

/** `**strong**` / `__strong__`. Mono text stays mono (bumped to [FontSet.monoBold]); other faces get a bold variant. */
class StrongSpan(
    private val s: EditorStyle,
) : MetricAffectingSpan(),
    MdStyleSpan {
    override val kind get() = SpanKind.STRONG
    override val arg get() = 0

    private fun apply(tp: TextPaint) {
        tp.typeface =
            if (isMonoFace(s, tp.typeface)) {
                s.fonts.monoBold
            } else {
                s.fonts.of(bold = true, italic = s.fonts.isItalic(tp.typeface))
            }
    }

    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    override fun updateDrawState(tp: TextPaint) = apply(tp)
}

/** `*emphasis*` / `_emphasis_`. Mono has no italic face, so a mono run is left as-is. */
class EmphasisSpan(
    private val s: EditorStyle,
) : MetricAffectingSpan(),
    MdStyleSpan {
    override val kind get() = SpanKind.EMPHASIS
    override val arg get() = 0

    private fun apply(tp: TextPaint) {
        if (!isMonoFace(s, tp.typeface)) {
            tp.typeface = s.fonts.of(bold = s.fonts.isBold(tp.typeface), italic = true)
        }
    }

    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    override fun updateDrawState(tp: TextPaint) = apply(tp)
}

/** `` `code span` ``. Mono face + [EditorColors.codeBg] background. */
class CodeSpan(
    private val s: EditorStyle,
) : MetricAffectingSpan(),
    MdStyleSpan {
    override val kind get() = SpanKind.CODE
    override val arg get() = 0

    private fun apply(tp: TextPaint) {
        tp.typeface = if (s.fonts.isBold(tp.typeface)) s.fonts.monoBold else s.fonts.mono
    }

    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    override fun updateDrawState(tp: TextPaint) {
        apply(tp)
        tp.bgColor = s.colors.codeBg
    }
}

/** Fenced/indented code block line (incl. fence lines): mono face + a full-width [EditorColors.codeBg] band. */
class CodeBlockSpan(
    private val s: EditorStyle,
) : MetricAffectingSpan(),
    LineBackgroundSpan,
    MdStyleSpan {
    override val kind get() = SpanKind.CODE_BLOCK
    override val arg get() = 0

    override fun updateMeasureState(tp: TextPaint) {
        tp.typeface = s.fonts.mono
    }

    override fun updateDrawState(tp: TextPaint) {
        tp.typeface = s.fonts.mono
    }

    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int,
    ) {
        val old = paint.color
        paint.color = s.colors.codeBg
        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
        paint.color = old
    }
}

/** Front-matter body line: mono face only (the grey comes from a separate [MarkerSpan] over the same MdSpan range). */
class MonoSpan(
    private val s: EditorStyle,
) : MetricAffectingSpan(),
    MdStyleSpan {
    override val kind get() = SpanKind.MONO
    override val arg get() = 0

    private fun apply(tp: TextPaint) {
        tp.typeface = s.fonts.mono
    }

    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    override fun updateDrawState(tp: TextPaint) = apply(tp)
}

/** Table row (header/delimiter/body per [arg]): mono face, header row bold so columns align. */
class TableRowSpan(
    override val arg: Int,
    private val s: EditorStyle,
) : MetricAffectingSpan(),
    MdStyleSpan {
    override val kind get() = SpanKind.TABLE_ROW

    private fun apply(tp: TextPaint) {
        tp.typeface = if (arg == 0) s.fonts.monoBold else s.fonts.mono
    }

    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    override fun updateDrawState(tp: TextPaint) = apply(tp)
}

/** Grey syntax markers (links, code fences, tables, HTML, escapes, front matter, ...): [EditorColors.markup]. */
class MarkerSpan(
    private val s: EditorStyle,
) : CharacterStyle(),
    UpdateAppearance,
    MdStyleSpan {
    override val kind get() = SpanKind.MARKER
    override val arg get() = 0

    override fun updateDrawState(tp: TextPaint) {
        tp.color = s.colors.markup
    }
}

/** `~~strike~~` (whole construct, incl. delimiters). */
class StrikeSpan :
    CharacterStyle(),
    UpdateAppearance,
    MdStyleSpan {
    override val kind get() = SpanKind.STRIKE
    override val arg get() = 0

    override fun updateDrawState(tp: TextPaint) {
        tp.isStrikeThruText = true
    }
}

/** `==highlight==` (iA extension; only produced when the setting enables it). */
class MarkSpan(
    private val s: EditorStyle,
) : CharacterStyle(),
    UpdateAppearance,
    MdStyleSpan {
    override val kind get() = SpanKind.MARK
    override val arg get() = 0

    override fun updateDrawState(tp: TextPaint) {
        tp.bgColor = s.colors.highlightBg
        tp.color = s.colors.highlightText
    }
}

/** A checked task item's text: dimmed + struck through. */
class DoneTaskSpan(
    private val s: EditorStyle,
) : CharacterStyle(),
    UpdateAppearance,
    MdStyleSpan {
    override val kind get() = SpanKind.DONE_TASK
    override val arg get() = 0

    override fun updateDrawState(tp: TextPaint) {
        tp.color = s.colors.textSecondary
        tp.isStrikeThruText = true
    }
}

/** Autolink / bare URL underline (the `<` `>` markers themselves are greyed separately, per 02 §4). */
class LinkUnderlineSpan :
    CharacterStyle(),
    UpdateAppearance,
    MdStyleSpan {
    override val kind get() = SpanKind.LINK_UNDERLINE
    override val arg get() = 0

    override fun updateDrawState(tp: TextPaint) {
        tp.isUnderlineText = true
    }
}

/**
 * Hit-test marker only (T08 taps this to toggle the task). [arg] = 1 when checked. Deliberately does **not**
 * implement the framework's no-copy span marker interface: the `SpannableStringBuilder` copy constructor drops
 * spans that do (factcheck A12), and `MdEditableFactory.newEditable(ssb)` performs exactly such a copy when
 * `setText` installs a built document.
 */
class TaskSpan(
    override val arg: Int,
) : MdStyleSpan {
    override val kind get() = SpanKind.TASK
}

/**
 * Negative leading margin on a heading's first line: the `"## "` marker run hangs left into the whole-document
 * [HangRoomSpan] gutter, so the heading TEXT still aligns with the body column. [arg] = the marker's measured
 * width in px, clamped to the current gutter (zero effect at 0 gutter, i.e. Compact width class, 02 §3).
 */
class HeadingHangSpan(
    override val arg: Int,
    private val s: EditorStyle,
) : LeadingMarginSpan,
    UpdateLayout,
    MdStyleSpan {
    override val kind get() = SpanKind.HEADING_HANG

    override fun getLeadingMargin(first: Boolean): Int = if (first) -minOf(arg, s.gutterPx) else 0

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?,
    ) = Unit
}

/**
 * Wrapped-line indent for list items and block quotes (no quote bar, no italics — iA look, 02 §4): the first
 * visual line is flush; every wrapped line indents by [arg] px (the container prefix's measured width).
 */
class HangingIndentSpan(
    override val arg: Int,
) : LeadingMarginSpan,
    UpdateLayout,
    MdStyleSpan {
    override val kind get() = SpanKind.HANGING_INDENT

    override fun getLeadingMargin(first: Boolean): Int = if (first) 0 else arg

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?,
    ) = Unit
}

/**
 * Whole-document gutter so hanging markers draw INSIDE the layout (TextView clips its own padding). Deliberately
 * plain `LeadingMarginSpan` — **not** `UpdateLayout` (factcheck A17: on an `UpdateLayout` span,
 * `DynamicLayout.ChangeWatcher.onSpanChanged` reflows the WHOLE old range and the WHOLE new range on every
 * insert/delete that shifts its end, i.e. two full-document reflows per keystroke) — and deliberately **not**
 * [MdStyleSpan] (01 §10 rule 7 exception: T07's reconcile must never touch it). Set once on install with
 * `SPAN_INCLUSIVE_INCLUSIVE` (the one exception to rule 7's `SPAN_EXCLUSIVE_EXCLUSIVE`), never reconciled.
 */
class HangRoomSpan(
    private val s: EditorStyle,
) : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean): Int = s.gutterPx

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout?,
    ) = Unit
}
