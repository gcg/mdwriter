package dev.mdwriter.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.mdwriter.MdWriterApp
import dev.mdwriter.data.document.AutosaveCoordinator
import dev.mdwriter.data.document.AutosaveTarget
import dev.mdwriter.data.document.ConflictNames
import dev.mdwriter.data.document.DocumentRepository
import dev.mdwriter.data.document.ExternalCheck
import dev.mdwriter.data.document.SaveResult
import dev.mdwriter.data.document.SaveState
import dev.mdwriter.data.document.Snapshot
import dev.mdwriter.data.document.WelcomeNote
import dev.mdwriter.data.library.AutoNamer
import dev.mdwriter.data.library.DocKey
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.FolderRef
import dev.mdwriter.data.library.LeaveOutcome
import dev.mdwriter.data.library.LeaveReason
import dev.mdwriter.data.library.LibraryRepository
import dev.mdwriter.data.library.LocationId
import dev.mdwriter.data.library.fileName
import dev.mdwriter.data.library.key
import dev.mdwriter.data.library.toRef
import dev.mdwriter.data.settings.Position
import dev.mdwriter.data.settings.PositionStore
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.storage.FileStat
import dev.mdwriter.data.storage.NoteFiles
import dev.mdwriter.data.storage.RecoveryStore
import dev.mdwriter.data.storage.StorageError
import dev.mdwriter.data.storage.StorageException
import dev.mdwriter.data.storage.StorageLimits
import dev.mdwriter.data.storage.TextCodec
import dev.mdwriter.data.storage.TextFormat
import dev.mdwriter.data.storage.userMessage
import dev.mdwriter.editor.FocusModeKind
import dev.mdwriter.editor.InstallRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** What [EditorScreen] binds while it hosts a live [dev.mdwriter.editor.EditorController]. Main thread only.
 * [EditorViewModel.unbindEditor] caches a final [Snapshot] so a pending save never touches a disposed view. */
interface EditorBinding {
    fun snapshot(): Snapshot

    fun caret(): Int

    fun scrollY(): Int
}

/** Mutable metadata for the currently-open document; kept in sync with the disk after every successful save
 * ([EditorViewModel.persist]) or reload. */
private class Session(
    var ref: DocRef,
    var baseline: FileStat,
    var format: TextFormat,
    var readOnly: Boolean,
    var displayName: String,
)

/**
 * The document session (01 §5/§6.3/§6.4): opens the right document on start, keeps it saved, restores it after
 * process death, and reacts to external changes. No View reference lives here (01 §5) — only [EditorBinding].
 *
 * Implements [AutosaveTarget] directly ("the coordinator never sees documents: it calls an AutosaveTarget
 * implemented by EditorViewModel", 01 §6.3) — [snapshot]/[persist] read the live [session] field, so the SAME
 * instance stays registered with [autosave] across saves of the same document.
 */
