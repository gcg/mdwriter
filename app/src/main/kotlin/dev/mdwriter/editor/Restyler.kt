package dev.mdwriter.editor

import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.view.Choreographer
import dev.mdwriter.editor.spans.EditorStyle
import dev.mdwriter.editor.spans.MdStyleSpan
import dev.mdwriter.editor.spans.PaintTextMeasurer
import dev.mdwriter.editor.spans.SpanFactory
import dev.mdwriter.editor.spans.SpanMaterializer
import dev.mdwriter.editor.spans.SpanSpec
import dev.mdwriter.markdown.MarkdownHighlighter
import dev.mdwriter.util.PerfLog
import dev.mdwriter.util.PerfStats
import java.util.Locale

/**
 * Live restyle: a [TextWatcher] marks lines dirty and schedules (at most) one [Choreographer] frame callback per
 * frame; the callback runs in `CALLBACK_ANIMATION`, before this frame's own `CALLBACK_TRAVERSAL` (layout/draw),
 * so the reconciled spans are what gets drawn in the SAME frame the edit happened in (01 §7, factcheck C2). The
 * reconcile diffs the wanted [SpanSpec]s (from [SpanFactory]) for the dirty lines against the existing
 * [MdStyleSpan]s and patches only the difference, inside a [BUDGET_NS] budget per frame, chunking a huge dirty
 * range across several frames (visible lines first, so a cascade never blanks the screen the user is looking at).
 *
 * Rule 3 / A20: NEVER wraps the reconcile in a begin/end batch edit — ending one calls `bringPointIntoView` and
 * would yank the viewport back to the caret on every chunk of a cascade. Rule 5: only
 * ever touches spans that implement [MdStyleSpan] — composing spans, `SuggestionSpan`, `SpellCheckSpan`,
 * selection and [dev.mdwriter.editor.spans.HangRoomSpan] are never queried or removed, since `getSpans(...,
 * MdStyleSpan::class.java)` simply does not see them. No span is ever changed inside [onTextChanged] — the text
 * is still mid-`replace()` there; spans are only ever changed from [doFrame].
 */
