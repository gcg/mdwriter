package dev.mdwriter.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.text.LineBreaker
import android.text.InputType
import android.text.Layout
import android.text.Spanned
import android.text.TextUtils
import android.text.style.UpdateLayout
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import dev.mdwriter.R
import dev.mdwriter.editor.spans.EditorColors
import kotlin.math.roundToInt

/**
 * The editor widget. Configuration only — no styling, no undo, no shortcuts, no selection UI (those are later
 * tasks; see the task's Scope). [scrollTo] is pinned to `(0, 0)`: the EditText **never scrolls itself** (01 §4.4,
 * factcheck A15) — [EditorScrollView] is the only scroller. `bringPointIntoView` still works and reaches the
 * ScrollView ancestor via the normal View chain.
 */
class MarkdownEditText(
    context: Context,
) : EditText(context, null, 0, R.style.Widget_MdWriter_Editor) {
    val caret = CaretDrawable(dp(2), dp(1).toFloat())

    /** Toggled between `[0,0]` and `[0,len]` by [reflowAll]; a plain marker, never read for its own sake. */
    private val reflowTrigger = object : UpdateLayout {}
    private var triggerWide = false

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

    /** Rule 11: paste is always plain text; copy/cut clip a plain `String` (no spans reach the clipboard). */
    override fun onTextContextMenuItem(id: Int): Boolean =
        when (id) {
            android.R.id.paste -> {
                super.onTextContextMenuItem(android.R.id.pasteAsPlainText)
            }

            android.R.id.copy, android.R.id.cut -> {
                val min = minOf(selectionStart, selectionEnd)
                val max = maxOf(selectionStart, selectionEnd)
                if (min == max) {
                    false
                } else {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText(null, TextUtils.substring(text, min, max)))
                    if (id == android.R.id.cut) text!!.delete(min, max)
                    true
                }
            }

            else -> {
                super.onTextContextMenuItem(id)
            }
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
