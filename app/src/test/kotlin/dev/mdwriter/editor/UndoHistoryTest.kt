package dev.mdwriter.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-JVM test for [UndoHistory] (Acceptance 1). A tiny [Doc] fixture applies [UndoHistory.Step]s to a real
 * [StringBuilder] so undo/redo assertions check the actual resulting text and selection, not just step counts.
 */
class UndoHistoryTest {
    /** Drives a [UndoHistory] against a plain text buffer, exactly like [MdUndoManager] drives it against an
     * `Editable`, but synchronously and without any Android type. */
    private class Doc(
        maxSteps: Int = 1_000,
        maxChars: Int = 1_000_000,
        windowMs: Long = 1_500,
    ) {
        val history = UndoHistory(maxSteps, maxChars, windowMs)
        val text = StringBuilder()
        var selStart = 0
        var selEnd = 0

        /** One `Editable.replace(start, end, replacement)`-shaped edit, recorded as a single [UndoHistory.Op]. */
        fun edit(
            start: Int,
            end: Int,
            replacement: String,
            now: Long,
        ) {
            val old = text.substring(start, end)
            val beforeSelStart = selStart
            val beforeSelEnd = selEnd
            text.replace(start, end, replacement)
            selStart = start + replacement.length
            selEnd = selStart
            history.record(start, old, replacement, beforeSelStart, beforeSelEnd, now)
        }

        fun group(block: () -> Unit) {
            history.beginGroup(selStart, selEnd)
            block()
            history.endGroup(selStart, selEnd)
        }

        fun undo() {
            val step = history.popUndo() ?: return
            step.revert { a, b, t -> text.replace(a, b, t) }
            selStart = step.beforeStart
            selEnd = step.beforeEnd
        }

        fun redo() {
            val step = history.popRedo() ?: return
            step.reapply { a, b, t -> text.replace(a, b, t) }
            selStart = step.afterStart
            selEnd = step.afterEnd
        }
    }

    @Test
    fun typingRunIsOneStep() {
        val d = Doc()
        var t = 0L
        for ((i, c) in "hello".withIndex()) d.edit(i, i, c.toString(), t++)
        assertThat(d.text.toString()).isEqualTo("hello")
        assertThat(d.history.size).isEqualTo(1)
        d.undo()
        assertThat(d.text.toString()).isEqualTo("")
        assertThat(d.selStart).isEqualTo(0)
        assertThat(d.selEnd).isEqualTo(0)
    }

    @Test
    fun newWordStartsNewStep() {
        val d = Doc()
        var t = 0L
        for (c in "hello world") d.edit(d.text.length, d.text.length, c.toString(), t++)
        assertThat(d.text.toString()).isEqualTo("hello world")
        assertThat(d.history.size).isEqualTo(2)
        d.undo()
        assertThat(d.text.toString()).isEqualTo("hello ")
    }

    @Test
    fun glideSpacePlusWordIsNewStep() {
        val d = Doc()
        // First step: typed "a".
        d.edit(0, 0, "a", 0)
        assertThat(d.history.size).isEqualTo(1)
        // A single multi-char IME "glide" commit that itself starts a new word (" be"): must NOT merge into the
        // previous step, even though it arrives as one call.
        d.edit(1, 1, " be", 100)
        assertThat(d.history.size).isEqualTo(2)
        assertThat(d.text.toString()).isEqualTo("a be")
        d.undo()
        assertThat(d.text.toString()).isEqualTo("a")
    }

    @Test
    fun pauseOverWindowBreaks() {
        val d = Doc(windowMs = 1_500)
        d.edit(0, 0, "h", 0)
        d.edit(1, 1, "i", 1_501) // same "word", but the pause exceeds the merge window
        assertThat(d.history.size).isEqualTo(2)
        assertThat(d.text.toString()).isEqualTo("hi")
    }

    @Test
    fun backspaceInsideRunShrinks() {
        val d = Doc()
        var t = 0L
        for ((i, c) in "hello".withIndex()) d.edit(i, i, c.toString(), t++)
        assertThat(d.history.size).isEqualTo(1)
        // Backspace the trailing "o", then "l": both merge into the SAME step, shrinking its inserted text.
        d.edit(4, 5, "", t++)
        d.edit(3, 4, "", t++)
        assertThat(d.history.size).isEqualTo(1)
        assertThat(d.text.toString()).isEqualTo("hel")
        d.undo()
        assertThat(d.text.toString()).isEqualTo("")
    }

