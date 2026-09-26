package dev.mdwriter.data.document

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Pure "(conflict YYYY-MM-DD HHmm)" naming for `SaveBoth` (01 §6.3/§6.4). Callers make the result unique with
 * `NoteFiles.uniqueName`. */
object ConflictNames {
    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm")

    /** `("Walk", "md", 2026-09-25T14:02)` -> `"Walk (conflict 2026-09-25 1402).md"`. An empty [ext] gives no dot. */
    fun name(
        base: String,
        ext: String,
        at: LocalDateTime,
    ): String {
        val stamped = "$base (conflict ${FORMAT.format(at)})"
        return if (ext.isEmpty()) stamped else "$stamped.$ext"
    }
}
