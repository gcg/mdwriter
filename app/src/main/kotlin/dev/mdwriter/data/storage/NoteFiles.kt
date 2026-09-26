package dev.mdwriter.data.storage

import dev.mdwriter.data.library.LibraryEntry
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object StorageLimits {
    const val LARGE_BYTES = 1L shl 20 // > 1 MiB: "Large document" notice (T11)
    const val READ_ONLY_BYTES = 5L shl 20 // > 5 MiB: open read-only (T11)
    const val MAX_OPEN_BYTES = 16L shl 20 // > 16 MiB: refuse with TooLarge (stores enforce on read)
    const val EXCERPT_BYTES = 2048
}

object NoteFiles {
    val EXTENSIONS = setOf("md", "markdown", "mdown", "mkd", "txt")

    /** Folders first (name, case-insensitive), then files newest lastModified first, then name. */
    val DEFAULT_ORDER: Comparator<LibraryEntry> =
        Comparator { a, b ->
            if (a.isFolder != b.isFolder) {
                if (a.isFolder) -1 else 1
            } else if (a.isFolder) {
                a.name.compareTo(b.name, ignoreCase = true)
            } else {
                val byTime = (b.lastModified ?: 0L).compareTo(a.lastModified ?: 0L) // newest first
                if (byTime != 0) byTime else a.name.compareTo(b.name, ignoreCase = true)
            }
        }

    /** (base, rawExt) — rawExt keeps the original case; empty when the last '.' is a leading dot or trailing. */
    private fun splitNameRaw(name: String): Pair<String, String> {
        val idx = name.lastIndexOf('.')
        return if (idx <= 0 || idx == name.length - 1) name to "" else name.substring(0, idx) to name.substring(idx + 1)
    }

    /** lowercase, no dot; "" if none ("a.MD" -> "md", ".md" -> "") */
    fun extensionOf(name: String): String = splitNameRaw(name).second.lowercase()

    /** "Walk.md" -> "Walk"; "Walk" -> "Walk" */
    fun baseName(name: String): String = splitNameRaw(name).first

    fun isHidden(name: String): Boolean = name.startsWith(".")

    fun isSupported(name: String): Boolean = !isHidden(name) && extensionOf(name) in EXTENSIONS

    /** MIME for SAF createDocument (01 §10 rule 15): md/markdown -> text/markdown, txt -> text/plain, else octet-stream. */
    fun mimeFor(name: String): String =
        when (extensionOf(name)) {
            "md", "markdown" -> "text/markdown"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }

    /** "Untitled.md" -> first of "Untitled.md", "Untitled 2.md", "Untitled 3.md"... not in [taken] (case-insensitive). */
    fun uniqueName(
        desired: String,
        taken: Collection<String>,
    ): String {
        val takenLower = taken.map { it.lowercase() }.toHashSet()
        if (desired.lowercase() !in takenLower) return desired
        val (base, ext) = splitNameRaw(desired)
        var n = 2
        while (true) {
            val candidate = if (ext.isEmpty()) "$base $n" else "$base $n.$ext"
            if (candidate.lowercase() !in takenLower) return candidate
            n++
        }
    }

    /** Lenient UTF-8 decode of a file head: strips a BOM, drops a trailing partial character (U+FFFD at the end). */
    fun decodeHead(
        head: ByteArray,
        length: Int,
    ): String {
        val bom = TextCodec.hasBom(head)
        val start = if (bom) 3 else 0
        val end = minOf(length, head.size).coerceAtLeast(start)
        val decoded =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(ByteBuffer.wrap(head, start, end - start))
                .toString()
        return if (decoded.isNotEmpty() && decoded.last() == '�') decoded.substring(0, decoded.length - 1) else decoded
    }
}
