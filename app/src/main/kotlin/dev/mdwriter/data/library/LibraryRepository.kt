package dev.mdwriter.data.library

import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.storage.DocumentStore
import dev.mdwriter.data.storage.StorageError
import dev.mdwriter.data.storage.StorageException

/**
 * Routes a [DocRef]/[LocationId] to the [DocumentStore] that owns it (01 §5). Minimal for T11 (Internal only);
 * T14 adds [LocationId.Tree]/[DocRef.TreeDoc] (a real `SafTreeStore` per tree URI), T18 adds [DocRef.External].
 *
 * [internalStore] is typed as the [DocumentStore] interface (not the concrete `InternalStore`) purely so tests can
 * substitute an in-memory `FakeDocumentStore`; `AppContainer` still passes its real `InternalStore`.
 */
class LibraryRepository(
    private val internalStore: DocumentStore,
    private val settings: SettingsRepository,
) {
    fun storeFor(ref: DocRef): DocumentStore =
        when (ref) {
            is DocRef.InternalFile -> internalStore
            is DocRef.TreeDoc -> throw StorageException(StorageError.PermissionLost)
            is DocRef.External -> throw StorageException(StorageError.NotFound)
        }

    fun storeFor(location: LocationId): DocumentStore =
        when (location) {
            LocationId.Internal -> internalStore
            is LocationId.Tree -> throw StorageException(StorageError.PermissionLost)
        }

    fun rootOf(location: LocationId): FolderRef = FolderRef(location, "")

    fun parentOf(ref: DocRef): FolderRef? =
        when (ref) {
            is DocRef.InternalFile -> FolderRef(LocationId.Internal, ref.relPath.substringBeforeLast('/', ""))
            else -> null
        }

    /** Depth-first walk of the internal tree (depth <= 8) via [DocumentStore.list]; the file with the largest
     * `lastModified` (used by `EditorViewModel`'s start-up fallback #4). `null` when the library is empty. */
    suspend fun newestDoc(): DocRef? = newestIn(rootOf(LocationId.Internal), depth = 0)?.first

    private suspend fun newestIn(
        folder: FolderRef,
        depth: Int,
    ): Pair<DocRef, Long>? {
        if (depth > MAX_DEPTH) return null
        val entries =
            try {
                internalStore.list(folder)
            } catch (_: StorageException) {
                return null
            }
        var best: Pair<DocRef, Long>? = null
        for (e in entries) {
            val doc = e.doc
            if (!e.isFolder && doc != null) {
                val time = e.lastModified ?: Long.MIN_VALUE
                if (best == null || time > best.second) best = doc to time
            }
        }
        for (e in entries) {
            val folderRef = e.folder
            if (e.isFolder && folderRef != null) {
                val childBest = newestIn(folderRef, depth + 1)
                if (childBest != null && (best == null || childBest.second > best.second)) best = childBest
            }
        }
        return best
    }

    private companion object {
        const val MAX_DEPTH = 8
    }
}
