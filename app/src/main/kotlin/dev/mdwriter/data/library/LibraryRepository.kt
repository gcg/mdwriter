package dev.mdwriter.data.library

import android.net.Uri
import android.provider.DocumentsContract
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.storage.DocumentStore
import dev.mdwriter.data.storage.NoteFiles
import dev.mdwriter.data.storage.SafTreeStore
import dev.mdwriter.data.storage.StorageError
import dev.mdwriter.data.storage.StorageException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

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
    /** T14: builds a fresh [SafTreeStore] (or a fake, in tests) for a tree URI string. Never called twice for the
     * same tree — [LibraryRepository] caches one instance per tree ([treeStore]). */
    private val treeStoreFactory: (String) -> DocumentStore = { treeUriString ->
        throw StorageException(
            StorageError.PermissionLost,
            IllegalStateException("no tree store factory: $treeUriString"),
        )
    },
    /** T14: null in tests that never touch a real grant (they can pre-seed [locations] via [seedLocationsForTest]
     * or simply never call [linkTree]/[revalidate]). */
    private val treeGrants: TreeGrants? = null,
) {
    private val treeStores = ConcurrentHashMap<String, DocumentStore>()
    private val treeNames = ConcurrentHashMap<String, String>()

    private fun treeStore(treeUriString: String): DocumentStore =
        treeStores.getOrPut(treeUriString) {
            treeStoreFactory(treeUriString)
        }

    private val _locations =
        MutableStateFlow(listOf(LocationInfo(LocationId.Internal, "", LocationState.Ready)))

    /** Internal first, then linked trees in [dev.mdwriter.data.settings.Settings.linkedTrees] order. Populated by
     * [revalidate] (called on every `ON_START`) and refreshed by every link/unlink/reconnect. */
    val locations: StateFlow<List<LocationInfo>> = _locations.asStateFlow()

    private fun rootDocRef(treeUriString: String): DocRef.TreeDoc =
        DocRef.TreeDoc(treeUriString, DocumentsContract.getTreeDocumentId(Uri.parse(treeUriString)))

    private fun guessNameFromUri(uri: Uri): String {
        val last = uri.lastPathSegment ?: return uri.toString()
        val afterColon = Uri.decode(last.substringAfterLast(':'))
        return afterColon.substringAfterLast('/').ifEmpty { afterColon }
    }

    private fun nameForTree(
        treeUriString: String,
        uri: Uri?,
    ): String = treeNames[treeUriString] ?: uri?.let(::guessNameFromUri) ?: treeUriString

    fun storeFor(ref: DocRef): DocumentStore =
        when (ref) {
            is DocRef.InternalFile -> internalStore
            is DocRef.TreeDoc -> storeForTreeChecked(ref.treeUri)
            is DocRef.External -> throw StorageException(StorageError.NotFound)
        }

    fun storeFor(location: LocationId): DocumentStore =
        when (location) {
            LocationId.Internal -> internalStore
            is LocationId.Tree -> storeForTreeChecked(location.treeUri)
        }

    private fun storeForTreeChecked(treeUriString: String): DocumentStore {
        val info = _locations.value.firstOrNull { it.id == LocationId.Tree(treeUriString) }
        if (info != null &&
            info.state == LocationState.Disconnected
        ) {
            throw StorageException(StorageError.PermissionLost)
        }
        return treeStore(treeUriString)
    }

    fun rootOf(location: LocationId): FolderRef =
        when (location) {
            LocationId.Internal -> FolderRef(location, "")
            is LocationId.Tree -> FolderRef(location, rootDocRef(location.treeUri).documentId)
        }

    suspend fun parentOf(ref: DocRef): FolderRef? =
        when (ref) {
            is DocRef.InternalFile -> FolderRef(LocationId.Internal, ref.relPath.substringBeforeLast('/', ""))
            is DocRef.TreeDoc -> (storeFor(ref) as? SafTreeStore)?.parentFolder(ref.documentId)
            is DocRef.External -> null
        }

    /** Caps of a FOLDER itself (not a listed [LibraryEntry] — used to gate "new note"/"new folder" on the current
     * folder, 02 §7 / T14 step 9). Always [EntryCaps.ALL] for [LocationId.Internal]. */
    suspend fun capsOf(folder: FolderRef): EntryCaps =
        when (folder.location) {
            LocationId.Internal -> EntryCaps.ALL
            is LocationId.Tree -> (storeFor(folder.location) as? SafTreeStore)?.folderCaps(folder.id) ?: EntryCaps.ALL
        }

    // ---- T14: linking ----------------------------------------------------------------------------------------------

    /** Takes a persistable grant, remembers the tree (order-preserving; a no-op if already linked), and returns its
     * freshly-[revalidate]d [LocationInfo]. */
    suspend fun linkTree(treeUri: Uri): LocationInfo =
        withContext(io) {
            val treeUriString = treeUri.toString()
            if (treeUriString !in settings.current().linkedTrees) {
                requireNotNull(treeGrants) { "linkTree needs a TreeGrants" }.take(treeUri)
                val name = runCatching { treeGrants.rootName(treeUri) }.getOrElse { guessNameFromUri(treeUri) }
                treeNames[treeUriString] = name
                settings.update { it.copy(linkedTrees = it.linkedTrees + treeUriString) }
            }
            revalidate()
            _locations.value.first { it.id == LocationId.Tree(treeUriString) }
        }

    /** Forgets [id]: drops it from settings, releases the grant, and drops its cached store — never touches any
     * file. If the currently-open document lives in [id], the caller (`EditorViewModel`) is responsible for its own
     * fallback; this only fixes up `settings.lastOpenDoc`. */
    suspend fun unlinkTree(id: LocationId.Tree) =
        withContext(io) {
            settings.update { it.copy(linkedTrees = it.linkedTrees - id.treeUri) }
            runCatching { Uri.parse(id.treeUri) }.getOrNull()?.let { treeGrants?.release(it) }
            treeStores.remove(id.treeUri)
            treeNames.remove(id.treeUri)
            val cur = settings.current()
            if (cur.lastOpenDoc?.toRef()?.location == id) settings.update { it.copy(lastOpenDoc = null) }
            invalidate()
            revalidate()
        }

    /** Re-picks a folder for a Disconnected (or still-Ready) tree. The same URI just re-takes the grant; a
     * DIFFERENT URI replaces it at the same position in [dev.mdwriter.data.settings.Settings.linkedTrees] and
     * releases the old grant (if still held). */
    suspend fun reconnect(
        old: LocationId.Tree,
        picked: Uri,
    ): LocationInfo =
        withContext(io) {
            val grants = requireNotNull(treeGrants) { "reconnect needs a TreeGrants" }
            val pickedString = picked.toString()
            grants.take(picked)
            if (pickedString != old.treeUri) {
                val order = settings.current().linkedTrees
                val idx = order.indexOf(old.treeUri)
                runCatching { Uri.parse(old.treeUri) }.getOrNull()?.let { oldUri ->
                    if (grants.isGranted(oldUri)) grants.release(oldUri)
                }
                treeStores.remove(old.treeUri)
                treeNames.remove(old.treeUri)
                settings.update {
                    val list = it.linkedTrees.toMutableList()
                    if (idx >= 0) list[idx] = pickedString else list.add(pickedString)
                    it.copy(linkedTrees = list)
                }
            }
            val name = runCatching { grants.rootName(picked) }.getOrElse { guessNameFromUri(picked) }
            treeNames[pickedString] = name
            invalidate()
            revalidate()
            _locations.value.first { it.id == LocationId.Tree(pickedString) }
        }

    /** A tree is Ready iff its grant is still held AND its root document can be `stat`ted; never auto-unlinked
     * (platform §2.13: a backup/restore loses grants, showing every linked tree as Disconnected — not gone). Call
     * on every `ON_START`. */
    suspend fun revalidate() =
        withContext(io) {
            val order = settings.current().linkedTrees
            val updated = mutableListOf(LocationInfo(LocationId.Internal, "", LocationState.Ready))
            for (treeUriString in order) {
                val uri = runCatching { Uri.parse(treeUriString) }.getOrNull()
                var ready = false
                if (uri != null && treeGrants?.isGranted(uri) == true) {
                    val stat = runCatching { treeStore(treeUriString).stat(rootDocRef(treeUriString)) }.getOrNull()
                    if (stat != null) {
                        ready = true
                        runCatching { treeGrants.rootName(uri) }.getOrNull()?.let { treeNames[treeUriString] = it }
                    }
                }
                val name = nameForTree(treeUriString, uri)
                updated +=
                    LocationInfo(
                        LocationId.Tree(treeUriString),
                        name,
                        if (ready) LocationState.Ready else LocationState.Disconnected,
                    )
            }
            _locations.value = updated
        }

    /** Test-only seam: lets a test populate [locations] without a real [TreeGrants]/[android.content.ContentResolver]. */
    fun seedLocationsForTest(locations: List<LocationInfo>) {
        _locations.value = locations
        for (l in locations) if (l.id is LocationId.Tree) treeNames[l.id.treeUri] = l.name
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
            val same = ref.location != null && ref.location == to.location
            if (same) {
                storeFor(ref).move(ref, to).also { invalidate() }
            } else {
                crossLocationMove(ref, to).also { invalidate() }
            }
        }

    /** Different [LocationId]s (e.g. Internal -> a linked Tree, or Tree -> Tree): copy the bytes, verify the size,
     * THEN trash the source — the source is only ever removed after a successful, size-verified write. On any
     * failure after the new document was created, it is trashed and the original exception rethrown; the source
     * is never touched in that case. */
    private suspend fun crossLocationMove(
        ref: DocRef,
        to: FolderRef,
    ): DocRef {
        val src = storeFor(ref)
        val dst = storeFor(to.location)
        val bytes = src.read(ref)
        val name = nameOf(ref) ?: src.displayName(ref)
        val taken = takenNamesLower(dst, to)
        val uniqueName = NoteFiles.uniqueName(name, taken)
        val newRef = dst.create(to, uniqueName)
        try {
            dst.write(newRef, bytes)
            val size = dst.stat(newRef)?.size
            if (size != null && size != bytes.size.toLong()) {
                throw StorageException(
                    StorageError.ProviderFailure(IOException("size mismatch after cross-location move")),
                )
            }
        } catch (t: Throwable) {
            runCatching { dst.trash(newRef) }
            throw t
        }
        src.trash(ref)
        return newRef
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