class EditorViewModel(
    private val documents: DocumentRepository,
    private val library: LibraryRepository,
    private val autosave: AutosaveCoordinator,
    private val settings: SettingsRepository,
    private val positions: PositionStore,
    private val recovery: RecoveryStore,
    private val autoNamer: AutoNamer,
    private val appScope: CoroutineScope,
    private val main: CoroutineDispatcher,
    private val handle: SavedStateHandle,
    private val clock: () -> Long,
) : ViewModel(),
    AutosaveTarget,
    DocumentSession {
    private var started = false

    private var session: Session? = null

    /** Set right before every `Install` event this VM sends; consumed by [onInstalled]. */
    private var pendingBeginDirty = false

    private var binding: EditorBinding? = null
    private var lastSnapshot: Snapshot? = null

    /** True between `onStart()`/`onStop()` (STARTED lifecycle state) — gates [restartTreeWatch] (T14). */
    private var isForeground = false

    /** T14: live external-change watch for a `TreeDoc` while it is open AND the activity is STARTED — debounced
     * 300 ms, re-checks via [checkExternalNow] on every notification (folder content-observer or our own local
     * mutation). Restarted on every install/re-point; always cancelled on `onStop()`. */
    private var treeChangesJob: Job? = null

    private val uiInternal = MutableStateFlow(EditorUiState.INITIAL)

    private val _current = MutableStateFlow<DocRef?>(null)

    /** [DocumentSession.current]: the currently-open document, kept in sync on every install/rename/move. */
    override val current: StateFlow<DocRef?> = _current.asStateFlow()

    /** T13: the two floating chrome glyphs' fade state machine — driven by `EditorScreen`'s edit/IME/scroll/tap
     * signals, combined into [uiState.chromeVisible][EditorUiState.chromeVisible]. */
    val chrome = ChromeVisibility(viewModelScope)

    val uiState: StateFlow<EditorUiState> =
        combine(uiInternal, autosave.state, chrome.visible) { u, s, chromeVisible ->
            u.copy(save = s, chromeVisible = chromeVisible)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState.INITIAL)

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    // ---- AutosaveTarget --------------------------------------------------------------------------------------

    override val ref: DocRef
        get() = session?.ref ?: DocRef.InternalFile("")

    /** Hops to Main to read the live editor ([EditorBinding.snapshot] is a plain, synchronous, main-thread call);
     * falls back to the cached [lastSnapshot] once the view has been unbound (01 §5). */
    override suspend fun snapshot(): Snapshot {
        val fromBinding = withContext(main) { binding?.snapshot() }
        if (fromBinding != null) {
            lastSnapshot = fromBinding
            return fromBinding
        }
        return lastSnapshot ?: Snapshot(0L, "")
    }

    override suspend fun persist(s: Snapshot): SaveResult {
        val sess = session ?: return SaveResult.Failed(StorageError.NotFound)
        val result = documents.save(sess.ref, s.text, sess.baseline, sess.format)
        when (result) {
            is SaveResult.Saved -> {
                sess.baseline = result.newBaseline
            }

            is SaveResult.Conflict -> {
                val disk =
                    try {
                        documents.load(sess.ref, useRecovery = false)
                    } catch (_: StorageException) {
                        null
                    }
                uiInternal.update {
                    it.copy(conflict = ConflictState.ChangedOnDisk(disk?.text ?: "", disk?.baseline ?: result.onDisk))
                }
            }

            is SaveResult.Failed -> {
                if (result.error == StorageError.NotFound) {
                    uiInternal.update { it.copy(conflict = ConflictState.Gone) }
                }
            }
        }
        return result
    }

    // ---- lifecycle: editor binding ---------------------------------------------------------------------------

    /** Called from `EditorScreen`'s `DisposableEffect` once [dev.mdwriter.editor.EditorController] exists. */
    fun bindEditor(b: EditorBinding) {
        binding = b
    }

    /** `onDispose`: cache a final snapshot so a pending/future save never touches the disposed view. */
    fun unbindEditor() {
        binding?.let { lastSnapshot = it.snapshot() }
        binding = null
    }

    // ---- start-up ---------------------------------------------------------------------------------------------

    /** Idempotent. Tries, in order: (1) process-death restore from [handle]; (2) first launch -> create+open
     * [WelcomeNote]; (3) [openFallbackChain] (last open doc / newest / a brand new note). */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            val savedRef = handle.get<String>(KEY_DOC_KEY)?.let { DocKey(it).toRef() }
            if (savedRef != null) {
                installAndOpen(
                    savedRef,
                    showIme = false,
                    selection = handle.get<Int>(KEY_SEL_START) ?: 0,
                    scrollY = handle.get<Int>(KEY_SCROLL_Y) ?: 0,
                )
                return@launch
            }
            if (!settings.current().welcomeCreated && library.newestDoc() == null) {
                createWelcomeNote()
                return@launch
            }
            openFallbackChain()
        }
    }

    private suspend fun createWelcomeNote() {
        val store = library.storeFor(LocationId.Internal)
        val ref = store.create(FolderRef.INTERNAL_ROOT, WelcomeNote.FILE_NAME)
        store.write(ref, TextCodec.encode(WelcomeNote.TEXT, TextFormat.DEFAULT))
        settings.update { it.copy(welcomeCreated = true) }
        installAndOpen(ref, showIme = false, selection = WelcomeNote.TEXT.length)
    }

    /** Steps 3-5 of [start] ("last open doc at its remembered position" / "newest doc" / "a brand new note"); also
     * used by [resolveConflict]'s `Close` action. Flushes whatever was open before (a no-op at start-up, when
     * nothing is). */
    private suspend fun openFallbackChain() {
        flushAndRememberPrevious()
        val current = settings.current()
        val lastKey = current.lastOpenDoc
        val lastRef = lastKey?.toRef()
        val lastStat =
            lastRef?.let { ref ->
                try {
                    library.storeFor(ref).stat(ref)
                } catch (_: StorageException) {
                    null
                }
            }
        if (lastRef != null && lastStat != null) {
            val pos = positions.get(lastKey)
            installAndOpen(lastRef, showIme = false, selection = pos?.caret, scrollY = pos?.scrollY)
            return
        }
        val newest = library.newestDoc()
        if (newest != null) {
            installAndOpen(newest, showIme = false)
            return
        }
        createNewNoteAndOpen()
    }

    // ---- opening a document -------------------------------------------------------------------------------------

    fun newNote() {
        viewModelScope.launch { createNewNoteAndOpen() }
    }

    /** Flushes whatever was open before (a no-op when nothing was — both call sites of [openFallbackChain]/[start]
     * hit that case; a real switch away from an open doc is already flushed by its own caller). */
    private suspend fun createNewNoteAndOpen() {
        flushAndRememberPrevious()
        val ext = settings.current().newNoteExtension
        val store = library.storeFor(LocationId.Internal)
        val ref = store.create(FolderRef.INTERNAL_ROOT, "Untitled.$ext")
        settings.update { it.copy(autoNamed = it.autoNamed + ref.key().value) }
        installAndOpen(ref, showIme = true)
    }

    // ---- DocumentSession -------------------------------------------------------------------------------------------

    /** The library asked to open [ref]: flush the current doc, optionally auto-name/delete-if-empty it (never for
     * a document being deleted — [leaveCurrent] = false), then install [ref] at its remembered position. */
    override suspend fun open(
        ref: DocRef,
        showIme: Boolean,
        leaveCurrent: Boolean,
    ) {
        val prevRef = session?.ref
        if (prevRef != null) {
            flushAndRememberPrevious()
            if (leaveCurrent) autoNamer.onLeave(prevRef, LeaveReason.Switch)
        }
        val pos = positions.get(ref.key())
        installAndOpen(ref, showIme, selection = pos?.caret, scrollY = pos?.scrollY)
    }

    override suspend fun flush() {
        val snap = binding?.snapshot() ?: lastSnapshot
        if (snap != null) autosave.flush(snap) else autosave.flush()
    }

    /** The library renamed/moved the currently-open document: re-point everything T11 keys by [DocRef] (01 §6.3). */
    override fun onCurrentRefChanged(
        old: DocRef,
        new: DocRef,
    ) {
        val sess = session ?: return
        sess.ref = new
        sess.displayName = library.nameOf(new) ?: sess.displayName
        _current.value = new
        uiInternal.update { it.copy(doc = new, title = NoteFiles.baseName(sess.displayName)) }
        handle[KEY_DOC_KEY] = new.key().value
        restartTreeWatch()
        viewModelScope.launch {
            val oldKey = old.key()
            val newKey = new.key()
            val copy = recovery.read(oldKey)
            if (copy != null) {
                recovery.write(newKey, copy.text)
                recovery.delete(oldKey)
            }
            settings.update { if (it.lastOpenDoc == oldKey) it.copy(lastOpenDoc = newKey) else it }
        }
    }

    /** T13: mirrors `MdWriterRoot`'s `drawerState` (modal/compact mode only — the permanent pane in expanded mode
     * is tracked by `accepts(dir)` instead, never this flag) into [EditorUiState.drawerOpen], which gates the
     * swipe-nav `enabled()` check. */
    fun setDrawerOpen(open: Boolean) {
        uiInternal.update { it.copy(drawerOpen = open) }
    }

    /** T17 extends this; for T13 it only ever clears the (always-false) [EditorUiState.findOpen] flag, part of the
     * back-ordering contract (01 §6.4 / §H): `BackHandler(enabled = ui.findOpen) { editorVm.closeFind() }`. */
    fun closeFind() {
        uiInternal.update { it.copy(findOpen = false) }
    }

    // ---- T15: Focus Mode / typewriter / word count settings, and the stats pipeline's result -------------------

    fun setFocusMode(mode: FocusModeKind) {
        viewModelScope.launch { settings.update { it.copy(focusMode = mode) } }
    }

    fun setTypewriter(on: Boolean) {
        viewModelScope.launch { settings.update { it.copy(typewriter = on) } }
    }

    fun setWordCount(on: Boolean) {
        viewModelScope.launch { settings.update { it.copy(wordCount = on) } }
    }

    /** `null` when Word count is off, or [StatsPipeline] hasn't produced a result yet. */
    fun onStats(d: DisplayStats?) {
        uiInternal.update { it.copy(stats = d?.stats, statsSelection = d?.isSelection ?: false) }
    }

    /** Called by `MdWriterRoot` right before the drawer opens: flush, then auto-name the current doc (never
     * delete-if-empty — the user is still "in" it, per the T12 pitfalls). */
    fun onDrawerOpened() {
        viewModelScope.launch {
            val ref = session?.ref ?: return@launch
            flush()
            when (val outcome = autoNamer.onLeave(ref, LeaveReason.DrawerOpened)) {
                is LeaveOutcome.Renamed -> {
                    onCurrentRefChanged(ref, outcome.newRef)
                }

                else -> {}
            }
        }
    }

    private suspend fun installAndOpen(
        ref: DocRef,
        showIme: Boolean,
        selection: Int? = null,
        scrollY: Int? = null,
    ) {
        val loaded =
            try {
                documents.load(ref)
            } catch (e: StorageException) {
                _events.send(EditorEvent.Message(errorMessageFor(ref, e.error)))
                return
            }
        session = Session(loaded.ref, loaded.baseline, loaded.format, loaded.readOnly, loaded.displayName)
        _current.value = loaded.ref
        val key = loaded.ref.key()
        uiInternal.update {
            it.copy(
                doc = loaded.ref,
                title = NoteFiles.baseName(loaded.displayName),
                loading = false,
                readOnly = loaded.readOnly,
                conflict =
                    loaded.diskTextIfConflict?.let { diskText ->
                        ConflictState.ChangedOnDisk(diskText, loaded.baseline)
                    },
            )
        }
        settings.update { it.copy(lastOpenDoc = key) }
        handle[KEY_DOC_KEY] = key.value
        val len = loaded.text.length
        val sel = (selection ?: len).coerceIn(0, len)
        val scroll = scrollY ?: 0
        pendingBeginDirty = loaded.recovered
        restartTreeWatch()
        _events.send(EditorEvent.Install(InstallRequest(loaded.text, sel, scroll, loaded.readOnly)))
        _events.send(EditorEvent.AfterOpen(showIme))
        if (loaded.large) _events.send(EditorEvent.Message("Large document — styling may be slower"))
        if (loaded.readOnly && (loaded.baseline.size ?: 0L) > StorageLimits.READ_ONLY_BYTES) {
            _events.send(EditorEvent.Message("Opened read-only (over 5 MB)"))
        }
    }

    private fun errorMessageFor(
        ref: DocRef,
        error: StorageError,
    ): String =
        if (error == StorageError.Encoding) {
            val name = (ref as? DocRef.InternalFile)?.fileName ?: "This file"
            "'$name' is not a text file"
        } else {
            error.userMessage()
        }

    /** Flushes+forgets the document we're switching away from (if any): a pending save must never be lost when
     * the user opens a different note. */
    private suspend fun flushAndRememberPrevious() {
        val prev = session ?: return
        val snap = binding?.snapshot() ?: lastSnapshot
        autosave.flush(snap)
        autosave.end()
        val caret = binding?.caret() ?: 0
        val scrollY = binding?.scrollY() ?: 0
        positions.put(prev.ref.key(), Position(caret, scrollY))
    }

    // ---- install / edit / lifecycle hooks (called from EditorScreen) -------------------------------------------

    /** Called after every `Install` event actually lands in the controller (`EditorScreen`'s wiring). */
    fun onInstalled(version: Long) {
        val s = session ?: return
        if (s.readOnly) return
        autosave.begin(this, version, dirty = pendingBeginDirty)
        pendingBeginDirty = false
    }

    fun onEdit(version: Long) {
        autosave.onEdit(version)
    }

    fun onStart() {
        isForeground = true
        viewModelScope.launch { checkExternalNow() }
        restartTreeWatch()
    }

    /** The actual external-change check (mtime+size, or size+hash for a null-mtime provider — T11's
     * `DocumentRepository.checkExternal`): silent reload if clean, conflict banner if dirty, `Gone` if deleted.
     * Called from [onStart] and, for a `TreeDoc`, whenever its folder reports a change (T14). */
    private suspend fun checkExternalNow() {
        val sess = session ?: return
        when (val check = documents.checkExternal(sess.ref, sess.baseline)) {
            ExternalCheck.Unchanged -> {}

            is ExternalCheck.Changed -> {
                if (autosave.state.value is SaveState.Clean) {
                    sess.baseline = check.disk.baseline
                    sess.format = check.disk.format
                    sess.readOnly = check.disk.readOnly
                    sess.displayName = check.disk.displayName
                    val len = check.disk.text.length
                    val caret = (binding?.caret() ?: 0).coerceIn(0, len)
                    val scrollY = binding?.scrollY() ?: 0
                    pendingBeginDirty = false
                    uiInternal.update {
                        it.copy(readOnly = check.disk.readOnly, title = NoteFiles.baseName(check.disk.displayName))
                    }
                    _events.send(
                        EditorEvent.Install(InstallRequest(check.disk.text, caret, scrollY, check.disk.readOnly)),
                    )
                } else {
                    uiInternal.update {
                        it.copy(
                            conflict = ConflictState.ChangedOnDisk(check.disk.text, check.disk.baseline),
                        )
                    }
                }
            }

            ExternalCheck.Gone -> {
                uiInternal.update { it.copy(conflict = ConflictState.Gone) }
            }
        }
    }

    /** (Re)starts the T14 tree-changes watch for whatever is currently open, if we're STARTED and it's a
     * `TreeDoc`; otherwise cancels any running watch. Safe to call unconditionally after every install/re-point. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun restartTreeWatch() {
        treeChangesJob?.cancel()
        treeChangesJob = null
        if (!isForeground) return
        val ref = session?.ref as? DocRef.TreeDoc ?: return
        treeChangesJob =
            viewModelScope.launch {
                val parent = runCatching { library.parentOf(ref) }.getOrNull() ?: return@launch
                val store = runCatching { library.storeFor(ref) }.getOrNull() ?: return@launch
                store
                    .changes(parent)
                    .debounce(TREE_WATCH_DEBOUNCE_MS)
                    .collect { checkExternalNow() }
            }
    }

    fun onStop() {
        isForeground = false
        treeChangesJob?.cancel()
        treeChangesJob = null
        val snap = binding?.snapshot() ?: lastSnapshot
        val caret = binding?.caret() ?: (handle.get<Int>(KEY_SEL_START) ?: 0)
        val scrollY = binding?.scrollY() ?: (handle.get<Int>(KEY_SCROLL_Y) ?: 0)
        handle[KEY_SEL_START] = caret
        handle[KEY_SCROLL_Y] = scrollY
        val ref = session?.ref
        val key = ref?.key()
        appScope.launch {
            withContext(NonCancellable) {
                if (snap != null) autosave.flush(snap) else autosave.flush()
                if (key != null) positions.put(key, Position(caret, scrollY))
                // Auto-name only (never delete-if-empty on ON_STOP — the user is still "in" this note, T12 pitfalls).
                if (ref != null) {
                    when (val outcome = autoNamer.onLeave(ref, LeaveReason.Stopped)) {
                        is LeaveOutcome.Renamed -> {
                            onCurrentRefChanged(ref, outcome.newRef)
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    // ---- conflict resolution ------------------------------------------------------------------------------------

    fun resolveConflict(action: ConflictAction) {
        viewModelScope.launch {
            val conflict = uiInternal.value.conflict
            uiInternal.update { it.copy(conflict = null) }
            val sess = session
            when (action) {
                ConflictAction.Reload -> {
                    performReload()
                }

                ConflictAction.KeepMine -> {
                    if (sess != null && conflict is ConflictState.ChangedOnDisk) sess.baseline = conflict.diskBaseline
                    autosave.resume()
                    autosave.flush()
                }

                ConflictAction.SaveBoth -> {
                    if (sess != null && conflict is ConflictState.ChangedOnDisk) saveBufferAsConflictCopy(sess)
                    performReload()
                }

                ConflictAction.SaveAsNew -> {
                    if (sess != null) saveBufferAsNewAndOpen(sess)
                }

                ConflictAction.Close -> {
                    openFallbackChain()
                }
            }
        }
    }

    private suspend fun performReload() {
        val sess = session ?: return
        recovery.delete(sess.ref.key())
        val fresh = documents.load(sess.ref, useRecovery = false)
        sess.baseline = fresh.baseline
        sess.format = fresh.format
        sess.readOnly = fresh.readOnly
        sess.displayName = fresh.displayName
        val len = fresh.text.length
        val caret = (binding?.caret() ?: 0).coerceIn(0, len)
        val scrollY = binding?.scrollY() ?: 0
        pendingBeginDirty = false
        uiInternal.update { it.copy(readOnly = fresh.readOnly, title = NoteFiles.baseName(fresh.displayName)) }
        _events.send(EditorEvent.Install(InstallRequest(fresh.text, caret, scrollY, fresh.readOnly)))
    }

    private suspend fun saveBufferAsConflictCopy(sess: Session) {
        val snap = binding?.snapshot() ?: lastSnapshot ?: return
        val base = NoteFiles.baseName(sess.displayName)
        val ext = NoteFiles.extensionOf(sess.displayName)
        val parent = library.parentOf(sess.ref) ?: library.rootOf(LocationId.Internal)
        val store = library.storeFor(parent.location)
        val desired = ConflictNames.name(base, ext, nowAsLocalDateTime())
        val newRef = store.create(parent, desired)
        store.write(newRef, TextCodec.encode(snap.text, sess.format))
        _events.send(EditorEvent.Message("Saved your version as '${store.displayName(newRef)}'"))
    }

    private suspend fun saveBufferAsNewAndOpen(sess: Session) {
        val snap = binding?.snapshot() ?: lastSnapshot ?: return
        val base = NoteFiles.baseName(sess.displayName)
        val ext = NoteFiles.extensionOf(sess.displayName)
        val parent = library.parentOf(sess.ref) ?: library.rootOf(LocationId.Internal)
        val store = library.storeFor(parent.location)
        val desired = if (ext.isEmpty()) base else "$base.$ext"
        val newRef = store.create(parent, desired)
        store.write(newRef, TextCodec.encode(snap.text, sess.format))
        flushAndRememberPrevious()
        installAndOpen(newRef, showIme = false)
    }

    /** [clock] is injected for tests; UTC keeps [ConflictNames] output deterministic regardless of the device's
     * timezone. */
    private fun nowAsLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(clock()), ZoneOffset.UTC)

    companion object {
        private const val KEY_DOC_KEY = "docKey"
        private const val KEY_SEL_START = "selStart"
        private const val KEY_SCROLL_Y = "scrollY"
        private const val TREE_WATCH_DEBOUNCE_MS = 300L

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container =
                        (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MdWriterApp)
                            .container
                    EditorViewModel(
                        documents = container.documents,
                        library = container.library,
                        autosave = container.autosave,
                        settings = container.settings,
                        positions = container.positions,
                        recovery = container.recovery,
                        autoNamer = container.autoNamer,
                        appScope = container.applicationScope,
                        main = container.dispatchers.main,
                        handle = createSavedStateHandle(),
                        clock = System::currentTimeMillis,
                    )
                }
            }
    }
}
