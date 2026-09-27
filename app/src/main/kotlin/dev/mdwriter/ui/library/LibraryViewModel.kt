package dev.mdwriter.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mdwriter.data.library.DocKey
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.Excerpt
import dev.mdwriter.data.library.FolderNode
import dev.mdwriter.data.library.FolderRef
import dev.mdwriter.data.library.LibraryEntry
import dev.mdwriter.data.library.LibraryRepository
import dev.mdwriter.data.library.LocationId
import dev.mdwriter.data.library.SearchHit
import dev.mdwriter.data.library.key
import dev.mdwriter.data.settings.PositionStore
import dev.mdwriter.data.settings.Settings
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.settings.SortOrder
import dev.mdwriter.data.storage.NoteFiles
import dev.mdwriter.data.storage.StorageException
import dev.mdwriter.data.storage.userMessage
import dev.mdwriter.ui.editor.DocumentSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Folders first (always name A-Z, case-insensitive), then files by [order]. Pure — no I/O, easy to unit test. */
fun sortEntries(
    entries: List<LibraryEntry>,
    order: SortOrder,
): List<LibraryEntry> {
    val (folders, files) = entries.partition { it.isFolder }
    val sortedFolders = folders.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    val byNameAsc = compareBy(String.CASE_INSENSITIVE_ORDER) { e: LibraryEntry -> e.name }
    val sortedFiles =
        when (order) {
            SortOrder.ModifiedNewestFirst -> files.sortedByDescending { it.lastModified ?: 0L }
            SortOrder.ModifiedOldestFirst -> files.sortedBy { it.lastModified ?: 0L }
            SortOrder.NameAToZ -> files.sortedWith(byNameAsc)
            SortOrder.NameZToA -> files.sortedWith(byNameAsc).asReversed()
        }
    return sortedFolders + sortedFiles
}

/** File-row title: base name, or the full name incl. extension when the setting is on. */
fun displayTitle(
    name: String,
    showExtensions: Boolean,
): String = if (showExtensions) name else NoteFiles.baseName(name)

