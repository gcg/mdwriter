package dev.mdwriter.editor

import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow

/** One [FindSession] snapshot: total match [count], the focused match's index (`-1` = none), and whether [count]
 * was truncated at [TextSearch.MAX_MATCHES]. */
data class FindResult(
    val count: Int,
    val index: Int,
    val truncated: Boolean,
) {
    companion object {
        val NONE = FindResult(0, -1, false)
    }
}

/**
 * Owns the live find state and hands it to [et] through the platform TextView search-result-highlight API (01
 * §6.2/§10 hard rules 5/6 — these are TextView-native highlights, **not** spans; a guard-grep for the span-mutation
 * calls this file must never use finds nothing here, T17 Acceptance 8).
 *
 * The platform does not shift [android.widget.TextView.setSearchResultHighlights] ranges on a text edit, so a
 * [TextWatcher] is attached (only while there is at least one range) and calls [TextSearch.shift] on every single
 * text change — typing, undo, a replace, or an edit from any other source — keeping the ranges valid before the
 * (debounced) re-search from [EditorController.find] ever arrives. An out-of-range offset would otherwise crash at
 * draw time.
 */
internal class FindSession(
    private val et: MarkdownEditText,
) {
    val result = MutableStateFlow(FindResult.NONE)

    var query: String = ""
        private set
    var matchCase: Boolean = false
        private set

    private var ranges = IntArray(0)
    private var focused = -1
    private var truncated = false
    private var watching = false

    // Captured by onTextChanged, consumed by afterTextChanged — same pattern as MdUndoManager's own watcher.
    private var cs = 0
    private var cRem = 0
    private var cAdd = 0

    private val watcher =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
                cs = start
                cRem = before
                cAdd = count
            }

            override fun afterTextChanged(s: Editable?) {
                if (ranges.isEmpty()) return
                val old = focusedStart()
                ranges = TextSearch.shift(ranges, cs, cRem, cAdd)
                val mapped =
                    when {
                        old < 0 -> 0
                        old >= cs + cRem -> old + cAdd - cRem
                        old < cs -> old
                        else -> cs
                    }
                focused = TextSearch.indexAtOrAfter(ranges, mapped)
                push(reveal = false)
            }
        }

    fun focusedStart(): Int = if (focused >= 0) ranges[2 * focused] else -1

    fun focusedRange(): IntRange? = if (focused >= 0) ranges[2 * focused] until ranges[2 * focused + 1] else null

    /** Installs a brand-new result set (a fresh search or a re-search after the query/case changed). */
    fun set(
        q: String,
        mc: Boolean,
        r: IntArray,
        idx: Int,
        trunc: Boolean,
        reveal: Boolean,
    ) {
        query = q
        matchCase = mc
        ranges = r
        focused = idx
        truncated = trunc
        if (!watching) {
            et.addTextChangedListener(watcher)
            watching = true
        }
        push(reveal)
    }

    /** [findNext]/[findPrevious]: steps the focused index, wrapping. */
    fun step(d: Int) {
        val n = ranges.size / 2
        if (n == 0) return
        focused = ((focused + d) % n + n) % n
        push(reveal = true)
    }

    /** After a single replace at `[s, s + replLen)`: drop the range we just wrote over (never re-target the text
     * we just replaced — the minimized edit is often an insert exactly at the old match's end, which [TextSearch.shift]
     * alone would leave pointing at the still-live match; see the task's own Pitfalls). */
    fun afterReplace(
        s: Int,
        replLen: Int,
    ) {
        ranges = TextSearch.dropRange(ranges, s, s + replLen)
        focused = TextSearch.indexAtOrAfter(ranges, s + replLen)
        push(reveal = true)
    }

    private fun push(reveal: Boolean) {
        val w = TextSearch.window(ranges, focused)
        et.setSearchResultHighlights(*w.ranges)
        et.setFocusedSearchResultIndex(if (w.focused >= 0) w.focused else TextView.FOCUSED_SEARCH_RESULT_INDEX_NONE)
        // 2-arg overload: the EditText itself is unfocused while the find field has focus (task Pitfalls/§D).
        if (reveal && focused >= 0) et.bringPointIntoView(ranges[2 * focused], true)
        result.value = FindResult(ranges.size / 2, focused, truncated)
    }

    /** Clears the session and detaches the watcher — called by [EditorController.install] and [EditorController.clearFind]. */
    fun clear() {
        if (watching) {
            et.removeTextChangedListener(watcher)
            watching = false
        }
        ranges = IntArray(0)
        focused = -1
        query = ""
        et.setSearchResultHighlights(*IntArray(0))
        et.setFocusedSearchResultIndex(TextView.FOCUSED_SEARCH_RESULT_INDEX_NONE)
        result.value = FindResult.NONE
    }
}
