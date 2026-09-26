package dev.mdwriter.data.document

import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.storage.FileStat
import dev.mdwriter.data.storage.TextFormat

/**
 * Deviation from 01 §6.3's sketch: [DocumentRepository.save] additionally takes `format` (so saving round-trips
 * BOM/line-ending byte-for-byte), and this type carries extra fields beyond the sketch's `(text, baseline, format,
 * readOnly)` (see STATUS T11).
 *
 * [diskTextIfConflict]: non-null only when the recovery copy we preferred is OLDER than a disk file that has
 * since changed underneath it — i.e. a genuine three-way conflict discovered at load time, never silently dropped.
 */
data class LoadedDocument(
    val ref: DocRef,
    val text: String,
    val baseline: FileStat,
    val format: TextFormat,
    val readOnly: Boolean,
    val displayName: String,
    val large: Boolean,
    val recovered: Boolean,
    val diskTextIfConflict: String?,
)

/** Result of re-`stat`ing (and, if changed, re-loading) a document against a known baseline. */
sealed interface ExternalCheck {
    data object Unchanged : ExternalCheck

    data object Gone : ExternalCheck

    data class Changed(
        val disk: LoadedDocument,
    ) : ExternalCheck
}