/**
 * Library drawer state, search, sort, breadcrumb navigation and every file operation (rename / duplicate / move /
 * delete-with-undo / new note / new folder). Stateless UI (`LibraryContent`, `FileRow`, dialogs) only calls back
 * into this.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val positions: PositionStore,
    private val session: DocumentSession,
    private val appScope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : ViewModel() {
    private val crumbs = MutableStateFlow(listOf(Crumb(library.rootOf(LocationId.Internal), "")))
    private val pending = MutableStateFlow<PendingDelete?>(null)

    /** Mirrored by [dev.mdwriter.ui.library.DeleteUndoSnackbarHost]; the undo timer itself lives in this VM. */
    val pendingDelete: StateFlow<PendingDelete?> = pending.asStateFlow()
    private var pendingIdSeq = 0L
    private val query = MutableStateFlow<String?>(null) // null = search inactive
    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = _events.receiveAsFlow()
    private var commitJob: Job? = null

    private fun currentFolder() = crumbs.value.last().folder

    private val entriesFlow =
        crumbs.map { it.last().folder }.distinctUntilChanged().flatMapLatest { library.entries(it) }

    // Blank/no query resolves immediately (no hits, normal listing shows); only a real query is debounced — this
    // also keeps `uiState`'s very first `combine` tuple from stalling on the 250 ms debounce before anyone types.
    private val hitsFlow: Flow<List<SearchHit>?> =
        query.flatMapLatest { q ->
            if (q.isNullOrBlank()) {
                flowOf(null)
            } else {
                flow {
                    delay(SEARCH_DEBOUNCE_MS)
                    emit(
                        withContext(io) {
                            library.search(
                                crumbs.value
                                    .first()
                                    .folder.location,
                                q,
                            )
                        },
                    )
                }
            }
        }

    private data class Raw(
        val entries: List<LibraryEntry>,
        val settings: Settings,
        val pending: PendingDelete?,
        val query: String?,
        val hits: List<SearchHit>?,
    )

    private val rawFlow: Flow<Raw> =
        combine(entriesFlow, settings.settings, pending, query, hitsFlow) { entries, s, p, q, hits ->
            Raw(entries, s, p, q, hits)
        }

    val uiState: StateFlow<LibraryUiState> =
        combine(rawFlow, crumbs, session.current) { raw, crumbList, openRef -> buildState(raw, crumbList, openRef) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    private fun isPending(
        entry: LibraryEntry,
        p: PendingDelete?,
    ): Boolean = p != null && entry.doc?.key() == p.entry.doc?.key()

    private suspend fun buildState(
        raw: Raw,
        crumbList: List<Crumb>,
        openRef: DocRef?,
    ): LibraryUiState.Content {
        val openKey = openRef?.key()
        val hits = raw.hits
        val folderEntries: List<LibraryEntry>
        val fileEntries: List<LibraryEntry>
        val hitByKey: Map<DocKey, SearchHit>
        if (hits != null) {
            folderEntries = emptyList()
            fileEntries = hits.map { it.entry }.filterNot { isPending(it, raw.pending) }
            hitByKey =
                hits
                    .mapNotNull { h ->
                        h.entry.doc
                            ?.key()
                            ?.let { it to h }
                    }.toMap()
        } else {
            val sorted = sortEntries(raw.entries.filterNot { isPending(it, raw.pending) }, raw.settings.sortOrder)
            folderEntries = sorted.filter { it.isFolder }
            fileEntries = sorted.filterNot { it.isFolder }
            hitByKey = emptyMap()
        }
        val fileItems =
            fileEntries.map { entry ->
                val hit = entry.doc?.key()?.let { hitByKey[it] }
                val excerptText = hit?.snippet ?: entry.excerpt ?: library.prefix(entry)?.let(Excerpt::fromPrefix)
                FileItem(
                    entry = entry,
                    title = displayTitle(entry.name, raw.settings.showExtensions),
                    excerpt = excerptText,
                    isOpen = entry.doc?.key() == openKey,
                    folderPath = hit?.folderPath,
                )
            }
        return LibraryUiState.Content(
            locations = listOf(LocationItem(LocationId.Internal, library.rootOf(LocationId.Internal))),
            crumbs = crumbList,
            sortOrder = raw.settings.sortOrder,
            folders = folderEntries,
            files = fileItems,
            searching = raw.query != null,
            query = raw.query.orEmpty(),
            openDocKey = openKey,
            atRoot = crumbList.size <= 1,
        )
    }

    // ---- new note / new folder --------------------------------------------------------------------------------

    fun newNote() =
        viewModelScope.launch {
            val ext = settings.current().newNoteExtension
            val ref = library.createUnique(currentFolder(), "Untitled", ext)
            settings.update { it.copy(autoNamed = it.autoNamed + ref.key().value) }
            session.open(ref, showIme = true)
            _events.send(LibraryEvent.CloseDrawer)
        }

    fun createFolder(name: String) =
        viewModelScope.launch {
            runCatching { library.createFolder(currentFolder(), name) }
                .onFailure { reportError(it) }
        }

    /** For the "Move…" dialog: the whole folder tree of the current location. */
    suspend fun folderTree(): List<FolderNode> = library.folderTree(currentFolder().location)

    // ---- open / navigate ----------------------------------------------------------------------------------------

    fun open(entry: LibraryEntry) =
        viewModelScope.launch {
            val ref = entry.doc ?: return@launch
            if (ref.key() != session.current.value?.key()) {
                session.open(ref, showIme = false)
            }
            _events.send(LibraryEvent.CloseDrawer)
        }

    fun openFolder(entry: LibraryEntry) {
        val folder = entry.folder ?: return
        crumbs.update { it + Crumb(folder, entry.name) }
    }

    fun goTo(crumbIndex: Int) {
        crumbs.update { it.take(crumbIndex + 1) }
    }

    fun up() {
        crumbs.update { if (it.size > 1) it.dropLast(1) else it }
    }

    /** T13's folder-up back handler — same as [up]; named to match the back-ordering contract (01 §6.4 / §H). */
    fun navigateUp() = up()

    // ---- search / sort --------------------------------------------------------------------------------------------

    fun startSearch() {
        query.value = ""
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun closeSearch() {
        query.value = null
    }

    /** T13's search-clear back handler — same as [closeSearch]; named to match the back-ordering contract (01 §6.4
     * / §H). */
    fun clearSearch() = closeSearch()

    fun setSort(order: SortOrder) =
        viewModelScope.launch {
            settings.update { it.copy(sortOrder = order) }
        }

    // ---- rename / duplicate / move --------------------------------------------------------------------------------

    fun rename(
        entry: LibraryEntry,
        newBase: String,
    ) = viewModelScope.launch {
        val ref = entry.doc ?: return@launch
        val folder = library.parentOf(ref) ?: currentFolder()
        val isOpen = ref.key() == session.current.value?.key()
        if (isOpen) session.flush()
        runCatching { library.rename(ref, folder, newBase) }
            .onSuccess { newRef ->
                settings.update { it.copy(autoNamed = it.autoNamed - ref.key().value) }
                positions.move(ref.key(), newRef.key())
                if (isOpen) session.onCurrentRefChanged(ref, newRef)
            }.onFailure { reportError(it) }
    }

    fun duplicate(entry: LibraryEntry) =
        viewModelScope.launch {
            val ref = entry.doc ?: return@launch
            val folder = library.parentOf(ref) ?: currentFolder()
            if (ref.key() == session.current.value?.key()) session.flush()
            runCatching { library.duplicate(ref, folder) }.onFailure { reportError(it) }
        }

    fun move(
        entry: LibraryEntry,
        target: FolderRef,
    ) = viewModelScope.launch {
        val ref = entry.doc ?: return@launch
        val isOpen = ref.key() == session.current.value?.key()
        if (isOpen) session.flush()
        runCatching { library.move(ref, target) }
            .onSuccess { newRef ->
                positions.move(ref.key(), newRef.key())
                if (isOpen) session.onCurrentRefChanged(ref, newRef)
            }.onFailure { reportError(it) }
    }

    // ---- delete / undo --------------------------------------------------------------------------------------------

    fun delete(entry: LibraryEntry) =
        viewModelScope.launch {
            val ref = entry.doc ?: return@launch
            commitPendingNow() // a second delete commits the first (platform §2.7)
            pendingIdSeq++
            pending.value = PendingDelete(entry, pendingIdSeq) // hidden from the list immediately
            if (ref.key() == session.current.value?.key()) { // deleting the OPEN doc
                session.flush()
                val state = uiState.value
                val next =
                    (if (state is LibraryUiState.Content) state.files.map { it.entry } else emptyList())
                        .filter { it.doc?.key() != ref.key() }
                        .maxByOrNull { it.lastModified ?: 0L }
                        ?.doc
                if (next != null) {
                    session.open(next, showIme = false, leaveCurrent = false)
                } else {
                    val ext = settings.current().newNoteExtension
                    val newRef = library.createUnique(currentFolder(), "Untitled", ext)
                    settings.update { it.copy(autoNamed = it.autoNamed + newRef.key().value) }
                    session.open(newRef, showIme = false, leaveCurrent = false)
                }
            }
            commitJob =
                viewModelScope.launch {
                    delay(UNDO_WINDOW_MS)
                    commitPendingNow()
                }
        }

    fun undoDelete() {
        commitJob?.cancel()
        commitJob = null
        pending.value = null
    }

    /** Snackbar timeout, a second delete, or ON_STOP. Runs in [appScope] so a finishing activity cannot drop it. */
    fun commitPendingNow() {
        commitJob?.cancel()
        commitJob = null
        val p = pending.getAndUpdate { null } ?: return
        val ref = p.entry.doc ?: return
        appScope.launch {
            withContext(NonCancellable) {
                runCatching {
                    library.trash(ref)
                    settings.update { it.copy(autoNamed = it.autoNamed - ref.key().value) }
                    positions.remove(ref.key())
                }
            }
        }
    }

    // ---- lifecycle hooks -------------------------------------------------------------------------------------------

    fun onDrawerOpened() {
        library.invalidate()
    }

    fun onDrawerClosed() {
        closeSearch()
    }

    fun onStop() {
        commitPendingNow()
    }

    private fun reportError(t: Throwable) {
        viewModelScope.launch {
            val message = (t as? StorageException)?.error?.userMessage() ?: (t.message ?: "Something went wrong")
            _events.send(LibraryEvent.Message(message))
        }
    }

    companion object {
        const val UNDO_WINDOW_MS = 5_000L
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
