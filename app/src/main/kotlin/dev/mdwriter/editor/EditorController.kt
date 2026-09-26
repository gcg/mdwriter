package dev.mdwriter.editor

import android.content.Context
import android.os.SystemClock
import android.text.Spanned
import android.view.WindowInsets
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.core.view.doOnPreDraw
import dev.mdwriter.editor.spans.EditorStyle
import dev.mdwriter.editor.spans.HangRoomSpan
import dev.mdwriter.editor.spans.PaintTextMeasurer
import dev.mdwriter.editor.spans.SpanFactory
import dev.mdwriter.editor.spans.SpanMaterializer
import dev.mdwriter.markdown.MarkdownHighlighter
import dev.mdwriter.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Text + selection + scroll position to install; `readOnly` only sets `showSoftInputOnFocus` here (T08 enforces it). */
data class InstallRequest(
    val text: String,
    val selection: Int,
    val scrollY: Int,
    val readOnly: Boolean,
)

/**
 * Facade the UI talks to (01 §6.2). This task adds the styled document install: open a document, get it fully
 * styled on the first frame (build off-main, `setText` on main). T07–T17 extend this same class — do not add
 * stub members for restyle/undo/selection/find here; each owning task adds its own (see the task's Scope).
 */
class EditorController(
    context: Context,
    initialStyle: EditorStyle,
) {
    val style: EditorStyle = initialStyle
    val editText =
        MarkdownEditText(context).also {
            it.applyColors(initialStyle.colors)
            it.typeface = initialStyle.fonts.regular
        }
    val scrollView = EditorScrollView(context, editText, style)

    private val hangRoom = HangRoomSpan(style)

    /** Main thread only, after [install]; `null` before the first install. T07 reads this to drive its reconcile. */
    internal var highlighter: MarkdownHighlighter? = null
        private set

    init {
        scrollView.onGeometryChanged = { syncHangRoom() }
    }

    /** Main thread; builds the styled text off-main, then a single `setText` installs it. */
    suspend fun install(doc: InstallRequest) {
        val t0 = SystemClock.uptimeMillis()
        val gutter = style.gutterPx
        val hl = MarkdownHighlighter(enableHighlight = style.highlightSyntax, enableFrontMatter = true)
        val ssb =
            withContext(Dispatchers.Default) {
                buildStyledDocument(
                    doc.text,
                    hl,
                    SpanFactory(PaintTextMeasurer(style)),
                    SpanMaterializer(style),
                    hangRoom.takeIf { gutter > 0 },
                )
            }
        val t1 = SystemClock.uptimeMillis()
        editText.setText(ssb, TextView.BufferType.EDITABLE) // factory copies into MdEditable
        highlighter = hl // explicit hand-off: Default -> main, no concurrent use (01 §6.1)
        editText.setSelection(doc.selection.coerceIn(0, editText.length()))
        editText.showSoftInputOnFocus = !doc.readOnly
        scrollView.doOnNextLayout { scrollView.scrollTo(0, doc.scrollY) }
        editText.doOnPreDraw {
            Log.i("MDPERF") {
                "OPEN|chars=${doc.text.length}|build=${t1 - t0}|firstFrame=${SystemClock.uptimeMillis() - t0}"
            }
        }
    }

    /** Theme/font/size/line-length/highlightSyntax change (T07 restyles; T19 policy): colours/typeface/geometry
     * are read live by every span, so this only needs to re-derive geometry and force one full reflow. */
    fun setStyle(s: EditorStyle) {
        editText.applyColors(s.colors)
        editText.typeface = s.fonts.regular
        scrollView.applyGeometry() // also invokes syncHangRoom via onGeometryChanged
        editText.reflowAll()
    }

    /** Keeps the whole-document [HangRoomSpan] in sync with the current gutter (≥ 600 dp only, 02 §3). */
    private fun syncHangRoom() {
        val e = editText.text ?: return
        val gutter = style.gutterPx
        val attached = e.getSpanStart(hangRoom) >= 0
        if (gutter > 0 && !attached) {
            e.setSpan(hangRoom, 0, e.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        } else if (gutter == 0 && attached) {
            e.removeSpan(hangRoom)
        }
        editText.reflowAll() // width changes already relayout; this makes a NEW gutter value take effect
    }

    fun snapshot(): String = editText.text.toString()

    fun requestFocus() {
        editText.requestFocus()
    }

    /** T12 drawer IME restore. */
    fun hasFocus(): Boolean = editText.hasFocus()

    /** T12/T13/T16. */
    fun collapseSelection() {
        val c = editText.selectionEnd
        if (c >= 0) editText.setSelection(c)
    }

    fun showIme() {
        editText.requestFocus()
        editText.windowInsetsController?.show(WindowInsets.Type.ime())
    }

    fun hideIme() {
        editText.windowInsetsController?.hide(WindowInsets.Type.ime())
    }

    fun caret(): Int = editText.selectionEnd

    fun scrollY(): Int = scrollView.scrollY

    fun release() {
        scrollView.onGeometryChanged = null
    }
}
