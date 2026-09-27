package dev.mdwriter.ui.preview

import dev.mdwriter.markdown.MarkdownHtml
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A rendered preview, ready to [PreviewWebView.show]: the full HTML page, the anchor id of the heading the caret
 * was under (if any), and its proportional position in the document (the scroll-sync fallback). */
data class PreviewPage(
    val html: String,
    val anchor: String?,
    val fraction: Float,
)

/**
 * Renders a document to a [PreviewPage] off the main thread (01 §7 "Preview HTML" row: `Default`). Takes a plain
 * text snapshot + caret (never a View/controller reference — `EditorViewModel.openPreview` reads the controller
 * on main, before launching this).
 */
class PreviewRenderer(
    private val default: CoroutineDispatcher,
    private val md: MarkdownHtml = MarkdownHtml(allowRawHtml = true),
) {
    suspend fun render(
        text: String,
        caret: Int,
        theme: PreviewTheme,
        title: String,
    ): PreviewPage =
        withContext(default) {
            val c = caret.coerceIn(0, text.length)
            val lineEnd = text.indexOf('\n', c).let { if (it < 0) text.length else it }
            val html = md.renderPage(text, theme.themeClass, theme.cssVars, title)
            val headingIndex = md.headingCount(text.substring(0, lineEnd)) - 1
            PreviewPage(
                html = html,
                anchor = PreviewSync.anchorFor(html, headingIndex),
                fraction = if (text.isEmpty()) 0f else c.toFloat() / text.length,
            )
        }
}
