package dev.mdwriter.editor

/**
 * A single half-open interval `[start, end)` of CURRENT-text offsets: every line/offset range that needs a
 * restyle reconcile before the next [Restyler] frame runs. Several edits may arrive before that frame runs, each
 * one shifting or widening the interval — see `plans/tasks/T07-incremental-restyle.md` Reference §A.
 *
 * Pure Kotlin, no `android.*` import (JVM-testable). [full] means "the whole document is dirty" (e.g. a
 * `HighlightDelta.full` cascade); [add] and [onEdit] become no-ops once [full] is set, since nothing can widen
 * past the whole document. [isEmpty] is true only right after [clear], or once [trimStart] has consumed the
 * whole range.
 */
class DirtyRange {
    var start: Int = 0
        private set

    var end: Int = 0 // exclusive
        private set

    var full: Boolean = false
        private set

    var isEmpty: Boolean = true
        private set

    fun clear() {
        isEmpty = true
        full = false
        start = 0
        end = 0
    }

    fun markAll() {
        full = true
        isEmpty = false
    }

    /** Union the range with `[s, e)` (NEW-text offsets). `e == s` is a valid, zero-width union point. */
    fun add(
        s: Int,
        e: Int,
    ) {
        if (full) return
        if (isEmpty) {
            start = s
            end = maxOf(s, e)
            isEmpty = false
        } else {
            start = minOf(start, s)
            end = maxOf(end, e)
        }
    }

    /**
     * Text `[at, at+removed)` was replaced by [added] chars; [newLength] is the text length AFTER the edit. Call
     * this BEFORE the [add] of the edit's own (highlighter) dirty range — see the class KDoc.
     *
     * An edit exactly at [start] with `removed == 0` shifts the whole range right (`oldEditEnd <= start`); that
     * is correct, since the edit's own highlighter delta is [add]ed right after this call.
     */
    fun onEdit(
        at: Int,
        removed: Int,
        added: Int,
        newLength: Int,
    ) {
        if (isEmpty || full) return
        val d = added - removed
        val oldEditEnd = at + removed
        // Edit entirely before (or touching) the current start: just shift it. Otherwise the edit overlaps or
        // reaches into the range from before its start: the new start is wherever the edit began.
        val ns = if (oldEditEnd <= start) start + d else minOf(start, at)
        val ne =
            when {
                end <= at -> end

                // edit entirely after the range (or touching its end): unchanged
                end >= oldEditEnd -> end + d

                // range end survives past the edit: shift it
                else -> at + added // range end was inside the removed text: collapse to the edit's new end
            }
        start = ns.coerceIn(0, newLength)
        end = ne.coerceIn(start, newLength)
    }

    /** Consume `[start, newStart)` after it has been reconciled. Empties the range once `newStart >= end`. */
    fun trimStart(newStart: Int) {
        if (full) return
        if (newStart >= end) clear() else start = maxOf(start, newStart)
    }
}
