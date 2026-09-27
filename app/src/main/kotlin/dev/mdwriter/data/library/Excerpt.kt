package dev.mdwriter.data.library

import dev.mdwriter.markdown.DocTitle

/** THE excerpt rule is `DocTitle.excerpt` (T04); this is a one-line delegate so the library layer has its own
 * name for it and never re-implements the rule. */
object Excerpt {
    fun fromPrefix(text: String): String? = DocTitle.excerpt(text)
}
