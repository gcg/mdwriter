package dev.mdwriter.ui.preview

/**
 * Finds the anchor id of the heading the caret is under, inside an already-rendered page's HTML (heading ids come
 * from commonmark-java's `HeadingAnchorExtension`, already unique/slugified — never re-derive them). Pure string/
 * regex work; [PreviewSyncTest] covers it directly.
 */
object PreviewSync {
    private val ID = Regex("""<h[1-6]\b[^>]*\bid="([^"]+)"""")

    /** Every heading's anchor id, in document order. */
    fun anchorIds(html: String): List<String> = ID.findAll(html).map { it.groupValues[1] }.toList()

    /** The [headingIndex]-th heading's anchor id (0-based), or `null` for a negative or out-of-range index. */
    fun anchorFor(
        html: String,
        headingIndex: Int,
    ): String? = if (headingIndex < 0) null else anchorIds(html).getOrNull(headingIndex)
}
