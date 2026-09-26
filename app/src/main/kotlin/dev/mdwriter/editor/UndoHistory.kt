package dev.mdwriter.editor

/**
 * Pure, platform-free word-grouped undo/redo history (no `android.*` import). Each undo [Step] is one or more
 * [Op]s (several only inside a [beginGroup]/[endGroup] pair — a toolbar/smart edit is exactly one undo step even
 * when it touches text in more than one place). Adjacent single-char edits merge into one step while they look
 * like "the same typing run": a run of inserted chars, a run of backspaced chars, a run of forward-deleted chars,
 * or an IME composing/autocorrect rewrite — but a new run starts a new step the moment a word boundary is
 * crossed (so "hello world" is 2 undo steps, not 1), a caret jump happens (any [hardBreak]), or the merge window
 * elapses. Both selection ends (not just a collapsed caret) are restored on undo/redo. The history is capped by
 * step count and by an approximate total character budget so a huge paste session cannot grow it unbounded.
 */
internal class UndoHistory(
    private val maxSteps: Int = 1_000,
    private val maxChars: Int = 1_000_000,
    private val windowMs: Long = 1_500,
) {
    class Op(
        var start: Int,
        var old: String,
        var new: String,
    )

    class Step(
        val ops: MutableList<Op>,
        val beforeStart: Int,
        val beforeEnd: Int,
        var afterStart: Int,
        var afterEnd: Int,
        var time: Long,
    ) {
        fun revert(r: (Int, Int, String) -> Unit) {
            for (op in ops.asReversed()) r(op.start, op.start + op.new.length, op.old)
        }

        fun reapply(r: (Int, Int, String) -> Unit) {
            for (op in ops) r(op.start, op.start + op.old.length, op.new)
        }

        val chars: Int get() = ops.sumOf { it.old.length + it.new.length }
    }

    private val undo = ArrayDeque<Step>()
    private val redo = ArrayDeque<Step>()
    private var breakNext = true
    private var depth = 0
    private var groupStep: Step? = null
    private var gStart = 0
    private var gEnd = 0
    private var approxChars = 0

    /** Caret after the last recorded edit — used by the caller to detect an out-of-band caret jump. */
    var expectedCaret = -1
        private set

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    val size get() = undo.size

    /** Forces the next edit to start a new step (a caret jump, a focus change, a pending clipboard action, …). */
    fun hardBreak() {
        breakNext = true
    }

    fun beginGroup(
        s: Int,
        e: Int,
    ) {
        if (depth++ == 0) {
            groupStep = null
            gStart = s
            gEnd = e
            breakNext = true
        }
    }

    fun endGroup(
        s: Int,
        e: Int,
    ) {
        check(depth > 0)
        if (--depth == 0) {
            groupStep?.let {
                it.afterStart = s
                it.afterEnd = e
            }
            groupStep = null
            breakNext = true
        }
    }

    fun record(
        start: Int,
        old: String,
        new: String,
        selStart: Int,
        selEnd: Int,
        now: Long,
    ) {
        if (old == new) return
        redo.clear()
        approxChars += old.length + new.length
        val caret = start + new.length
        if (depth > 0) {
            val g =
                groupStep ?: Step(mutableListOf(), gStart, gEnd, caret, caret, now).also {
                    undo.addLast(it)
                    groupStep = it
                }
            g.ops += Op(start, old, new)
            g.time = now
        } else {
            val top = undo.lastOrNull()
            val op = top?.ops?.singleOrNull()
            if (!breakNext && op != null && now - top.time <= windowMs && tryMerge(op, start, old, new)) {
                top.afterStart = caret
                top.afterEnd = caret
                top.time = now
                if (op.old.isEmpty() && op.new.isEmpty()) undo.removeLast() // typed, then fully backspaced
            } else {
                undo.addLast(Step(mutableListOf(Op(start, old, new)), selStart, selEnd, caret, caret, now))
            }
            breakNext = false
        }
        expectedCaret = caret
        trim()
    }

    private fun tryMerge(
        op: Op,
        start: Int,
        old: String,
        new: String,
    ): Boolean {
        val opEnd = op.start + op.new.length
        return when {
            old.isEmpty() && op.new.isNotEmpty() && start == opEnd -> {
                if (startsNewWord(op.new.last(), new)) {
                    false
                } else {
                    op.new += new
                    true
                }
            }

            new.isEmpty() && start + old.length == opEnd && op.new.endsWith(old) -> {
                op.new = op.new.dropLast(old.length)
                true
            }

            new.isEmpty() && op.new.isEmpty() && start + old.length == op.start -> {
                op.start = start
                op.old = old + op.old
                true
            }

            new.isEmpty() && op.new.isEmpty() && start == op.start -> { // forward delete
                op.old += old
                true
            }

            old.isNotEmpty() && start >= op.start && start + old.length <= opEnd &&
                op.new.regionMatches(start - op.start, old, 0, old.length) -> { // composing/autocorrect rewrite
                val r = start - op.start
                op.new = op.new.substring(0, r) + new + op.new.substring(r + old.length)
                true
            }

            else -> {
                false
            }
        }
    }

    private fun startsNewWord(
        prev: Char,
        new: String,
    ): Boolean {
        var p = prev
        for (c in new) {
            if (p.isWhitespace() && !c.isWhitespace()) return true
            p = c
        }
        return false
    }

    private fun trim() {
        if (undo.size <= maxSteps && approxChars <= maxChars) return
        approxChars = undo.sumOf { it.chars }
        while (undo.size > 1 && (undo.size > maxSteps || approxChars > maxChars)) {
            approxChars -= undo.removeFirst().chars
        }
    }

    fun popUndo(): Step? =
        undo.removeLastOrNull()?.also {
            redo.addLast(it)
            breakNext = true
        }

    fun popRedo(): Step? =
        redo.removeLastOrNull()?.also {
            undo.addLast(it)
            breakNext = true
        }

    fun clear() {
        undo.clear()
        redo.clear()
        approxChars = 0
        breakNext = true
        expectedCaret = -1
    }
}
