package dev.mdwriter.intents

import dev.mdwriter.data.storage.TextCodec
import dev.mdwriter.markdown.DocTitle

/** A note built from a [ACTION_SEND][android.content.Intent.ACTION_SEND] text share: [baseName] (sanitized, no
 * extension) and the final [body] (LF-normalized, exactly one trailing newline). */
data class SharedNote(
    val baseName: String,
    val body: String,
) {
    companion object {
        /**
         * Builds a [SharedNote] from a SEND intent's subject/text (T18 step 4):
         * - Normalizes [text] to LF.
         * - If [subject] is non-blank, the body does not already start with a `#` heading, and the trimmed body
         *   isn't just the subject repeated, prepends `"# $subject\n\n"` (a duplicate heading is never added).
         * - Ensures exactly one trailing newline.
         * - [baseName] comes from [DocTitle.fromContent] of the FINAL body (falls back to "Shared note").
         */
        fun compose(
            subject: String?,
            text: String,
        ): SharedNote {
            val normalized = TextCodec.normalizeToLf(text)
            val trimmedSubject = subject?.trim().orEmpty()
            val alreadyHeading = normalized.trimStart().startsWith("#")
            val body =
                if (trimmedSubject.isNotBlank() && !alreadyHeading && normalized.trim() != trimmedSubject) {
                    "# $trimmedSubject\n\n$normalized"
                } else {
                    normalized
                }
            val finalBody = body.trimEnd('\n') + "\n"
            val baseName = DocTitle.sanitizeFileName(DocTitle.fromContent(finalBody) ?: "Shared note")
            return SharedNote(baseName, finalBody)
        }
    }
}
