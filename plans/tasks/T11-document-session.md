# T11 — Document session: settings, repository, autosave, EditorViewModel, restore, welcome note

**Goal** The editor becomes a real note-taking app. It opens a real file in `filesDir/library`, saves automatically
(no Save button), brings back your text and caret after the process is killed, and notices when the file changes on
disk. First launch creates and opens `Welcome.md`; later launches reopen the last note where you left it. The settings
and positions stores hold every key the app will need, so later tasks only read them.

**Depends on**
- T07: `EditorController` (`install`, `snapshot`, `edits`, `version`, `caret`, `scrollY`, `hideIme/showIme/requestFocus`), and `EditorScreen`/`EditorHost`
  showing it full screen.
- T10: `DocRef`/`DocKey`/`key()`/`toRef()`/`FolderRef`, `DocumentStore` (+`displayName`), `FileStat`, `StorageError`, `StorageException`, `userMessage()`,
  `TextCodec`/`TextFormat`/`DecodeResult`, `StorageLimits`, `NoteFiles`, `Hashes`, `TrashBin`, `InternalStore` (+`purgeTrash()`),
  `RecoveryStore`/`RecoveryCopy`. T02: `MdWriterTheme`, `LocalWriterColors`.

**Read first**
- `plans/01-architecture.md` §5, §6.2 (`InstallRequest`), §6.3, §6.4, §7, §8, §9 (size limits), §10.
- `plans/02-design-spec.md` §2 (colour tokens), §3 (text column), §7 (the last 3 bullets on new note / open).
- `plans/research/platform.md` §2.8–§2.10 and §2.13; `plans/research/design.md` §4.10.
- The STATUS entries of T07 and T10. Check real names first: `grep -rn "fun \|class " app/src/main/kotlin/dev/mdwriter/data
  app/src/main/kotlin/dev/mdwriter/editor/EditorController.kt`.

## Scope — In / Out
- **In:**: `Settings`/`SettingsRepository` (all keys), `PositionStore`, `LibraryRepository` (minimal routing); `DocumentRepository`, `AutosaveCoordinator`,
  `EditorViewModel`, `EditorUiState`/`ConflictState`/`EditorEvent`; `ConflictBanner`, the `EditorScreen` wiring, `MdWriterRoot`, the full `AppContainer`, the
  welcome note and startup rules, and the backup rules.
- **Out:**: Library drawer, `DocumentSession`, `AutoNamer`, empty-note cleanup, `UniqueName` → **T12**; "Start writing…" hint, chrome, overflow, back handling →
  **T13**; SAF → **T14**. Focus/typewriter/stats → **T15**; External docs, intents, read-only pill, share → **T18**; Settings UI and applying
  font/size/line-length at runtime → **T19**. StrictMode → **T20**.

