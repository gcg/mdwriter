package dev.mdwriter.ui.preview

/**
 * Pure path helpers for [DocumentImagePathHandler] (T16): resolving a `/doc/<path>` request against the note's own
 * directory, without ever escaping it. No Android imports — plain JVM, [ImagePathTest] covers it directly.
 */
object ImagePath {
    /**
     * Decoded path after `"/doc/"`, split on `'/'` with `"."` and empty segments dropped (`".."` is KEPT — [walk]
     * interprets it). `null` if [path] contains a backslash or NUL (never a valid relative path on this platform)
     * or is absolute (starts with `'/'`).
     */
    fun split(path: String): List<String>? {
        if (path.contains('\\') || path.contains('\u0000')) return null
        if (path.startsWith("/")) return null
        return path.split('/').filter { it.isNotEmpty() && it != "." }
    }

    /**
     * Walks [tokens] from [base] (a non-empty root..dir stack — `base[0]` is the boundary [walk] never pops past).
     * `".."` pops one level; any other token asks [child] to resolve it under the current node. Returns `null` on
     * an escape past `base[0]`, or the moment [child] itself returns `null` (a missing/disallowed entry).
     */
    fun <T> walk(
        base: List<T>,
        tokens: List<String>,
        child: (parent: T, name: String) -> T?,
    ): T? {
        if (base.isEmpty()) return null
        val stack = base.toMutableList()
        for (token in tokens) {
            if (token == "..") {
                if (stack.size <= 1) return null
                stack.removeAt(stack.size - 1)
            } else {
                val next = child(stack.last(), token) ?: return null
                stack.add(next)
            }
        }
        return stack.last()
    }

    /** MIME type for a supported image extension (case-insensitive), or `null` (anything else is refused). */
    fun imageMime(name: String): String? {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return null
        return when (name.substring(dot + 1).lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            else -> null
        }
    }
}
