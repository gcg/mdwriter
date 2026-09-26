package dev.mdwriter.editor

import android.graphics.Path
import android.graphics.RectF
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Selection pill visibility/anchor state (01 §6.2 `EditorController.selection`). [anchor] is in
 * [EditorScrollView] viewport coordinates. `start`/`end` are always published (T15 stats), even while [visible]
 * is `false` — only [anchor] is meaningless then (it is [NONE]).
 */
data class SelectionState(
    val visible: Boolean,
    val anchor: RectF,
    val start: Int,
    val end: Int,
) {
    companion object {
        // Never mutate: shared by every hidden/collapsed publish.
        internal val NONE = RectF()
        val Hidden = SelectionState(false, NONE, 0, 0)
    }
}

/**
 * Suppresses the system floating selection toolbar (01 §4.9, hard rule 10): [onCreateActionMode] must return
 * `true` (returning `false` kills the selection outright) and the menu must be cleared in BOTH
 * [onCreateActionMode] and [onPrepareActionMode] (`Editor.java` re-adds its own items around both calls). This
 * keeps the selection handles and the IME — only the floating text-labelled toolbar disappears. Deliberately NOT
 * installed as a `customInsertionActionModeCallback`: the system's caret "Paste" pill must stay (AC8).
 */
object HideSystemSelectionToolbar : ActionMode.Callback {
    @VisibleForTesting
    internal var createCount = 0

    override fun onCreateActionMode(
        mode: ActionMode,
        menu: Menu,
    ): Boolean {
        createCount++
        menu.clear()
        return true
    }

    override fun onPrepareActionMode(
        mode: ActionMode,
        menu: Menu,
    ): Boolean {
        menu.clear()
        return true
    }

    override fun onActionItemClicked(
        mode: ActionMode,
        item: MenuItem,
    ): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode) = Unit
}

/**
 * Publishes [SelectionState] for the Compose pill (`dev.mdwriter.ui.toolbar.FormatToolbarOverlay`). View-layer
 * only (01 §3: no Compose imports in `dev.mdwriter.editor`).
 *
 * Every selection change hides the pill immediately, then re-shows it 150 ms after the LAST change — this covers
 * handle drags and repeated hardware Shift+arrow presses without the pill flickering in and out on every
 * intermediate step. Our own programmatic edits (toolbar taps, shortcuts) instead re-anchor at once via
 * [programmatic], with no hide flicker, so an action can chain onto the still-selected, transformed text.
 */
internal class SelectionUi(
    private val editText: MarkdownEditText,
    private val scrollView: EditorScrollView,
) : ViewTreeObserver.OnPreDrawListener,
    View.OnAttachStateChangeListener {
    private val _state = MutableStateFlow(SelectionState.Hidden)
    val state: StateFlow<SelectionState> = _state.asStateFlow()

    private val path = Path()
    private val bounds = RectF()
    private var programmaticDepth = 0
    private val showRunnable = Runnable { publish(show = true) }

    override fun onViewAttachedToWindow(v: View) {
        v.viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onViewDetachedFromWindow(v: View) {
        v.viewTreeObserver.removeOnPreDrawListener(this)
        editText.removeCallbacks(showRunnable)
    }

    /** Every change hides; re-show 150 ms after the LAST change (handle drags, Shift+arrows). */
    fun onSelectionChanged(
        s: Int,
        e: Int,
    ) {
        if (programmaticDepth > 0) return
        editText.removeCallbacks(showRunnable)
        publish(show = false)
        if (s != e && editText.hasFocus()) editText.postDelayed(showRunnable, RESHOW_DELAY_MS)
    }

    fun onFocusChanged(focused: Boolean) =
        onSelectionChanged(editText.selectionStart, if (focused) editText.selectionEnd else editText.selectionStart)

    /** Our own edits (toolbar/shortcut): no hide flicker, re-anchor immediately. */
    fun <T> programmatic(block: () -> T): T {
        programmaticDepth++
        try {
            return block()
        } finally {
            if (--programmaticDepth == 0) {
                editText.removeCallbacks(showRunnable)
                publish(show = true)
            }
        }
    }

    /** Covers scroll, relayout and restyle-driven geometry changes; StateFlow dedups equal states (RectF.equals). */
    override fun onPreDraw(): Boolean {
        if (_state.value.visible) publish(show = true)
        return true
    }

    private fun publish(show: Boolean) {
        val s = minOf(editText.selectionStart, editText.selectionEnd)
        val e = maxOf(editText.selectionStart, editText.selectionEnd)
        val r = if (show && s in 0 until e && editText.hasFocus()) visibleRect(s, e) else null
        // start/end always published (T15 stats) even when hidden/collapsed.
        _state.value = SelectionState(r != null, r ?: SelectionState.NONE, s, e)
    }

    /** Bounds of the VISIBLE part of the selection in [EditorScrollView] viewport coords; `null` if off-screen. */
    private fun visibleRect(
        s: Int,
        e: Int,
    ): RectF? {
        val l = editText.layout ?: return null
        val vh = scrollView.height
        if (vh <= 0) return null
        val ox = editText.left + editText.totalPaddingLeft - scrollView.scrollX
        // NOT editText.scrollY: it is pinned to 0 (01 §4.4) — EditorScrollView is the only scroller.
        val oy = editText.top + editText.totalPaddingTop - scrollView.scrollY
        val vs = maxOf(s, l.getLineStart(l.getLineForVertical(-oy)))
        val ve = minOf(e, l.getLineEnd(l.getLineForVertical(vh - oy)))
        if (vs >= ve) return null
        path.reset()
        l.getSelectionPath(vs, ve, path)
        path.computeBounds(bounds)
        val r =
            RectF(bounds).apply {
                offset(ox.toFloat(), oy.toFloat())
                top = maxOf(top, 0f)
                bottom = minOf(bottom, vh.toFloat())
            }
        return if (r.bottom > r.top) r else null
    }

    private companion object {
        // = WriterMotion.PILL_RESHOW_DELAY_MS (no Compose import allowed in this package).
        const val RESHOW_DELAY_MS = 150L
    }
}