## Files to create / modify (all under `app/src/`)
- `main/kotlin/dev/mdwriter/data/settings/Settings.kt`: `Settings`, `SortOrder`, `LineLengths` (reuses T02's `ThemeMode` and `WriterFont`).
- `main/kotlin/dev/mdwriter/data/settings/SettingsRepository.kt`: DataStore "settings"; `settings`, `current()`, `update{}`.
- `main/kotlin/dev/mdwriter/data/settings/PositionStore.kt`: `Position`; DataStore "positions"; LRU 200.
- `main/kotlin/dev/mdwriter/data/library/LibraryRepository.kt`: `storeFor(ref)`, `storeFor(location)`, `rootOf`, `parentOf`, `newestDoc()`.
- `main/kotlin/dev/mdwriter/data/document/LoadedDocument.kt`: `LoadedDocument`, `ExternalCheck`.
- `main/kotlin/dev/mdwriter/data/document/SaveResult.kt`: `SaveResult`, `SaveState`.
- `main/kotlin/dev/mdwriter/data/document/DocumentRepository.kt`: `load`/`save`/`checkExternal` + conflict detection.
- `main/kotlin/dev/mdwriter/data/document/AutosaveCoordinator.kt`: `Snapshot`, `AutosaveTarget`, `AutosaveCoordinator`.
- `main/kotlin/dev/mdwriter/data/document/ConflictNames.kt`: pure "(conflict YYYY-MM-DD HHmm)" naming.
- `main/kotlin/dev/mdwriter/data/document/WelcomeNote.kt`: `object WelcomeNote { const val FILE_NAME = "Welcome.md"; val TEXT }`.
- `main/kotlin/dev/mdwriter/ui/editor/EditorUiState.kt`: `EditorUiState` (01 §6.4), `ConflictState`, `ConflictAction`, `EditorEvent`.
- `main/kotlin/dev/mdwriter/ui/editor/EditorViewModel.kt`: the session logic, `EditorBinding`, `Factory`.
- `main/kotlin/dev/mdwriter/ui/editor/ConflictBanner.kt`: the non-modal top banner.
- `main/kotlin/dev/mdwriter/ui/editor/EditorScreen.kt` (modify): events → controller, edits → VM, lifecycle, banner.
- `main/kotlin/dev/mdwriter/ui/root/MdWriterRoot.kt` (create): theme from settings, `SnackbarHost`, `EditorScreen`.
- `main/kotlin/dev/mdwriter/MainActivity.kt` (modify): `setContent { MdWriterRoot(container) }`.
- `main/kotlin/dev/mdwriter/AppContainer.kt` (modify): every member of 01 §5, plus the trash purge on start.
- `main/kotlin/dev/mdwriter/editor/FocusModeKind.kt`: **create** `enum class FocusModeKind { Off, Sentence, Paragraph }` (no earlier task defines it; T15 reuses it).
- `main/res/xml/data_extraction_rules.xml` (new); `main/AndroidManifest.xml` (modify: `android:dataExtractionRules`).
- Tests under `test/kotlin/dev/mdwriter/`: `data/settings/{SettingsRepositoryTest,PositionStoreTest}.kt`;
  `data/document/{AutosaveCoordinatorTest,DocumentRepositoryTest,ConflictNamesTest}.kt`; `ui/editor/EditorViewModelTest.kt`; `testing/FakeDocumentStore.kt` (in
  memory; switches `nullLastModified`, `failNextWrites(n)`, `externalWrite(ref, bytes)`); `testing/FakeEditorBinding.kt`.

## Steps
1. Read the T07/T10 STATUS entries. Run `make test` and confirm it is green.
2. `Settings.kt` + `SettingsRepository.kt` (Ref A). Then `SettingsRepositoryTest`: defaults; a round trip with every field non-default; list order kept; an
   unknown enum string falls back to its default; two concurrent `update`s both apply.
3. `PositionStore.kt` (Ref B) + test: put/get, `move`, `remove`, and the 201st key evicts the least-recently-used entry.
4. `LibraryRepository.kt` (minimal; T12 extends it, T14 adds Tree, T18 adds External): `storeFor(ref)`: Internal → `internalStore`; `TreeDoc` → throw
   `StorageException(PermissionLost)`; `External` → throw `StorageException(NotFound)`; `rootOf(location) = FolderRef(location, "")` (Internal);
   `parentOf(ref)`: Internal = `relPath.substringBeforeLast('/', "")`; other refs → null; `suspend fun newestDoc(): DocRef?`: a depth-first walk of the internal
   tree (depth ≤ 8) via `list`; returns the file with the largest `lastModified`.
5. `SaveResult.kt`, `LoadedDocument.kt`, `DocumentRepository.kt` (Ref C) + `DocumentRepositoryTest`. Use real `InternalStore`/`RecoveryStore` on a
   `TemporaryFolder`, and `FakeDocumentStore` for the null-`lastModified` case.
6. `AutosaveCoordinator.kt` (Ref D) + `AutosaveCoordinatorTest` (virtual time; see Acceptance 3).
7. `ConflictNames.name(base, ext, LocalDateTime)` + test: `("Walk","md", 2026-09-25T14:02)` → `"Walk (conflict 2026-09-25 1402).md"`. An empty ext gives no dot.
   Callers make it unique with `NoteFiles.uniqueName`.
8. `WelcomeNote.kt` with the exact text of Ref F.
9. `EditorUiState.kt` + `EditorViewModel.kt` (Ref E). Then `EditorViewModelTest`: `Dispatchers.setMain(StandardTestDispatcher())`, Turbine on
   `events`/`uiState`, real Settings/Position stores on temp files, `FakeDocumentStore`, `FakeEditorBinding`.
10. `ConflictBanner`: A `Surface` at the top of the text column (max width = the text column), 16 dp margins; `surface` background, 1 dp hairline border, 12 dp
    radius; the message is 14 sp `textPrimary`; actions are `TextButton`s in `accent`; `ChangedOnDisk`: "Changed on disk" + Reload · Keep mine · Save both;
    `Gone`: "File was moved or deleted" + Save as new · Close; No scrim and no dialog; the editor stays editable. `semantics { liveRegion =
    LiveRegionMode.Polite }`.
11. `EditorScreen` wiring (Ref G). Then `MdWriterRoot(container)`: `MdWriterTheme` driven by `themeMode` (+ `isSystemInDarkTheme()`) and `pureBlack`. Adapt to
    T02's parameters (`grep -rn "fun MdWriterTheme" app/src`); A `SnackbarHost` for `EditorEvent.Message`; `LaunchedEffect(Unit) { vm.start() }`;
    `MainActivity`: `setContent { MdWriterRoot((application as MdWriterApp).container) }`.
12. `AppContainer`: `internalStore = InternalStore(File(filesDir,"library"), TrashBin(File(filesDir,".trash")))`; `recovery =
    RecoveryStore(File(noBackupFilesDir,"recovery"))`; `library = LibraryRepository(internalStore, settings)`; `documents = DocumentRepository(library,
    recovery, dispatchers.io)`; `autosave = AutosaveCoordinator(applicationScope, dispatchers.default)`; In `init`, launch `internalStore.purgeTrash()` on IO in
    `applicationScope`.
13. Backup: `res/xml/data_extraction_rules.xml` exactly as in platform §2.13 (`library/` + `datastore/`, both sections); Manifest:
    `android:dataExtractionRules="@xml/data_extraction_rules"`, and keep `android:allowBackup="true"`; Put the "How to turn backup off" text (Pitfalls) as an
    XML comment above `<application>`.
14. `make format && make check`. Then the emulator checks and the STATUS entry.

## Reference code
**A. Settings (copy the field list verbatim; the defaults are the contract for T12–T19)**
```kotlin
package dev.mdwriter.data.settings
// ThemeMode { System, Light, Dark } and WriterFont { Duo, Quattro, Mono } are T02's enums (dev.mdwriter.ui.theme) — reuse them, never redeclare.
enum class SortOrder { ModifiedNewestFirst, ModifiedOldestFirst, NameAToZ, NameZToA } // names fixed by T12
object LineLengths { val ALLOWED = listOf(64, 72, 80) }
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System, val pureBlack: Boolean = false,
    val typeface: WriterFont = WriterFont.Duo,          // T02 enum; if 02 §1 names another default family, use that
    val textSizeStep: Int = 2 /* EditorMetrics.DEFAULT_TEXT_SIZE_STEP */, val lineLength: Int = 64,
    val focusMode: FocusModeKind = FocusModeKind.Off, val typewriter: Boolean = false, val wordCount: Boolean = false,
    val swipeNavigation: Boolean = true, val highlightSyntax: Boolean = false,
    val newNoteExtension: String = "md", val showExtensions: Boolean = false,
    val sortOrder: SortOrder = SortOrder.ModifiedNewestFirst,
    val lastOpenDoc: DocKey? = null, val linkedTrees: List<String> = emptyList(),   // tree URI strings, link order (T14)
    val autoNamed: Set<String> = emptySet(),            // DocKey.value's (T12)
    val hintDismissed: Boolean = false, val recentExternal: List<String> = emptyList(), // DocKey.values, newest first, cap 100 (T18)
    val welcomeCreated: Boolean = false,
)
class SettingsRepository(private val store: DataStore<Preferences>) {
    val settings: Flow<Settings> = store.data.map { it.toSettings() }.distinctUntilChanged()
    suspend fun current(): Settings = settings.first()
    suspend fun update(transform: (Settings) -> Settings) { store.edit { p -> p.writeAll(transform(p.toSettings())) } }
}
// AppContainer: PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatchers.io + SupervisorJob()),
//     produceFile = { app.preferencesDataStoreFile("settings") })  -> filesDir/datastore/settings.preferences_pb
```
- Keys (snake_case of the field names): `theme_mode pure_black typeface text_size_step line_length focus_mode typewriter word_count swipe_navigation
  highlight_syntax new_note_extension show_extensions sort_order last_open_doc linked_trees auto_named hint_dismissed recent_external welcome_created`.
- Enums are stored by `name`. `auto_named` is a string set. Ordered lists are ONE string joined with `'\n'`. A null `lastOpenDoc` removes the key.
- `FocusModeKind` lives in `editor/FocusModeKind.kt` (created here). `EditorController.focusMode`/`typewriter` are added by T15, not here.

**B. PositionStore (sketch)**
- `data class Position(val caret: Int, val scrollY: Int)`.
- Key `stringPreferencesKey("p_" + Hashes.sha1Hex(key.value))`; value `"$caret,$scrollY,$usedAt"`.
- API: `get(key): Position?`, `put(key, p)`, `move(from, to)`, `remove(key)`, all `suspend`.
- `put` evicts inside the same `edit {}`: when there are more than 200 `p_` keys, drop the smallest `usedAt`.
- The clock is injected.

**C. DocumentRepository (sketch; the rules are exact)**
```kotlin
data class LoadedDocument(val ref: DocRef, val text: String, val baseline: FileStat, val format: TextFormat,
    val readOnly: Boolean, val displayName: String, val large: Boolean, val recovered: Boolean,
    val diskTextIfConflict: String?)            // non-null: recovery copy is OLDER than a changed disk file
sealed interface SaveResult { data class Saved(val newBaseline: FileStat) : SaveResult
    data class Conflict(val onDisk: FileStat) : SaveResult; data class Failed(val error: StorageError) : SaveResult }
sealed interface SaveState { data object Clean : SaveState; data object Dirty : SaveState
    data object Saving : SaveState; data class Error(val message: String) : SaveState }
sealed interface ExternalCheck { data object Unchanged : ExternalCheck; data object Gone : ExternalCheck
    data class Changed(val disk: LoadedDocument) : ExternalCheck }
class DocumentRepository(private val library: LibraryRepository, private val recovery: RecoveryStore,
                         private val io: CoroutineDispatcher) {
    private val knownHash = ConcurrentHashMap<DocKey, String>()   // sha1 of the bytes last read/written
    suspend fun load(ref: DocRef, useRecovery: Boolean = true): LoadedDocument = withContext(io) {
        val store = library.storeFor(ref); val key = ref.key()
        val stat = store.stat(ref) ?: throw StorageException(StorageError.NotFound)
        stat.size?.let { if (it > StorageLimits.MAX_OPEN_BYTES) throw StorageException(StorageError.TooLarge(it)) }
        val bytes = store.read(ref)
        val dec = TextCodec.decode(bytes) as? DecodeResult.Text ?: throw StorageException(StorageError.Encoding) // binary
        knownHash[key] = Hashes.sha1Hex(bytes)                    // add a ByteArray overload to Hashes if missing
        val readOnly = (ref is DocRef.External && !ref.writable) || bytes.size > StorageLimits.READ_ONLY_BYTES
        val base = LoadedDocument(ref, dec.text, stat, dec.format, readOnly, store.displayName(ref),
            large = bytes.size > StorageLimits.LARGE_BYTES, recovered = false, diskTextIfConflict = null)
        val rec = if (useRecovery && !readOnly) recovery.read(key) else null
        when {                                                    // 01 §8; never silently drop a recovery copy
            rec == null -> base
            rec.text == dec.text -> base.also { recovery.delete(key) }
            stat.lastModified == null || rec.savedAt >= stat.lastModified -> base.copy(text = rec.text, recovered = true)
            else -> base.copy(text = rec.text, recovered = true, diskTextIfConflict = dec.text)
        }
    }
    suspend fun save(ref: DocRef, text: String, baseline: FileStat, format: TextFormat): SaveResult = withContext(io) {
        val store = library.storeFor(ref); val key = ref.key()
        try {
            recovery.write(key, text)                                // FIRST (platform §2.8)
            val now = store.stat(ref) ?: return@withContext SaveResult.Failed(StorageError.NotFound)
            if (changedOnDisk(store, ref, baseline, now)) return@withContext SaveResult.Conflict(now)
            val bytes = TextCodec.encode(text, format)
            store.write(ref, bytes); knownHash[key] = Hashes.sha1Hex(bytes); recovery.delete(key)
            SaveResult.Saved(store.stat(ref) ?: FileStat(null, bytes.size.toLong()))
        } catch (e: StorageException) { SaveResult.Failed(e.error) }
          catch (e: IOException) { SaveResult.Failed(StorageError.ProviderFailure(e)) }
    }
    /** mtime+size when both known; else size, then content hash vs the last bytes we saw (SAF, platform §2.9). */
    private suspend fun changedOnDisk(store: DocumentStore, ref: DocRef, baseline: FileStat, now: FileStat): Boolean {
        if (now.lastModified != null && baseline.lastModified != null)
            return now.lastModified != baseline.lastModified || now.size != baseline.size
        if (now.size != baseline.size) return true
        val known = knownHash[ref.key()] ?: return false
        return Hashes.sha1Hex(store.read(ref)) != known
    }
    // checkExternal(ref, baseline): stat null or PermissionLost -> Gone; changedOnDisk -> Changed(load(ref, false)); else Unchanged
}
```
**D. AutosaveCoordinator (sketch; keep the semantics exactly)**
```kotlin
data class Snapshot(val version: Long, val text: String)
interface AutosaveTarget { val ref: DocRef
    suspend fun snapshot(): Snapshot                          // implementer hops to Main
    suspend fun persist(s: Snapshot): SaveResult }            // implementer calls DocumentRepository.save
class AutosaveCoordinator(private val scope: CoroutineScope, dispatcher: CoroutineDispatcher,
    private val maxLatencyMs: Long = 10_000, private val backoffMs: List<Long> = listOf(1_000, 2_000, 5_000, 10_000)) {
    private val serial = dispatcher.limitedParallelism(1)     // ALL mutable fields touched only on `serial`
    private val saveLock = Mutex()
    val state: StateFlow<SaveState>                            // MutableStateFlow(Clean)
    fun idleMsFor(ref: DocRef) = if (ref is DocRef.TreeDoc) 2_000L else 1_000L
    fun begin(target: AutosaveTarget, version: Long, dirty: Boolean) // cancel timers; saved = if (dirty) MIN else version
    fun end()                                                  // target = null, timers cancelled (after flush)
    fun onEdit(version: Long)      // latest = version; if != saved: Dirty (unless Error); restart idle timer;
                                   // start the max-latency timer only if not running (cancelled only on success)
    fun resume()                   // clears `paused` (set by Conflict) and schedules a save
    suspend fun flush(pre: Snapshot? = null) = scope.async(serial) { cancelTimers(); saveNow(pre) }.await()
    private suspend fun saveNow(pre: Snapshot?) = withContext(NonCancellable) { saveLock.withLock {
        val t = target ?: return@withLock; if (paused) return@withLock
        if (pre == null && latest == saved) return@withLock    // clean: don't even snapshot (O(n) copy)
        val snap = pre ?: t.snapshot(); if (snap.version == saved) { state = Clean; return@withLock }
        state = Saving
        when (val r = t.persist(snap)) {
            is SaveResult.Saved -> { saved = snap.version; attempt = 0; cancelRetry(); cancelMax()
                state = if (latest == saved) Clean else Dirty.also { schedule() } }
            is SaveResult.Conflict -> { paused = true; state = Dirty }        // VM shows the banner
            is SaveResult.Failed -> { state = Error(r.error.userMessage())
                retryJob = scope.launch(serial) { delay(backoffMs[min(attempt++, backoffMs.lastIndex)]); saveNow(null) } }
        } } }
}
```
**E. EditorViewModel (sketch; the behaviour is exact)**
- **Construction:** Constructor `(documents, library, autosave, settings, positions, recovery, appScope, main: CoroutineDispatcher, handle: SavedStateHandle,
  clock: () -> Long)`; `Factory = viewModelFactory { initializer { … (this[APPLICATION_KEY] as MdWriterApp).container … createSavedStateHandle() } }`.
- **Types:** `interface EditorBinding { fun snapshot(): Snapshot; fun caret(): Int; fun scrollY(): Int }`, main thread only, with `bindEditor(b)` /
  `unbindEditor()`. Unbind stores a final `lastSnapshot` so a pending save never touches a dead view; `sealed interface EditorEvent { Install(request:
  InstallRequest); AfterOpen(showIme: Boolean); Message(text: String) }`, sent through `Channel(BUFFERED).receiveAsFlow()`; `sealed interface ConflictState {
  data class ChangedOnDisk(val diskText: String, val diskBaseline: FileStat); data object Gone }`; `enum class ConflictAction { Reload, KeepMine, SaveBoth,
  SaveAsNew, Close }`; `uiState = combine(_ui, autosave.state) { u, s -> u.copy(save = s) }.stateIn(viewModelScope, WhileSubscribed(5_000), initial)`.
- **`start()`** is idempotent. Try these in order: (1) `handle["docKey"]` → open with `handle["selStart"]`/`["scrollY"]` (process death); (2) First launch
  (`!welcomeCreated && newestDoc()==null`): create a unique `Welcome.md` in the internal root, write `WelcomeNote.TEXT`, set `welcomeCreated=true`, and open
  with `selection=TEXT.length`, `showIme=false`; (3) `lastOpenDoc?.toRef()` with a non-null `stat` → its `PositionStore` position, `showIme=false`; (4)
  `newestDoc()`; (5) `newNote()`.
- **`open(ref, showIme, selection?, scrollY?)`** in order: flush the current doc and `positions.put` it; `documents.load(ref)`; Set `_ui` doc/title
  (`NoteFiles.baseName(displayName)`)/readOnly; `settings.update { lastOpenDoc = key }`, and `handle["docKey"]`; Send `Install(InstallRequest(text,
  sel.coerceIn(0, len), scroll, readOnly))`, then `AfterOpen(showIme)`; `large` → `Message("Large document — styling may be slower")`; size read-only →
  `Message("Opened read-only (over 5 MB)")`; `diskTextIfConflict` → `ChangedOnDisk`; On a load error: `Message(error.userMessage())` (binary: "‘x’ is not a text
  file"), and keep the current doc.
- **`onInstalled(version)`**: unless readOnly, `autosave.begin(target, version, dirty = loaded.recovered)`. The target's `persist` calls `documents.save(ref,
  text, baseline, format)`: `Saved` → store the new baseline; `Conflict` → `load(ref, useRecovery=false)`, then `ChangedOnDisk`; `Failed(NotFound)` → `Gone`.
- **`newNote()`**: create an empty `Untitled.<newNoteExtension>` (unique) in the internal root, add its key to `autoNamed`, then `open(ref, showIme = true)`.
- **Edits and lifecycle:** `onEdit(version)` = `autosave.onEdit(version)`; `onStart()` runs `checkExternal` [`Changed` and Clean → silent reload (`Install`
  keeping the caret coerced) + `begin` clean; `Changed` and dirty → `ChangedOnDisk`; `Gone` → `Gone`]; `onStop()`: (1) snapshot on main now (binding or
  `lastSnapshot`), (2) store caret/scroll in `handle`, (3) `appScope.launch { withContext(NonCancellable) { autosave.flush(snap); positions.put(key,
  Position(…)) } }`.
- **`resolveConflict(a)`** always clears `conflict`: Reload: `load(useRecovery=false)`, delete the recovery copy, install, begin clean; KeepMine: baseline =
  `diskBaseline`, `autosave.resume()`, `flush()`; SaveBoth: (1) write the buffer to a unique `ConflictNames.name(base, ext, now)` in `library.parentOf(ref) ?:
  rootOf(Internal)` (create + save, same format), (2) `Message("Saved your version as ‘<name>’")`, (3) Reload; SaveAsNew: write the buffer to a unique
  `<base>.<ext>` there, then open it; Close: `start` steps 3–5, without saving.

**F. `WelcomeNote.TEXT` (copy verbatim; it ends with an empty line so the caret sits on a fresh line)**
```markdown
# Welcome to mdwriter

mdwriter is a quiet place to write. There is no Save button: every word is saved as you type, as a plain Markdown file on this phone.

Swipe right for your notes. Swipe left to preview.

Select text to format it. The toolbar appears only while something is selected.

## Markdown in thirty seconds

# Heading
## Smaller heading
**bold** and *italic*
- a list item
- [ ] a task
- [x] a finished task
> a quote
`code`
[a link](https://example.com)

## A few more things

- Focus Mode and typewriter scrolling live in the menu at the top right.
- Your notes are ordinary .md files. Nothing leaves your phone unless you share it.
- Delete this note whenever you like.

```
**G. EditorScreen wiring (sketch)**
```kotlin
LaunchedEffect(vm, controller) { vm.events.collect { e -> when (e) {
    is EditorEvent.Install -> { controller.install(e.request); vm.onInstalled(controller.version) }
    is EditorEvent.AfterOpen -> if (e.showIme) { controller.requestFocus(); controller.showIme() } else controller.hideIme()
    is EditorEvent.Message -> onMessage(e.text) } } }
LaunchedEffect(vm, controller) { controller.edits.collect { vm.onEdit(it.version) } }
DisposableEffect(vm, controller) { vm.bindEditor(object : EditorBinding {
    override fun snapshot() = Snapshot(controller.version, controller.snapshot())
    override fun caret() = controller.caret(); override fun scrollY() = controller.scrollY() })
    onDispose { vm.unbindEditor() } }
LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
```

## Acceptance criteria
1. `make check` is green. `make test` runs all 6 new test classes: 0 failures, 0 skipped.
2. `SettingsRepositoryTest`: `defaultsWhenEmpty`, `roundTripsEveryField`, `keepsListOrder`, `unknownEnumFallsBack`, `concurrentUpdatesBothApply`.
   `PositionStoreTest`: `putGet`, `moveAndRemove`, `evictsLeastRecentlyUsedBeyond200`.
3. `AutosaveCoordinatorTest` (`runTest` + `StandardTestDispatcher(testScheduler)`): `savesAfter1sIdle`: 0 writes at 999 ms, exactly 1 at 1,001 ms;
   `treeDocUses2sIdle`; `continuousTypingSavesWithin10s`: an edit every 500 ms for 15 s gives ≥ 1 write by 10,001 ms; `flushSavesImmediatelyAndAwaits`;
   `unchangedVersionNeverWrites`: 0 writes and 0 snapshots; `failureRetriesWithBackoff`: retries at +1/+2/+5/+10/+10 s; `conflictPausesUntilResume`;
   `flushSurvivesCallerCancellation`: the write still completes.
4. `DocumentRepositoryTest`: `crlfBomRoundTripByteForByte`: unchanged text saves identical bytes; `over1MbFlagsLarge`, `over5MbIsReadOnly`; `binaryIsRefused`
   (`StorageException(Encoding)`); `newerRecoveryWins`, `olderDifferentRecoveryRaisesConflict`; `externalChangeDetectedOnSave` (→ `Conflict`);
   `nullLastModifiedUsesSizeThenHash`; `recoveryWrittenBeforeWriteAndDeletedAfter`: a failing store leaves the recovery file.
5. `EditorViewModelTest`: `firstLaunchCreatesWelcomeCaretAtEndImeHidden`: `selection == WelcomeNote.TEXT.length` and `AfterOpen(false)`;
   `laterLaunchReopensLastDocAtPosition`, `missingLastDocOpensNewest`, `noDocsAfterWelcomeCreatesUntitledWithIme`; `editAutosavesAfter1s`;
   `externalChangeWhileCleanReloadsSilently`, `externalChangeWhileDirtyShowsConflict`; `reloadDiscardsMine`, `keepMineOverwritesDisk`;
   `saveBothWritesConflictCopy`: `X (conflict 2026-09-25 1402).md` holds my text, and the editor shows the disk text;
   `processDeathRestoresFromHandleAndRecovery`: docKey + selStart=5 + a newer recovery copy → `Install(text=recovery, selection=5)`, then Dirty, then saved.
6. `aapt2 dump xmltree` of the debug APK shows `allowBackup` true and `dataExtractionRules` = `@xml/data_extraction_rules`.
7. Emulator after `pm clear`: `run-as … ls files/library` prints `Welcome.md`; The screenshot shows the H1 "Welcome to mdwriter" with glyph height ≥ 1.5× the
   body text; `dumpsys input_method` shows `mInputShown=false`.
8. Type `zqx` at the end and wait 2 s. `tail -c 40` of the file contains `zqx`.
9. Type `kill1`, press HOME, `am kill`; `pidof` prints nothing. After a relaunch, `kill1` is present and the caret is right after it: type `!` and the file ends
   with `kill1!`.
10. Conflict checks: HOME, append `EXT` via `run-as`, relaunch → `EXT` shows, with no banner; Type `y`, and append again within 1 s → after 3 s the "Changed on
    disk" banner is visible; "Save both" → `Welcome (conflict …).md` exists.

## Verification commands
```bash
make check
export JAVA_HOME=$(/usr/libexec/java_home -v 21); ./gradlew :app:testDebugUnitTest --tests 'dev.mdwriter.data.*' --tests 'dev.mdwriter.ui.editor.EditorViewModelTest'
~/Library/Android/sdk/build-tools/36.0.0/aapt2 dump xmltree --file AndroidManifest.xml app/build/outputs/apk/debug/app-debug.apk | grep -E 'allowBackup|dataExtractionRules'
make emulator; make devices; make install-debug DEVICE=emulator-5554
ADB=~/Library/Android/sdk/platform-tools/adb; S="-s emulator-5554"; P=dev.mdwriter.debug
$ADB $S shell pm clear $P   # first-launch state of the DEBUG app on the emulator only
$ADB $S shell am start -n $P/dev.mdwriter.MainActivity; sleep 2; $ADB $S shell run-as $P ls files/library
$ADB $S shell dumpsys input_method | grep mInputShown; $ADB $S exec-out screencap -p > /tmp/t11-welcome.png
$ADB $S shell input text zqx; sleep 2; $ADB $S shell run-as $P tail -c 40 files/library/Welcome.md
$ADB $S shell input text kill1; $ADB $S shell input keyevent KEYCODE_HOME; sleep 1
$ADB $S shell am kill $P; $ADB $S shell pidof $P; $ADB $S shell am start -n $P/dev.mdwriter.MainActivity
$ADB $S shell "run-as $P sh -c 'echo EXT >> files/library/Welcome.md'"
```

## Pitfalls
- **Never put the text in the Bundle** (01 §8, hard rule 8). `SavedStateHandle` holds only `docKey`, `selStart` and `scrollY`.
- **No View in the ViewModel** (01 §5). Unbind `EditorBinding` in `onDispose` and cache the last `Snapshot`. `snapshot()` runs on main (`withContext(main)`).
- **Saves outlive the ViewModel.** Flush in `appScope` + `NonCancellable`, never in `viewModelScope` (platform §2.8).
- **Recovery copy first, delete only after success.** An OLDER, differing recovery copy becomes a conflict and is never dropped.
- **Take the baseline from `stat` after our own write.** Otherwise ON_START flags our own save as an external change. Don't re-install on ON_START when nothing
  changed: it would kill the undo history and the scroll position.
- DataStore: The file name must end in `.preferences_pb` (use `preferencesDataStoreFile`); Never open two DataStores on one file; In tests, use a fresh
  `TemporaryFolder` file and cancel the DataStore scope in `@After`.
- Coerce `InstallRequest.selection` into `0..text.length`; stored positions can be stale.
- **How to turn backup off** (goes in the manifest comment): `allowBackup="false"` stops only cloud backup. At targetSdk ≥ 31, device-to-device transfer still
  follows `data_extraction_rules.xml`; To stop both, replace every `<include>` in both sections with `<exclude domain="root" path="."/>` plus the same `exclude`
  for `file`, `database`, `sharedpref` and `external`; `.trash/`, `noBackupFilesDir` (recovery) and the cache are never backed up (platform §2.13).
- For the first-launch check, use `pm clear` on the emulator debug app. Never uninstall anything (hard rule 13).

## Definition of done
- [ ] Every file above exists, with the names used here (list any deviation in STATUS).
- [ ] Acceptance 1–10 are checked, and the screenshot is described in STATUS.
- [ ] `make check` is green.
- [ ] STATUS.md entry written: the `Settings` fields and defaults; the `EditorEvent`/`EditorBinding`/`start()` names for T12/T13/T18; the deviation:
      `DocumentRepository.save` takes `format`, and `LoadedDocument` has extra fields. Update 01 §6.3 in the same commit.
- [ ] Commit `T11: document session, autosave, settings, welcome note`.

