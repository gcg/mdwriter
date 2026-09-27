package dev.mdwriter.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.widget.TextView
import androidx.core.graphics.withSave

/**
 * Ascending sentence boundaries of [paragraph], always including `0` and `paragraph.length` (T15, 02 §5).
 * [IcuSentenceBreaker] is the real, on-device implementation; JVM tests use their own `java.text.BreakIterator`
 * breaker (`android.icu` must never appear in `:core:markdown`, 01 §2 — and this file lives in `:app`, never there).
 */
fun interface SentenceBreaker {
    fun boundaries(paragraph: String): IntArray
}

/** Real sentence breaker (`android.icu`, app-only). Created once per [MarkdownEditText], reused. */
class IcuSentenceBreaker : SentenceBreaker {
    private val bi =
        android.icu.text.BreakIterator
            .getSentenceInstance(
                android.icu.util.ULocale
                    .getDefault(),
            )

    override fun boundaries(paragraph: String): IntArray {
        bi.setText(paragraph)
        val out = ArrayList<Int>()
        var b = bi.first()
        while (b != android.icu.text.BreakIterator.DONE) {
            out += b
            b = bi.next()
        }
        return out.toIntArray()
    }
}

/** `[start, end)` — the range Focus Mode keeps at full colour. [NONE] means "Focus Mode is off". */
data class FocusRange(
    val start: Int,
    val end: Int,
) {
    companion object {
        val NONE = FocusRange(-1, -1)
    }
}

/** Pure focus-range math (02 §5): sentence/paragraph boundaries around the caret or selection. No Android import
 * except through [SentenceBreaker], which the caller supplies — this object itself stays JVM-testable. */
object FocusRanges {
    const val MAX_SCAN = 10_000

    fun paragraphStart(
        t: CharSequence,
        off: Int,
    ): Int {
        var i = off
        val lim = maxOf(0, off - MAX_SCAN)
        while (i > lim && t[i - 1] != '\n') i--
        return i
    }

    fun paragraphEnd(
        t: CharSequence,
        off: Int,
    ): Int {
        var i = off
        val lim = minOf(t.length, off + MAX_SCAN)
        while (i < lim && t[i] != '\n') i++
        return i
    }

    fun compute(
        t: CharSequence,
        selStart: Int,
        selEnd: Int,
        mode: FocusModeKind,
        br: SentenceBreaker,
    ): FocusRange {
        if (mode == FocusModeKind.Off || selStart < 0) return FocusRange.NONE
        val lo = minOf(selStart, selEnd).coerceIn(0, t.length)
        val hi = maxOf(selStart, selEnd).coerceIn(0, t.length)
        val p1s = paragraphStart(t, lo)
        val p2e = paragraphEnd(t, hi)
        if (mode == FocusModeKind.Paragraph) return FocusRange(p1s, p2e)
        val p1e = paragraphEnd(t, lo)
        val p2s = paragraphStart(t, hi)
        val a = p1s + sentenceStart(br.boundaries(t.substring(p1s, p1e)), lo - p1s, p1e - p1s)
        val b = p2s + sentenceEnd(br.boundaries(t.substring(p2s, p2e)), hi - p2s, p2e - p2s, hi > lo)
        return FocusRange(a, maxOf(a, b))
    }

    /** Largest boundary <= pos; at the paragraph end, the start of the LAST sentence (keeps it lit after ". "). */
    internal fun sentenceStart(
        b: IntArray,
        pos: Int,
        len: Int,
    ): Int {
        if (len == 0) return 0
        val p = if (pos >= len) len - 1 else pos
        var r = 0
        for (x in b) {
            if (x <= p) r = x else break
        }
        return r
    }

    /** A non-empty selection ending exactly on a boundary ends there; otherwise the next boundary > pos, else len. */
    internal fun sentenceEnd(
        b: IntArray,
        pos: Int,
        len: Int,
        nonEmpty: Boolean,
    ): Int {
        if (nonEmpty && b.contains(pos)) return pos
        for (x in b) if (x > pos) return x
        return len
    }
}

/** 02 §2: `alpha = (dim − text) / (bg − text)` on the grey channel; colour = [bg] with that alpha. */
fun focusOverlayArgb(
    bg: Int,
    text: Int,
    dim: Int,
): Int {
    val r = { c: Int -> (c shr 16) and 0xFF }
    val a = ((r(dim) - r(text)).toFloat() / (r(bg) - r(text))).coerceIn(0f, 1f)
    return ((a * 255f + 0.5f).toInt() shl 24) or (bg and 0x00FFFFFF)
}

