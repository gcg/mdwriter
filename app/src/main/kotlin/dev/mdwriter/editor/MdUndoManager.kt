package dev.mdwriter.editor

import android.os.SystemClock
import android.text.Editable
import android.text.Selection
import android.text.TextUtils
import android.text.TextWatcher
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * `TextWatcher` adapter: wraps a pure [UndoHistory] around a live `EditText` (platform undo is disabled, see
 * `editor_styles.xml`'s `android:allowUndo=false` — this class is the app's own undo/redo). Registered on the
 * `EditText` AFTER the [Restyler] ([EditorController]'s `init`), so restyling always reacts to the same
 * `replace()` calls this class observes.
 */
internal class MdUndoManager(
    private val view: EditText,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) : TextWatcher {
    private val history = UndoHistory()

    /** Fired after anything that could change [canUndo]/[canRedo] (a recorded edit, [clear], [undo]/[redo]). */
    var onChanged: (() -> Unit)? = null

    private var applying = false
    private var inChange = false
    private var pendingOld: String? = null
    private var pendingSelStart = 0
    private var pendingSelEnd = 0

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    fun hardBreak() = history.hardBreak()

    fun clear() {
        history.clear()
        onChanged?.invoke()
    }

    /** Runs [block] without recording any of the text changes it makes (document install/reload). */
    fun <T> ignoring(block: () -> T): T {
        val was = applying
        applying = true
        try {
            return block()
        } finally {
            applying = was
        }
    }

    /** Runs [block] (one or more `Editable.replace` calls) as ONE undo step. Not `inline`: a public inline
     * function cannot reach a private member ([history]) of its own class from outside it. */
    fun <T> group(block: () -> T): T {
        val ed = view.text
        val s = Selection.getSelectionStart(ed).coerceAtLeast(0)
        val e = Selection.getSelectionEnd(ed).coerceAtLeast(0)
        history.beginGroup(minOf(s, e), maxOf(s, e))
        try {
            return block()
        } finally {
            val ed2 = view.text
            val ns = Selection.getSelectionStart(ed2).coerceAtLeast(0)
            val ne = Selection.getSelectionEnd(ed2).coerceAtLeast(0)
            history.endGroup(minOf(ns, ne), maxOf(ns, ne))
            onChanged?.invoke()
        }
    }

    override fun beforeTextChanged(
        s: CharSequence,
        start: Int,
        count: Int,
        after: Int,
    ) {
        inChange = true
        if (!applying) {
            pendingOld = TextUtils.substring(s, start, start + count) // never subSequence: this must survive the edit
            pendingSelStart = Selection.getSelectionStart(s).coerceAtLeast(0)
            pendingSelEnd = Selection.getSelectionEnd(s).coerceAtLeast(0)
        }
    }

    override fun onTextChanged(
        s: CharSequence,
        start: Int,
        before: Int,
        count: Int,
    ) {
        if (!applying) {
            val old = pendingOld ?: ""
            val new = TextUtils.substring(s, start, start + count)
            history.record(start, old, new, pendingSelStart, pendingSelEnd, clock())
            onChanged?.invoke()
        }
    }

    override fun afterTextChanged(s: Editable) {
        inChange = false
    }

    /** A caret jump (not a selection made by an edit we just recorded) breaks the current merge run. */
    fun onSelectionChanged(
        start: Int,
        end: Int,
    ) {
        if (!inChange && !applying && (start != end || start != history.expectedCaret)) history.hardBreak()
    }

    fun undo() = replay(history.popUndo(), forward = false)

    fun redo() = replay(history.popRedo(), forward = true)

    private fun replay(
        step: UndoHistory.Step?,
        forward: Boolean,
    ) {
        step ?: return
        val ed = view.text ?: return
        val wasComposing = BaseInputConnection.getComposingSpanStart(ed) != -1
        BaseInputConnection.removeComposingSpans(ed)
        applying = true
        view.beginBatchEdit()
        try {
            if (forward) {
                step.reapply {
                    a,
                    b,
                    t,
                    ->
                    ed.replace(a, b, t)
                }
            } else {
                step.revert { a, b, t -> ed.replace(a, b, t) }
            }
            val a = if (forward) step.afterStart else step.beforeStart
            val b = if (forward) step.afterEnd else step.beforeEnd
            Selection.setSelection(ed, a.coerceIn(0, ed.length), b.coerceIn(0, ed.length))
        } finally {
            view.endBatchEdit()
            applying = false
        }
        if (wasComposing) {
            view.context.getSystemService(InputMethodManager::class.java)?.restartInput(view)
        }
        onChanged?.invoke()
    }
}
