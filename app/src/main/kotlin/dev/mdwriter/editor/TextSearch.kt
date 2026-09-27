package dev.mdwriter.editor

import dev.mdwriter.markdown.TextEdit

/**
 * Pure, platform-free find/replace engine (no `android.*` import, T17). Matches are non-overlapping, left to
 * right, kept as a flat `IntArray` of `[s0,e0,s1,e1,…]` pairs — the exact shape TextView's own
 * `setSearchResultHighlights(int...)` API takes (01 §6.2/§10 hard rules 5/6: these are TextView-native search
 * result highlights, never spans).
 *
 * The platform does **not** shift these ranges on a text edit — [FindSession]'s own [android.text.TextWatcher]
 * calls [shift] on every change (typing, undo, replace, or an edit from any other source) to keep them valid.
 */
object TextSearch {
    const val MAX_MATCHES = 100_000

    // Ranges handed to TextView (it builds a Path per range on edit — only ever HAND OVER a bounded window).
    const val HIGHLIGHT_WINDOW = 500

    private val EMPTY = IntArray(0)

    /**
     * Non-overlapping matches of [query] in [text], left to right, as `[s0,e0,s1,e1,…]`.
     * Case-insensitive comparison is per-character (`String.indexOf(ignoreCase = true)`) — no full Unicode case
     * folding (e.g. German "ß" never matches "SS"), a documented limitation.
     */
    fun findAll(
        text: String,
        query: String,
        matchCase: Boolean,
        limit: Int = MAX_MATCHES,
    ): IntArray {
        if (query.isEmpty() || query.length > text.length) return EMPTY
        var out = IntArray(64)
        var n = 0
        var from = 0
        while (n / 2 < limit) {
            val i = text.indexOf(query, from, ignoreCase = !matchCase)
            if (i < 0) break
            if (n + 2 > out.size) out = out.copyOf(out.size * 2)
            out[n++] = i
            out[n++] = i + query.length
            from = i + query.length
        }
        return out.copyOf(n)
    }

    /** Index of the first match whose start is `>= offset`; wraps to `0` when none is; `-1` when [r] is empty. */
    fun indexAtOrAfter(
        r: IntArray,
        offset: Int,
    ): Int {
        if (r.isEmpty()) return -1
        var i = 0
        while (i < r.size && r[i] < offset) i += 2
        return if (i >= r.size) 0 else i / 2
    }

    /**
     * Re-maps [r] after replacing `[start, start+removed)` with `added` chars: matches strictly before the edit
     * are kept, matches strictly after are shifted by `added - removed`, and any match intersecting the edited
     * range is dropped (its offsets are no longer meaningful).
     */
    fun shift(
        r: IntArray,
        start: Int,
        removed: Int,
        added: Int,
    ): IntArray {
        val d = added - removed
        val end = start + removed
        val out = IntArray(r.size)
        var n = 0
        var i = 0
        while (i < r.size) {
            val s = r[i]
            val e = r[i + 1]
            when {
                e <= start -> {
                    out[n++] = s
                    out[n++] = e
                }

                s >= end -> {
                    out[n++] = s + d
                    out[n++] = e + d
                }
            }
            i += 2
        }
        return out.copyOf(n)
    }

    /** Drops every match intersecting `[from, to)`. */
    fun dropRange(
        r: IntArray,
        from: Int,
        to: Int,
    ): IntArray {
        val out = IntArray(r.size)
        var n = 0
        var i = 0
        while (i < r.size) {
            if (r[i + 1] <= from || r[i] >= to) {
                out[n++] = r[i]
                out[n++] = r[i + 1]
            }
            i += 2
        }
        return out.copyOf(n)
    }

    /** The bounded slice of [r] actually handed to TextView (see [HIGHLIGHT_WINDOW]), centred on [focused] where
     * possible; [first] is the pair-index [ranges] starts at within [r] (so callers can re-map a TextView-local
     * focused index back to a full-list index). */
    data class HighlightWindow(
        val ranges: IntArray,
        val focused: Int,
        val first: Int,
    )

    fun window(
        r: IntArray,
        focused: Int,
        max: Int = HIGHLIGHT_WINDOW,
    ): HighlightWindow {
        val n = r.size / 2
        if (n <= max) return HighlightWindow(r, focused, 0)
        val first = (focused.coerceAtLeast(0) - max / 2).coerceIn(0, n - max)
        return HighlightWindow(
            r.copyOfRange(2 * first, 2 * (first + max)),
            if (focused < 0) -1 else focused - first,
            first,
        )
    }

    /** Replaces match [index] alone with [repl] — caret lands right after the replacement. */
    fun replaceOne(
        r: IntArray,
        index: Int,
        repl: String,
    ): TextEdit {
        val s = r[2 * index]
        return TextEdit(s, r[2 * index + 1], repl, s + repl.length, s + repl.length)
    }

    /**
     * ONE [TextEdit] covering the first match's start to the last match's end, with every match in between also
     * replaced by [repl] — keeps the undo step and the restyle range small (never a whole-document replace, never
     * N separate edits). Returns `null` when [r] is empty. [caret] (an absolute offset in [text] before the edit)
     * is re-mapped by the accumulated length delta; a caret that sits inside a replaced match lands at the end of
     * that match's own replacement.
     */
    fun replaceAll(
        text: String,
        r: IntArray,
        repl: String,
        caret: Int,
    ): TextEdit? {
        if (r.isEmpty()) return null
        val a = r[0]
        val b = r[r.size - 1]
        val sb = StringBuilder(b - a + (r.size / 2) * repl.length)
        var pos = a
        var delta = 0
        var mapped = -1
        var i = 0
        while (i < r.size) {
            val s = r[i]
            val e = r[i + 1]
            sb.append(text, pos, s).append(repl)
            pos = e
            if (mapped < 0) {
                if (e <= caret) {
                    delta += repl.length - (e - s)
                } else if (s < caret) {
                    mapped = s + delta + repl.length
                }
            }
            i += 2
        }
        val c = if (mapped >= 0) mapped else caret + delta
        return TextEdit(a, b, sb.toString(), c, c)
    }
}
