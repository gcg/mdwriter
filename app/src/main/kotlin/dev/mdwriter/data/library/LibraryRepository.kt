package dev.mdwriter.data.library

import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.storage.DocumentStore
import dev.mdwriter.data.storage.NoteFiles
import dev.mdwriter.data.storage.StorageError
import dev.mdwriter.data.storage.StorageException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One node of [LibraryRepository.folderTree]: [folder] itself, its display [name] and its [depth] (root = 0). */
data class FolderNode(
    val folder: FolderRef,
    val name: String,
    val depth: Int,
)

/** One [LibraryRepository.search] result: the matching [entry], its folder path for display (e.g. "Drafts/Trip"),
 * and a short [snippet] of surrounding content when the match was in the body (null when only the name matched). */
data class SearchHit(
    val entry: LibraryEntry,
    val folderPath: String,
    val snippet: String?,
)

/**
 * Routes a [DocRef]/[LocationId] to the [DocumentStore] that owns it (01 §5). T11: Internal only; T12 (this file)
 * adds the listing/search/mutation surface the library drawer needs, all generically on top of [DocumentStore] so
 * T14's `SafTreeStore` inherits it for free. T14 adds [LocationId.Tree]/[DocRef.TreeDoc] (a real `SafTreeStore` per
 * tree URI), T18 adds [DocRef.External].
 *
 * [internalStore] is typed as the [DocumentStore] interface (not the concrete `InternalStore`) purely so tests can
 * substitute an in-memory `FakeDocumentStore`; `AppContainer` still passes its real `InternalStore`.
 */
