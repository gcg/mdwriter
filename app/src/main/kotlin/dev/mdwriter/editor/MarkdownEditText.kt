package dev.mdwriter.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.graphics.text.LineBreaker
import android.text.InputType
import android.text.Layout
import android.text.Spanned
import android.text.TextUtils
import android.text.style.UpdateLayout
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import dev.mdwriter.R
import dev.mdwriter.editor.spans.EditorColors
import dev.mdwriter.editor.spans.TaskSpan
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The editor widget. [scrollTo] is pinned to `(0, 0)`: the EditText **never scrolls itself** (01 §4.4, factcheck
 * A15) — [EditorScrollView] is the only scroller. `bringPointIntoView` still works and reaches the ScrollView
 * ancestor via the normal View chain. [mdUndo]/[smartInput]/[commands] are wired by [EditorController]; they stay
 * nullable `var`s because `TextView`'s own constructor calls overridden methods before this class's `init` runs.
 */
class MarkdownEditText(
    context: Context,
) : EditText(context, null, 0, R.style.Widget_MdWriter_Editor) {
    val caret = CaretDrawable(dp(2), dp(1).toFloat())

    /** Toggled between `[0,0]` and `[0,len]` by [reflowAll]; a plain marker, never read for its own sake. */
    private val reflowTrigger = object : UpdateLayout {}
    private var triggerWide = false

    /** Set once by [EditorController.install]; T08 owns enforcement (01 §6.2): commits/keys that would mutate
     * the text are dropped, select + copy still work. */
    var readOnly: Boolean = false

    internal var mdUndo: MdUndoManager? = null
    internal var smartInput: SmartInput? = null
    internal var commands: EditorCommands? = null
    internal var selectionUi: SelectionUi? = null

    private var swallowKeyUp = -1
    private var taskDown = -1
    private var downX = 0f
    private var downY = 0f

    init {
        // Must run before any setText (incl. the super constructor's own EMPTY text): the FIRST document must
        // already be an MdEditable, or it starts as a plain SpannableStringBuilder with 162 ms keystrokes later.
        setEditableFactory(MdEditableFactory)
        isSaveEnabled = false // 01 §8: text lives on disk, never in the Bundle
        id = View.NO_ID // rule 8
        gravity = Gravity.TOP or Gravity.START
        inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        setHorizontallyScrolling(false)
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        includeFontPadding = false // every line = 1.30 em box + extra (uniform pitch)
        revealOnFocusHint = false // no ScrollView scroll-to-child jumps on focus
        // Hard rule 10: suppress the system floating toolbar but keep handles/IME. Deliberately no
        // customInsertionActionModeCallback — the caret "Paste" pill must stay (T09 AC8).
        customSelectionActionModeCallback = HideSystemSelectionToolbar
    }

    fun applyColors(c: EditorColors) {
        setTextColor(c.text)
        highlightColor = c.selection
        caret.color = c.accent
        setTextCursorDrawable(caret)
        textSelectHandle?.let { setTextSelectHandle(it.mutate().apply { setTint(c.accent) }) }
        textSelectHandleLeft?.let { setTextSelectHandleLeft(it.mutate().apply { setTint(c.accent) }) }
        textSelectHandleRight?.let { setTextSelectHandleRight(it.mutate().apply { setTint(c.accent) }) }
    }

    /** The EditText never scrolls itself (01 §4.4, factcheck A15). `bringPointIntoView` still reaches the ScrollView. */
    override fun scrollTo(
        x: Int,
        y: Int,
    ) = super.scrollTo(0, 0)

    /** Rule 11: paste is always plain text; copy/cut clip a plain `String` (no spans reach the clipboard) — the
     * bodies below are unchanged from T05/T06, only wrapped in an [MdUndoManager] undo group and a hard break
     * (a pasted/cut block is never merged with adjacent typing). Undo/redo route through the platform context
     * menu's own ids too (`android.R.id.undo`/`redo`, e.g. a hardware Ctrl+Z some IMEs send this way). */
    override fun onTextContextMenuItem(id: Int): Boolean =
        when (id) {
            android.R.id.undo -> {
                commands?.undo()
                true
            }

            android.R.id.redo -> {
                commands?.redo()
                true
            }

            android.R.id.paste, android.R.id.pasteAsPlainText -> {
                if (readOnly) {
                    false
                } else {
                    mdUndo?.hardBreak()
                    runGrouped { super.onTextContextMenuItem(android.R.id.pasteAsPlainText) }
                }
            }

            android.R.id.copy -> {
                copyOrCut(cut = false)
            }

            android.R.id.cut -> {
                if (readOnly) {
                    false
                } else {
                    mdUndo?.hardBreak()
                    runGrouped { copyOrCut(cut = true) }
                }
            }

            else -> {
                super.onTextContextMenuItem(id)
            }
        }

    private fun copyOrCut(cut: Boolean): Boolean {
        val min = minOf(selectionStart, selectionEnd)
        val max = maxOf(selectionStart, selectionEnd)
        if (min == max) return false
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText(null, TextUtils.substring(text, min, max)))
        if (cut) text!!.delete(min, max)
        return true
    }

    private fun runGrouped(block: () -> Boolean): Boolean {
        val u = mdUndo ?: return block()
        return u.group(block)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        val si = smartInput ?: return ic
        return SmartInputConnection(ic, si)
    }

    /** True for any hardware key that would mutate the text if let through (01 §6.2 read-only enforcement):
     * printable keys, plus Enter/Delete/Tab even though those aren't `isPrintingKey`. Select/copy/navigation keys
     * (arrows, Ctrl+A/C, …) are never blocked. */
    private fun blocksInReadOnly(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean =
        keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL ||
            keyCode == KeyEvent.KEYCODE_TAB || event.isPrintingKey

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (readOnly && blocksInReadOnly(keyCode, event)) return true
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            val shiftOnly =
                event.metaState and KeyEvent.META_SHIFT_ON != 0 &&
                    event.metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_META_ON) == 0
            if (event.hasNoModifiers() || shiftOnly) {
                smartInput?.onTab(event.isShiftPressed)
                swallowKeyUp = keyCode
                return true
            }
        }
        if (event.hasNoModifiers()) {
            val si = smartInput
            val handled =
                si != null &&
                    when (keyCode) {
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> si.onEnter()
                        KeyEvent.KEYCODE_DEL -> si.onBackspace()
                        else -> false
                    }
            if (handled) {
                swallowKeyUp = keyCode
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode == swallowKeyUp) {
            swallowKeyUp = -1
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyShortcut(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val cmd = EditorShortcuts.map(keyCode, event.metaState) ?: return super.onKeyShortcut(keyCode, event)
        val c = commands ?: return super.onKeyShortcut(keyCode, event)
        when (cmd) {
            ShortcutCommand.Undo -> c.undo()
            ShortcutCommand.Redo -> c.redo()
            is ShortcutCommand.Action -> c.perform(cmd.action)
        }
        return true
    }

    override fun onSelectionChanged(
        selStart: Int,
        selEnd: Int,
    ) {
        super.onSelectionChanged(selStart, selEnd)
        mdUndo?.onSelectionChanged(selStart, selEnd)
        selectionUi?.onSelectionChanged(selStart, selEnd)
    }

    override fun onFocusChanged(
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (!focused) mdUndo?.hardBreak()
        selectionUi?.onFocusChanged(focused)
    }

    override fun performClick(): Boolean = super.performClick()

    /** Tapping a `[ ]`/`[x]` marker toggles the task without moving the caret or requesting the IME (a
     * synthesized `ACTION_CANCEL` replaces the real `ACTION_UP` before it reaches `TextView`'s own click/caret
     * handling). EditText-local coords; [EditorScrollView] scrolls, so the EditText's own scrollX/scrollY are
     * always 0 — no scroll maths needed here. */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                taskDown = if (readOnly) -1 else taskMarkerAt(event.x, event.y)
            }

            MotionEvent.ACTION_UP -> {
                if (taskDown >= 0) {
                    val at = taskDown
                    taskDown = -1
                    val slop = ViewConfiguration.get(context).scaledTouchSlop
                    if (abs(event.x - downX) < slop && abs(event.y - downY) < slop &&
                        event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()
                    ) {
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.onTouchEvent(cancel)
                        cancel.recycle()
                        commands?.toggleTaskAt(at)
                        performClick()
                        return true
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                taskDown = -1
            }
        }
        return super.onTouchEvent(event)
    }

    private fun taskMarkerAt(
        x: Float,
        y: Float,
    ): Int {
        val l = layout ?: return -1
        val t = text ?: return -1
        val off = getOffsetForPosition(x, y)
        if (off < 0) return -1
        val line = l.getLineForVertical((y - totalPaddingTop).toInt())
        val lx = x - totalPaddingLeft
        val pad = 8 * resources.displayMetrics.density
        for (sp in t.getSpans((off - 3).coerceAtLeast(0), (off + 3).coerceAtMost(t.length), TaskSpan::class.java)) {
            val s = t.getSpanStart(sp)
            if (l.getLineForOffset(s) != line) continue
            val a = l.getPrimaryHorizontal(s)
            val b = l.getPrimaryHorizontal(t.getSpanEnd(sp))
            if (lx in (minOf(a, b) - pad)..(maxOf(a, b) + pad)) return s
        }
        return -1
    }

    /**
     * Forces one full reflow (colours/typeface/size change: spans read [dev.mdwriter.editor.spans.EditorStyle]
     * live, so no span object needs replacing — see `plans/tasks/T06-styling-spans.md` Reference §E).
     * `DynamicLayout.onSpanChanged` reflows both the OLD and the NEW range of an `UpdateLayout` span
     * (factcheck A13), and `SpannableStringBuilder.setSpan` on an attached span always broadcasts even with
     * unchanged bounds, so toggling one permanent trigger span between `[0,0]` and `[0,len]` costs one full +
     * one empty reflow — cheaper than a set/remove pair (two full reflows).
     */
    fun reflowAll() {
        val e = text ?: return
        triggerWide = !triggerWide
        e.setSpan(reflowTrigger, 0, if (triggerWide) e.length else 0, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        invalidate()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
}