    @Test
    fun backspaceRunOverExistingTextIsOneStep() {
        val d = Doc()
        d.text.append("hello world") // pre-existing text, never itself recorded
        d.selStart = d.text.length
        d.selEnd = d.text.length
        var t = 0L
        // Backspace "world" one character at a time from the end.
        var pos = d.text.length
        while (pos > "hello ".length) {
            d.edit(pos - 1, pos, "", t++)
            pos--
        }
        assertThat(d.text.toString()).isEqualTo("hello ")
        assertThat(d.history.size).isEqualTo(1)
        d.undo()
        assertThat(d.text.toString()).isEqualTo("hello world")
    }

    @Test
    fun forwardDeleteRunIsOneStep() {
        val d = Doc()
        d.text.append("hello world")
        var t = 0L
        // Forward-delete at a fixed position: each delete removes the char now sitting at that offset.
        repeat(6) { d.edit(5, 6, "", t++) }
        assertThat(d.text.toString()).isEqualTo("hello")
        assertThat(d.history.size).isEqualTo(1)
        d.undo()
        assertThat(d.text.toString()).isEqualTo("hello world")
    }

    @Test
    fun composingRewriteMerges() {
        val d = Doc()
        var t = 0L
        for ((i, c) in "teh".withIndex()) d.edit(i, i, c.toString(), t++)
        assertThat(d.history.size).isEqualTo(1)
        // Autocorrect rewrites the composed word in place: still the same step.
        d.edit(0, 3, "the", t++)
        assertThat(d.history.size).isEqualTo(1)
        assertThat(d.text.toString()).isEqualTo("the")
        d.undo()
        assertThat(d.text.toString()).isEqualTo("")
    }

    @Test
    fun hardBreakPreventsMerge() {
        val d = Doc()
        d.edit(0, 0, "a", 0)
        d.history.hardBreak()
        d.edit(1, 1, "b", 0) // same instant, same "word" — would merge without the hard break
        assertThat(d.history.size).isEqualTo(2)
    }

    @Test
    fun groupIsOneStepWithSeveralOps() {
        val d = Doc()
        d.text.append("one\ntwo")
        d.group {
            d.edit(0, 0, "- ", 0)
            d.edit(4 + 2, 4 + 2, "- ", 0) // account for the shift from the first op
        }
        assertThat(d.text.toString()).isEqualTo("- one\n- two")
        assertThat(d.history.size).isEqualTo(1)
        d.undo()
        assertThat(d.text.toString()).isEqualTo("one\ntwo")
    }

    @Test
    fun newEditClearsRedo() {
        val d = Doc()
        d.edit(0, 0, "a", 0)
        d.undo()
        assertThat(d.history.canRedo).isTrue()
        d.history.hardBreak()
        d.edit(0, 0, "b", 100)
        assertThat(d.history.canRedo).isFalse()
    }

    @Test
    fun capAt1000Steps() {
        val d = Doc(maxSteps = 5)
        var t = 0L
        repeat(8) { i ->
            d.history.hardBreak() // each edit is its own step (never merges with the previous one)
            d.edit(d.text.length, d.text.length, ('a' + i).toString(), t++)
        }
        assertThat(d.history.size).isEqualTo(5)
    }

    @Test
    fun charBudgetTrims() {
        val d = Doc(maxChars = 3)
        var t = 0L
        repeat(8) { i ->
            d.history.hardBreak()
            d.edit(d.text.length, d.text.length, ('a' + i).toString(), t++)
        }
        // Each 1-char step costs 1 approx-char; once the running total exceeds the 3-char budget the oldest
        // steps are dropped until it fits again — the live text itself is untouched (trimming is undo-only).
        assertThat(d.history.size).isEqualTo(3)
        assertThat(d.text.toString()).isEqualTo("abcdefgh")
    }

    @Test
    fun restoresBothSelectionEnds() {
        val d = Doc()
        d.text.append("abcdef")
        d.selStart = 2
        d.selEnd = 5 // a real (non-collapsed) selection over "cde" before the edit
        d.edit(2, 5, "X", 0)
        assertThat(d.text.toString()).isEqualTo("abXf")
        d.undo()
        assertThat(d.text.toString()).isEqualTo("abcdef")
        assertThat(d.selStart).isEqualTo(2)
        assertThat(d.selEnd).isEqualTo(5)
    }
}