class LibraryRepository(
    private val internalStore: DocumentStore,
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
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

    /** Display name (incl. extension) derived purely from the ref; null for stores where that isn't known yet
     * (Tree/External, until T14/T18). */
    fun nameOf(ref: DocRef): String? =
        when (ref) {
            is DocRef.InternalFile -> ref.fileName
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

    // ---- T12: live listing --------------------------------------------------------------------------------------

    private val invalidations =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Forces every open [entries] flow to re-list. Called after every mutation (this class always does it itself). */
    fun invalidate() {
        invalidations.tryEmit(Unit)
    }

    /** Emits the listing of [folder] on subscription, whenever the store reports a change to it, and whenever
     * [invalidate] fires. Listing itself always runs on [io]; the flow is conflated (a slow collector only ever
     * sees the latest listing, never a backlog). */
    fun entries(folder: FolderRef): Flow<List<LibraryEntry>> {
        val store = storeFor(folder.location)
        return merge(flowOf(Unit), store.changes(folder), invalidations)
            .conflate()
            .map { withContext(io) { store.list(folder) } }
    }

    // ---- T12: mutations (every one calls invalidate()) -----------------------------------------------------------

    suspend fun createUnique(
        folder: FolderRef,
        base: String,
        ext: String,
    ): DocRef =
        withContext(io) {
            val store = storeFor(folder.location)
            val taken = takenNamesLower(store, folder)
            val name = UniqueName.numbered(base, ext, taken)
            store.create(folder, name).also { invalidate() }
        }

    /** Keeps [ref]'s current extension; [folder] is where the (still unique, store-enforced) sibling check runs. */
    suspend fun rename(
        ref: DocRef,
        folder: FolderRef,
        newBase: String,
    ): DocRef =
        withContext(io) {
            val store = storeFor(ref)
            val currentName = nameOf(ref) ?: store.displayName(ref)
            val ext = UniqueName.splitName(currentName).second
            val newDisplayName = if (ext != null) "$newBase.$ext" else newBase
            store.rename(ref, newDisplayName).also { invalidate() }
        }

    /** "<base> copy.<ext>", "<base> copy 2.<ext>" … in [folder]; never auto-named. */
    suspend fun duplicate(
        ref: DocRef,
        folder: FolderRef,
    ): DocRef =
        withContext(io) {
            val store = storeFor(ref)
            val currentName = nameOf(ref) ?: store.displayName(ref)
            val (base, ext) = UniqueName.splitName(currentName)
            val taken = takenNamesLower(store, folder)
            val desired = UniqueName.copyOf(base, ext ?: DEFAULT_EXT, taken)
            val newRef = store.create(folder, desired)
            store.write(newRef, store.read(ref))
            invalidate()
            newRef
        }

    suspend fun move(
        ref: DocRef,
        to: FolderRef,
    ): DocRef =
        withContext(io) {
            storeFor(ref).move(ref, to).also { invalidate() }
        }

    suspend fun createFolder(
        parent: FolderRef,
        name: String,
    ): FolderRef =
        withContext(io) {
            storeFor(parent.location).createFolder(parent, name).also { invalidate() }
        }

    suspend fun trash(ref: DocRef) =
        withContext(io) {
            storeFor(ref).trash(ref).also { invalidate() }
        }

    private suspend fun takenNamesLower(
        store: DocumentStore,
        folder: FolderRef,
    ): Set<String> =
        try {
            store.list(folder).map { it.name.lowercase() }.toHashSet()
        } catch (_: StorageException) {
            emptySet()
        }

    // ---- T12: folder tree (Move… dialog) -------------------------------------------------------------------------

    /** Depth-first, root first (the location root is included so "Move…" can offer it), max depth 8. [name] of the
     * root is left empty; the UI substitutes the localized "On this device" label. */
    suspend fun folderTree(location: LocationId): List<FolderNode> {
        val store = storeFor(location)
        val root = rootOf(location)
        val result = mutableListOf(FolderNode(root, "", 0))

        suspend fun walk(
            folder: FolderRef,
            depth: Int,
        ) {
            if (depth >= MAX_DEPTH) return
            val entries =
                try {
                    store.list(folder)
                } catch (_: StorageException) {
                    return
                }
            for (e in entries) {
                val f = e.folder
                if (e.isFolder && f != null) {
                    result += FolderNode(f, e.name, depth + 1)
                    walk(f, depth + 1)
                }
            }
        }
        withContext(io) { walk(root, 0) }
        return result
    }

    // ---- T12: prefix (search + excerpt fallback), LRU 500 -------------------------------------------------------

    private val prefixMutex = Mutex()
    private val prefixCache =
        object : LinkedHashMap<String, String?>(PREFIX_CACHE_CAPACITY, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>): Boolean =
                size > PREFIX_CACHE_MAX
        }

    /** Decoded head of [entry]'s file (up to [maxChars]), cached by `DocKey + lastModified + size`. Null if the
     * entry has no doc, or it can no longer be read. */
    suspend fun prefix(
        entry: LibraryEntry,
        maxChars: Int = PREFIX_MAX_CHARS,
    ): String? {
        val doc = entry.doc ?: return null
        val cacheKey = "${doc.key().value}|${entry.lastModified}|${entry.size}"
        prefixMutex.withLock { prefixCache[cacheKey] }?.let { return it }
        val text =
            withContext(io) {
                try {
                    val bytes = storeFor(doc).read(doc)
                    NoteFiles.decodeHead(bytes, bytes.size).take(maxChars)
                } catch (_: StorageException) {
                    null
                }
            }
        prefixMutex.withLock { prefixCache[cacheKey] = text }
        return text
    }

    // ---- T12: search ----------------------------------------------------------------------------------------------

    /** Name + content search under [location], starting from its root; name matches sort first, then a limit of
     * [limit] hits. Case-insensitive. Runs entirely on [io]. */
    suspend fun search(
        location: LocationId,
        query: String,
        limit: Int = SEARCH_LIMIT,
    ): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val store = storeFor(location)
        val hits = mutableListOf<SearchHit>()

        suspend fun walk(
            folder: FolderRef,
            path: String,
            depth: Int,
        ) {
            if (hits.size >= limit || depth > MAX_DEPTH) return
            val entries =
                try {
                    store.list(folder)
                } catch (_: StorageException) {
                    return
                }
            for (e in entries) {
                if (hits.size >= limit) return
                if (e.isFolder) {
                    val f = e.folder ?: continue
                    walk(f, if (path.isEmpty()) e.name else "$path/${e.name}", depth + 1)
                } else {
                    val nameMatches = e.name.contains(q, ignoreCase = true)
                    val content = prefix(e)
                    val idx = content?.indexOf(q, ignoreCase = true) ?: -1
                    if (nameMatches || idx >= 0) {
                        val snippet = if (idx >= 0) snippetAround(content!!, idx, q.length) else null
                        hits += SearchHit(e, path, snippet)
                    }
                }
            }
        }
        withContext(io) { walk(rootOf(location), "", 0) }
        return hits.sortedByDescending { it.entry.name.contains(q, ignoreCase = true) }.take(limit)
    }

    private fun snippetAround(
        text: String,
        matchIndex: Int,
        matchLength: Int,
    ): String {
        val start = (matchIndex - SNIPPET_CONTEXT_CHARS).coerceAtLeast(0)
        val end = (matchIndex + matchLength + SNIPPET_CONTEXT_CHARS).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return (prefix + text.substring(start, end) + suffix).replace('\n', ' ').trim()
    }

    private companion object {
        const val MAX_DEPTH = 8
        const val PREFIX_MAX_CHARS = 2048
        const val PREFIX_CACHE_CAPACITY = 32
        const val PREFIX_CACHE_MAX = 500
        const val SEARCH_LIMIT = 200
        const val SNIPPET_CONTEXT_CHARS = 40
        const val DEFAULT_EXT = "md"
    }
}
