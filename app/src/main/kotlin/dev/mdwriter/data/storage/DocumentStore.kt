package dev.mdwriter.data.storage

import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.FolderRef
import dev.mdwriter.data.library.LibraryEntry
import kotlinx.coroutines.flow.Flow

data class FileStat(
    val lastModified: Long?,
    val size: Long?,
)

/** Returned by trash(); passed back to restore(). [trashId] is the TrashBin entry id (or a store-specific id). */
data class TrashToken(
    val original: DocRef,
    val displayName: String,
    val trashId: String,
)

interface DocumentStore { // implemented by InternalStore (T10), SafTreeStore (T14), ExternalDocStore (T18)
    suspend fun list(folder: FolderRef): List<LibraryEntry>

    suspend fun read(ref: DocRef): ByteArray

    suspend fun write(
        ref: DocRef,
        bytes: ByteArray,
    )

    suspend fun stat(ref: DocRef): FileStat? // null = gone

    suspend fun displayName(ref: DocRef): String // ADDITION to 01 §6.3: name incl. extension

    suspend fun create(
        folder: FolderRef,
        displayName: String,
    ): DocRef

    suspend fun createFolder(
        parent: FolderRef,
        name: String,
    ): FolderRef

    suspend fun rename(ref: DocRef, newDisplayName: String): DocRef // ALWAYS use the returned ref afterwards

    suspend fun trash(ref: DocRef): TrashToken

    suspend fun restore(token: TrashToken): DocRef?

    suspend fun move(
        ref: DocRef,
        to: FolderRef,
    ): DocRef

    fun changes(folder: FolderRef): Flow<Unit>
}