/**
 * Draw-time-only dim overlay (hard rule 5: **no spans**). Draws the dimmed band as a set of plain fill
 * rectangles — one per (partly-)dimmed line, with the focused lines' lit portion left undrawn — rather than a
 * single big rect clipped by [Canvas.clipOutPath]: a hardware-accelerated `View` canvas does not reliably honour
 * `clipOutPath`/`clipPath` for the concave, multi-rectangle paths [Layout.getSelectionPath] returns for a
 * multi-line range (confirmed on-device: the clip silently had no effect at all, dimming the whole band including
 * the "lit" text — a plain `Canvas(bitmap)` in a unit/instrumented test does not reproduce this, since that path
 * uses Skia's software rasterizer, not the View hardware-accelerated one). Rectangles have no such caveat on any
 * canvas. Recomputed on every draw — cheap: at most a few dozen visible lines.
 */
internal class FocusOverlay {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** The band drawn on the last [draw] call (EditText-local y, i.e. NOT scroll-adjusted) — [MarkdownEditText
     * .onViewportChanged] invalidates only once the live viewport has left this band. */
    var drawnTop = 0
        private set
    var drawnBottom = 0
        private set

    fun setColor(argb: Int) {
        paint.color = argb
    }

    fun draw(
        canvas: Canvas,
        view: TextView,
        layout: Layout,
        focus: FocusRange,
        caretOffset: Int,
        top: Int,
        bottom: Int,
    ) {
        val padLeft = view.totalPaddingLeft.toFloat()
        val padTop = view.totalPaddingTop
        val fullWidth = layout.width.toFloat()
        val topLocal = (top - padTop).coerceAtLeast(0)
        val bottomLocal = (bottom - padTop).coerceAtMost(layout.height)
        if (bottomLocal > topLocal) {
            val firstLine = layout.getLineForVertical(topLocal)
            val lastLine = layout.getLineForVertical((bottomLocal - 1).coerceAtLeast(0))
            val hasFocus = focus.end > focus.start
            val focusStartLine = if (hasFocus) layout.getLineForOffset(focus.start) else -1
            val focusEndLine = if (hasFocus) layout.getLineForOffset(focus.end) else -1
            val caretLine = if (caretOffset >= 0) layout.getLineForOffset(caretOffset) else -1
            val caretHalf = CARET_HALF_WIDTH_DP * view.resources.displayMetrics.density
            canvas.withSave {
                translate(padLeft, padTop.toFloat())
                for (line in firstLine..lastLine) {
                    val lineTop = maxOf(layout.getLineTop(line), topLocal).toFloat()
                    val lineBottom = minOf(layout.getLineBottom(line, false), bottomLocal).toFloat()
                    if (lineBottom <= lineTop) continue
                    val lit = ArrayList<Pair<Float, Float>>(2)
                    if (hasFocus && line in focusStartLine..focusEndLine) {
                        val xs = if (line == focusStartLine) layout.getPrimaryHorizontal(focus.start) else 0f
                        val xe = if (line == focusEndLine) layout.getPrimaryHorizontal(focus.end) else fullWidth
                        lit += xs to xe
                    }
                    if (line == caretLine) {
                        val cx = layout.getPrimaryHorizontal(caretOffset)
                        lit += (cx - caretHalf) to (cx + caretHalf)
                    }
                    drawDimmedGaps(lit, fullWidth, lineTop, lineBottom, paint)
                }
            }
        }
        drawnTop = top
        drawnBottom = bottom
    }

    /** Draws [paint] over every part of `[0, fullWidth) x [lineTop, lineBottom)` NOT covered by [lit] (merged,
     * sorted first). No [android.graphics.Path]/clip involved — see the class KDoc for why. */
    private fun Canvas.drawDimmedGaps(
        lit: List<Pair<Float, Float>>,
        fullWidth: Float,
        lineTop: Float,
        lineBottom: Float,
        paint: Paint,
    ) {
        if (lit.isEmpty()) {
            drawRect(0f, lineTop, fullWidth, lineBottom, paint)
            return
        }
        val sorted = lit.sortedBy { it.first }
        var cursor = 0f
        for ((s, e) in sorted) {
            val start = s.coerceIn(0f, fullWidth)
            val end = e.coerceIn(0f, fullWidth)
            if (start > cursor) drawRect(cursor, lineTop, start, lineBottom, paint)
            if (end > cursor) cursor = end
        }
        if (cursor < fullWidth) drawRect(cursor, lineTop, fullWidth, lineBottom, paint)
    }

    private companion object {
        const val CARET_HALF_WIDTH_DP = 2f
    }
}