internal class Restyler(
    private val editText: MarkdownEditText,
    private val scrollView: EditorScrollView,
    style: EditorStyle,
    private val onTextChange: () -> Unit,
) : TextWatcher,
    Choreographer.FrameCallback {
    /** True until the first [reset] (i.e. the first successful [EditorController.install]), and during a later one. */
    var suspended = true

    /** Set by the controller right after `setText`; main-thread-only from then on (01 §6.1). */
    var highlighter: MarkdownHighlighter? = null

    /** Invoked after a frame that changed at least one span (T15's focus overlay refresh hooks this). */
    var onRestyled: (() -> Unit)? = null

    private val updateStats = PerfStats(24)
    private val dirty = DirtyRange()
    private var scheduled = false
    private var visibleDone = false
    private var hlLength = 0
    private var depth = 0

    private val factory = SpanFactory(PaintTextMeasurer(style))
    private val mat = SpanMaterializer(style)
    private val specs = ArrayList<SpanSpec>()
    private val want = HashMap<Key, SpanSpec>()

    private data class Key(
        val kind: Int,
        val arg: Int,
        val start: Int,
        val end: Int,
    )

    /** Called by the controller right after `setText` installs [length] chars of a (freshly `fullScan`ned) document. */
    fun reset(length: Int) {
        hlLength = length
        dirty.clear()
        visibleDone = false
    }

    /** Style/geometry/highlight-setting change: every span's colours/widths may have changed. */
    fun markAllDirty() {
        dirty.markAll()
        visibleDone = false
        schedule()
    }

    val isIdle: Boolean get() = dirty.isEmpty && !scheduled

    override fun beforeTextChanged(
        s: CharSequence,
        start: Int,
        count: Int,
        after: Int,
    ) {
        depth++
    }

    override fun onTextChanged(
        s: CharSequence,
        start: Int,
        before: Int,
        count: Int,
    ) {
        if (suspended) return
        val hl = highlighter ?: return
        // depth should always be 1 (a single, non-reentrant replace()) and lengths should always be consistent
        // for it; the diffing update(text) overload is a defensive fallback only, never the expected path.
        val consistent = depth == 1 && hlLength - before + count == s.length
        val t0 = if (PerfLog.enabled) System.nanoTime() else 0L
        val delta = if (consistent) hl.update(s, start, before, count) else hl.update(s)
        if (PerfLog.enabled && updateStats.add(System.nanoTime() - t0)) {
            android.util.Log.i(
                PerfLog.TAG,
                String.format(
                    Locale.ROOT,
                    "hl.update n=%d p50=%.3f p95=%.3f max=%.3f",
                    updateStats.count,
                    updateStats.percentileMs(0.5),
                    updateStats.percentileMs(0.95),
                    updateStats.percentileMs(1.0),
                ),
            )
            updateStats.reset()
        }
        hlLength = s.length
        dirty.onEdit(start, before, count, s.length)
        if (delta.full) dirty.markAll() else dirty.add(delta.startOffset, delta.endOffset)
        visibleDone = false
        onTextChange() // version++ / EditEvent — NO span changes here (rule 3 pitfall)
        schedule()
    }

    override fun afterTextChanged(s: Editable) {
        depth--
    }

    private fun schedule() {
        if (!scheduled) {
            scheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        val hl = highlighter ?: return
        val e = editText.text ?: return
        if (dirty.isEmpty) return
        val deadline = System.nanoTime() + BUDGET_NS
        var from = if (dirty.full) 0 else hl.lineIndexOf(dirty.start)
        val endLine = if (dirty.full) hl.lineCount else hl.lineIndexOf(dirty.end) + 1

        // Cascades (e.g. a HighlightDelta.full from a link-definition change) can dirty far more lines than fit
        // in one frame's budget: reconcile the visible lines (plus a small margin) first, so the user never sees
        // stale styling on screen while the rest catches up over the following frames.
        if (!visibleDone && endLine - from > CHUNK_LINES) {
            val vis = visibleLogicalLines(hl)
            val a = maxOf(from, vis.first - VISIBLE_MARGIN_LINES)
            val b = minOf(endLine, vis.last + VISIBLE_MARGIN_LINES + 1)
            var l = a
            while (l < b) {
                val c = minOf(b, l + CHUNK_LINES)
                reconcile(e, hl, l, c)
                l = c
            }
            visibleDone = true
        }

        if (dirty.full) {
            // From here on, consume the dirty range from the front as chunks complete (a `full` range has no
            // useful start/end of its own to trim).
            dirty.clear()
            dirty.add(0, e.length)
        }
        while (from < endLine) {
            val to = minOf(endLine, from + CHUNK_LINES)
            reconcile(e, hl, from, to)
            dirty.trimStart(paraEnd(hl, e, to - 1))
            from = to
            if (System.nanoTime() > deadline) break
        }
        if (!dirty.isEmpty) schedule()
        onRestyled?.invoke()
    }

    /** Layout (visual) rows -> highlighter (logical) lines. */
    private fun visibleLogicalLines(hl: MarkdownHighlighter): IntRange {
        val layout = editText.layout ?: return 0..0
        val r = scrollView.visibleLineRange()
        if (r.isEmpty()) return 0..0
        val firstOffset = layout.getLineStart(r.first)
        val lastOffset = layout.getLineEnd(r.last).coerceAtMost(editText.length())
        return hl.lineIndexOf(firstOffset)..hl.lineIndexOf(lastOffset)
    }

    private fun paraEnd(
        hl: MarkdownHighlighter,
        e: CharSequence,
        line: Int,
    ): Int = minOf(hl.lineEnd(line) + 1, e.length)

    /**
     * Diffs existing [MdStyleSpan]s against [SpanFactory] output over `[fromLine, endLine)`, first widened until
     * stable to any existing span that sticks out past the requested lines. Stale spans can stick out after a
     * `'\n'` insert splits a previously one-line span's line in two — the pre-edit span object is still attached
     * at its old bounds until this reconcile removes it, so the widening loop must include it in the query.
     */
    fun reconcile(
        e: Editable,
        hl: MarkdownHighlighter,
        fromLine: Int,
        endLine: Int,
    ) {
        var l0 = fromLine
        var l1 = endLine
        var changed = true
        var guard = 0
        while (changed && guard < 3) {
            guard++
            var a = hl.lineStart(l0)
            var b = paraEnd(hl, e, l1 - 1)
            for (sp in e.getSpans(a, b, MdStyleSpan::class.java)) {
                a = minOf(a, e.getSpanStart(sp))
                b = maxOf(b, e.getSpanEnd(sp))
            }
            val n0 = hl.lineIndexOf(a)
            val n1 = hl.lineIndexOf(maxOf(a, b - 1)) + 1
            changed = n0 != l0 || n1 != l1
            l0 = minOf(l0, n0)
            l1 = maxOf(l1, n1)
        }

        specs.clear()
        want.clear()
        factory.specsForLines(e, hl, l0, l1, specs)
        for (s in specs) want.putIfAbsent(Key(s.kind, s.arg, s.start, s.end), s)

        val rangeStart = hl.lineStart(l0)
        val rangeEnd = paraEnd(hl, e, l1 - 1)
        for (sp in e.getSpans(rangeStart, rangeEnd, MdStyleSpan::class.java)) { // rule 5: ONLY our own spans
            val key = Key(sp.kind, sp.arg, e.getSpanStart(sp), e.getSpanEnd(sp))
            if (want.remove(key) == null) e.removeSpan(sp)
        }
        for ((k, s) in want) e.setSpan(mat.create(s), k.start, k.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        // NO begin/end batch edit here (rule 3, A20: ending one -> bringPointIntoView yanks the viewport).
    }

    companion object {
        const val BUDGET_NS = 4_000_000L
        const val CHUNK_LINES = 256
        private const val VISIBLE_MARGIN_LINES = 20
    }
}
