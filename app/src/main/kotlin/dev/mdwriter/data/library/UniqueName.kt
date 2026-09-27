package dev.mdwriter.data.library

/** Pure name helpers for the library (T12): "Untitled.md" / "Untitled 2.md" numbering, "X copy.md" duplicate names,
 * and splitting a display name into base + extension. Deliberately separate from `data.storage.NoteFiles` (which
 * this package must not depend on) even though the rules are similar. */
object UniqueName {
    /** Supported note extensions (lower-case, no dot). Anything else is part of the base name. */
    val NOTE_EXTENSIONS = setOf("md", "markdown", "mdown", "mkd", "txt", "text")

    /** "Groceries.md" -> ("Groceries", "md"); "notes.backup" -> ("notes.backup", null). */
    fun splitName(fileName: String): Pair<String, String?> {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0) return fileName to null
        val ext = fileName.substring(dot + 1).lowercase()
        return if (ext in NOTE_EXTENSIONS) fileName.substring(0, dot) to ext else fileName to null
    }

    /** "Untitled.md", "Untitled 2.md", "Untitled 3.md" … [taken] holds lower-cased sibling names. */
    fun numbered(
        base: String,
        ext: String,
        taken: Set<String>,
    ): String =
        generateSequence(1) { it + 1 }
            .map { n -> if (n == 1) "$base.$ext" else "$base $n.$ext" }
            .first { it.lowercase() !in taken }

    /** "Groceries copy.md", "Groceries copy 2.md" … */
    fun copyOf(
        base: String,
        ext: String,
        taken: Set<String>,
    ): String = numbered("$base copy", ext, taken)
}
