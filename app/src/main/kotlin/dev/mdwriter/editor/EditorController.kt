package dev.mdwriter.editor

import android.content.Context
import android.view.WindowInsets
import androidx.core.view.doOnNextLayout
import dev.mdwriter.editor.spans.EditorStyle

/** Text + selection + scroll position to install; `readOnly` only sets `showSoftInputOnFocus` here (T08 enforces it). */
data class InstallRequest(
    val text: String,
    val selection: Int,
    val scrollY: Int,
    val readOnly: Boolean,
)

/**
 * Skeleton facade the UI talks to (01 §6.2). This task only builds enough for a plain, unstyled editor: install
 * plain text, keep style/geometry in sync, release. T06–T15 extend this same class — do not add stub members for
 * spans/restyle/undo/selection/find here; each owning task adds its own (see the task's Scope).
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

    /** Main thread; T06 replaces the body with the styled (off-main-built) install. */
    suspend fun install(doc: InstallRequest) {
        editText.setText(doc.text)
        editText.setSelection(doc.selection.coerceIn(0, editText.length()))
        editText.showSoftInputOnFocus = !doc.readOnly
        scrollView.doOnNextLayout { scrollView.scrollTo(0, doc.scrollY) }
        scrollView.requestLayout()
    }

    /** Theme/font/size/line-length/highlightSyntax change (T07 restyles; T19 policy). */
    fun setStyle(s: EditorStyle) {
        editText.applyColors(s.colors)
        editText.typeface = s.fonts.regular
        scrollView.applyGeometry()
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
