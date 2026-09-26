# T12 — Library drawer UI + file operations

**Goal**
The library becomes usable: a modal drawer (opened for now by a plain glyph at top-start) lists the notes of the
current folder with title, excerpt and relative date, the open note marked by a blue bar. You can search, sort, drill
into folders, create notes and folders, and rename / duplicate / move / delete (undoable) notes. Notes created with
"+" are named from their first line when you leave them, and an untitled note left empty disappears.

**Depends on**
- **T11** delivered: `LibraryRepository` (routes `LocationId.Internal` to `InternalStore`), `DocumentRepository`,
  `AutosaveCoordinator` (`flush()`), `EditorViewModel` (loads a `DocRef`, restores caret/scroll from `PositionStore`,
  emits `InstallRequest`s, flushes on `ON_STOP`), `SettingsRepository` + `Settings`, `PositionStore`, the welcome note.
- **T10** delivered: `InternalStore` (`list/read/write/stat/create/createFolder/rename/trash/restore/move/changes`),
  `TrashToken`, `TextCodec`.
- **T04**: `DocTitle.fromContent(text): String?`, `DocTitle.sanitizeFileName(name): String` (returns a base name,
  no extension). **T02**: `WriterColors` tokens, fonts, `ic_*` drawables.
- Read the STATUS entries of T10, T11 (and T02, T05) first: they name the real accessors (e.g. how the composition
  reads `WriterColors`, how `EditorViewModel` opens a document). Where this file shows a name that T11 spelled
  differently, **use T11's name**; add only what is missing (listed in step 1).

**Read first**
- `plans/01-architecture.md` §3 (package map), §5 (AppContainer), §6.3 (data contracts), §6.4 (UI state), §7, §10.
- `plans/02-design-spec.md` §2 (tokens), §7 (library drawer — the spec for this task), §11 (motion), §12 (wireframe),
  §14 (icons).
- `plans/research/platform.md` §2.5–§2.7 (folders, naming, rename/delete/undo), §4.1 #3 (M3 drawer facts), §5 (back).
- `plans/research/design.md` §4.5 (drawer details), §5.2–§5.4 (type scale, spacing, shapes), §6.4 + §6.7 (wireframes).

## Scope — In / Out
**In:** `LibraryViewModel`; `LibraryContent` (stateless) + `FileRow`/`FolderRow`/`LocationRow`; `RelativeDate`;
dialogs (rename, new folder, move); long-press row menu; undoable delete snackbar; `ModalNavigationDrawer` wiring in
`MdWriterRoot` with IME hide/restore; a temporary library glyph; `AutoNamer` (auto-name on leave, delete empty
untitled on switch); `DocumentSession` (the seam between `LibraryViewModel` and `EditorViewModel`); the
`LibraryRepository`/`Settings`/`PositionStore` additions listed in step 1; tests.

**Out (owned elsewhere — do not build):**
- Horizontal swipe-to-open, fading chrome, overflow menu, all `BackHandler`s (subfolder-up, search-clear), expanded
  permanent pane, Ctrl+N/Ctrl+L, accessibility "Open library" → **T13**. (Back just closes the drawer via
  `ModalDrawerSheet(drawerState)` for now.)
- Linked folders, "Use a folder…" row, SAF specifics of `parentOf`/`nameOf`/search → **T14**.
- "Share" in the row menu → **T18** (leave the `rowMenuExtras` slot). Settings UI for sort/extension toggles → **T19**
  (T12 already *reads* `sortOrder`, `newNoteExtension`, `showExtensions`).
- Folder rename/delete: not in v1 (`DocumentStore` has no folder delete). Long-press on a folder row does nothing.

## Files to create / modify
| Path (under `app/src/`) | Purpose |
|---|---|
| `main/kotlin/dev/mdwriter/ui/library/LibraryViewModel.kt` | state, sort, breadcrumb, search, all file actions, pending delete |
| `main/kotlin/dev/mdwriter/ui/library/LibraryUiState.kt` | `LibraryUiState`, `Crumb`, `FileItem`, `SearchState`, `PendingDelete`, `LibraryEvent`, `LocationItem` |
| `main/kotlin/dev/mdwriter/ui/library/LibraryContent.kt` | stateless drawer content (header, search, locations, breadcrumb+sort menu, lists, empty state) |
| `main/kotlin/dev/mdwriter/ui/library/FileRow.kt` | `FileRow`, `FolderRow`, `LocationRow`, `RowMenu` (long-press menu) |
| `main/kotlin/dev/mdwriter/ui/library/LibraryDialogs.kt` | `NameDialog` (rename / new folder), `MoveDialog`, `validateName()` |
| `main/kotlin/dev/mdwriter/ui/library/LibrarySnackbar.kt` | `DeleteUndoSnackbarHost` (state-driven, flat style) |
| `main/kotlin/dev/mdwriter/ui/library/RelativeDate.kt` | pure `java.time` formatter |
| `main/kotlin/dev/mdwriter/ui/editor/DocumentSession.kt` | interface implemented by `EditorViewModel` |
| `main/kotlin/dev/mdwriter/data/library/AutoNamer.kt` | auto-name on leave, delete-empty-untitled |
| `main/kotlin/dev/mdwriter/data/library/UniqueName.kt` | `numbered()`, `copyOf()`, `splitName()` (pure) |
| `main/kotlin/dev/mdwriter/data/library/Excerpt.kt` | `Excerpt.fromPrefix(text) = DocTitle.excerpt(text)` (one-line delegate) |
| `main/kotlin/dev/mdwriter/data/library/LibraryRepository.kt` | **modify**: additions of step 1 |
| `main/kotlin/dev/mdwriter/data/settings/Settings.kt`, `SettingsRepository.kt` | **modify** only if fields of step 1 are missing |
| `main/kotlin/dev/mdwriter/data/settings/PositionStore.kt` | **modify**: `move(from, to)`, `remove(key)` if missing |
| `main/kotlin/dev/mdwriter/ui/editor/EditorViewModel.kt` | **modify**: implement `DocumentSession`, `onDrawerOpened()`, `AfterOpen` event, auto-name in `onStop` |
| `main/kotlin/dev/mdwriter/ui/editor/EditorScreen.kt` | **modify**: temporary library glyph; handle `AfterOpen` (focus + IME) |
| `main/kotlin/dev/mdwriter/ui/root/MdWriterRoot.kt` | **modify/create**: hoist `EditorController`, drawer, snackbar host, event wiring |
| `main/kotlin/dev/mdwriter/AppContainer.kt` | **modify**: `val autoNamer` |
| `main/res/values/strings.xml` | all strings of this task (no hard-coded UI text) |
| `test/kotlin/dev/mdwriter/ui/library/RelativeDateTest.kt`, `LibraryViewModelTest.kt`, `LibraryUiTest.kt` | JVM / Robolectric tests |
| `test/kotlin/dev/mdwriter/data/library/AutoNamerTest.kt`, `UniqueNameTest.kt`, `ExcerptTest.kt` | JVM tests |
| `test/kotlin/dev/mdwriter/testing/FakeDocumentSession.kt` (+ reuse T10/T11 fakes) | test doubles |

## Steps
1. **Check / add the data seams.** Grep what T10/T11 built (`grep -rn "fun \|val " app/src/main/kotlin/dev/mdwriter/data`).
   Ensure these exist (add the missing ones, generically on top of the `DocumentStore` interface so T14 inherits them):
   - `Settings` fields: `sortOrder: SortOrder` (enum `ModifiedNewestFirst` default, `ModifiedOldestFirst`,
     `NameAToZ`, `NameZToA`), `autoNamed: Set<String>` (`DocKey.value`s), `newNoteExtension: String` (`"md"`),
     `showExtensions: Boolean` (false). DataStore keys `sort_order` (enum name), `auto_named` (string set),
     `new_note_extension`, `show_extensions`. `SettingsRepository` must offer `val settings: Flow<Settings>` and
     `suspend fun update(transform: (Settings) -> Settings)`; add `update` if T11 only has per-field setters.
   - `PositionStore.move(from: DocKey, to: DocKey)` and `remove(key: DocKey)`.
   - `LibraryRepository`: `entries(folder): Flow<List<LibraryEntry>>` (emits on start, on `store.changes(folder)` and
     on `invalidate()`; listing on IO; conflated), `invalidate()`, `rootOf(location): FolderRef`,
     `parentOf(ref): FolderRef?` and `nameOf(ref): String?` (Internal: derived from `relPath`; other refs return null
     until T14), `createUnique(folder, base, ext): DocRef`, `rename(ref, folder, newBase): DocRef` (keeps the
     extension, unique in folder), `duplicate(ref, folder): DocRef`, `move(ref, to): DocRef`, `createFolder(parent,
     name): FolderRef`, `trash(ref): TrashToken`, `folderTree(location): List<FolderNode>` (depth-first, root first,
     max depth 8), `prefix(entry, maxChars = 2048): String?` (decoded head of the file, LRU cache of 500 keyed by
     `DocKey + lastModified + size`), `search(location, query, limit = 200): List<SearchHit>`. Every mutation calls
     `invalidate()`. Define `data class FolderNode(val folder: FolderRef, val name: String, val depth: Int)` and
     `data class SearchHit(val entry: LibraryEntry, val folderPath: String, val snippet: String?)` in
     `LibraryRepository.kt`.
2. **Pure helpers** `UniqueName`, `Excerpt`, `RelativeDate` + their JVM tests (Reference code §A–§C). Write the
   tests first; they pin behaviour.
3. **`DocumentSession`** (Reference §D). Make `EditorViewModel` implement it by delegating to T11's existing
   open/flush code. `open(ref, showIme, leaveCurrent)`: if a doc is open → `flush()`, then (if `leaveCurrent`)
   `autoNamer.onLeave(old, LeaveReason.Switch)`; load `ref` through T11's path (position restore included); after the
   install, send `EditorEvent.AfterOpen(showIme)` on T11's one-shot event channel. `onCurrentRefChanged(old, new)`
   re-points everything T11 keys by document: `uiState.doc`/`title`, the autosave target (baseline stays valid — a
   rename does not change size), the recovery copy (move or delete the old key's file after a successful flush),
   `settings.lastOpenDoc`, and `SavedStateHandle`'s doc key.
4. **`AutoNamer`** (Reference §E) + `AutoNamerTest`. Wire: `EditorViewModel.onDrawerOpened()` → flush then
   `onLeave(current, DrawerOpened)`; T11's `ON_STOP` flush → then `onLeave(current, Stopped)` in `applicationScope`
   with `NonCancellable`; switches go through `open()`. A `Renamed(newRef)` outcome → `onCurrentRefChanged`.
   Also make every "fresh Untitled note" path you own add its key to `settings.autoNamed` (T11's first-launch fresh
   note too, if it creates one — the welcome note is **not** auto-named).
5. **`LibraryViewModel`** (Reference §F) + `LibraryViewModelTest` with fakes and virtual time.
6. **UI**: `LibraryContent`, rows, row menu, dialogs, snackbar per 02 §7 and the table below. Stateless: every
   composable takes state + lambdas; only `LibraryDrawer(vm, openDoc)` touches the ViewModel.
7. **Root wiring** (Reference §G): hoist `EditorController` to `MdWriterRoot` if T05/T11 created it inside
   `EditorScreen` (pass it down); `ModalNavigationDrawer` with `gesturesEnabled = drawerState.isOpen ||
   drawerState.targetValue == DrawerValue.Open`; `ModalDrawerSheet(drawerState = …)` (the overload that handles
   predictive back); IME hide on open / restore on close; the temporary glyph in `EditorScreen` (`ic_left_panel_open`,
   48 dp target, `textSecondary`, `WindowInsets.statusBars` + 4 dp start, contentDescription "Open library").
8. **UI tests** (Robolectric Compose, `androidx.compose.ui.test.junit4.v2.createComposeRule`) — see acceptance.
9. `make format`, `make check`, install on the emulator, exercise every action, take the screenshots, STATUS, commit.

**Visual spec (02 §7, design.md §5.2–§5.4).** Drawer width `min(360.dp, windowWidth − 56.dp)`; sheet
`drawerContainerColor = surface`, `drawerContentColor = text`, `drawerTonalElevation = 0.dp`, `drawerShape =
RectangleShape`, `scrimColor = scrim`. Header 56 dp: "Library" 20 sp Bold at 20 dp start; end icons `ic_search` (tint
`textSecondary`) and `ic_edit_square` (tint `accent`, contentDescription "New note"). Search field replaces the title:
20 dp radius, `surfaceHover` fill, no border, leading `ic_search`, trailing `ic_close` (clears + closes). "Locations"
13 sp `textSecondary`, 20 dp start; `LocationRow` 44 dp: `ic_phone_android` 20 dp + "On this device" 16 sp (Bold when
selected). Hairline divider: `with(LocalDensity.current) { 1.toDp() }`, colour `divider`. Breadcrumb row 48 dp:
crumbs 13 sp `textSecondary` joined by " › ", last crumb `text`; tapping a crumb navigates to it; end `ic_sort` opens
the **folder menu**. Folder rows 48 dp (`ic_folder` 20 dp + name 16 sp). File rows 72 dp, 20 dp horizontal padding:
line 1 title 16 sp (Bold + 3 dp full-height `accent` bar at the start edge when `openDoc` matches) and the relative
date 12 sp `textSecondary` end-aligned; line 2 excerpt 13 sp `textSecondary`, 1 line, ellipsis. Pressed/selected row
background `surfaceHover`. Empty folder: centred "No notes yet" 16 sp `textSecondary` + `TextButton` "New note" with
`ic_edit_square` tinted `accent`, label in `text`. Menus: `DropdownMenu(shape = RoundedCornerShape(12.dp),
containerColor = surface, tonalElevation = 0.dp, shadowElevation = 0.dp, border = BorderStroke(hairline, divider))`.
Dialogs: `AlertDialog(containerColor = surface, tonalElevation = 0.dp, shape = RoundedCornerShape(12.dp))`,
buttons as `TextButton` in `text` colour (never `accent` text — 02 §2).

**Folder menu (the decided home of "New folder").** The breadcrumb's `ic_sort` button opens one `DropdownMenu`:
group 1 "Date modified" / "Name" (✓ `ic_check` on the active key); group 2 "Newest first" / "Oldest first" (or
"A–Z" / "Z–A" when sorting by name); a hairline; "New folder…" (`ic_create_new_folder`) → `NameDialog` → creates the
folder in the current folder (stays in place; the folder appears in the list). No other entry point.

**Row menu (long-press a file row):** Rename (`ic_drive_file_rename_outline`) · Duplicate (`ic_content_copy`) ·
Move… (`ic_drive_folder_upload`) · *[rowMenuExtras — T18 inserts Share here]* · Delete (`ic_delete`, text + icon in
`danger`). Use `Modifier.combinedClickable(onClick, onLongClick)` with `LocalHapticFeedback` `LongPress`.

## Reference code
### §A `UniqueName` (copy; tests must cover every branch)
```kotlin
package dev.mdwriter.data.library

object UniqueName {
    /** Supported note extensions (lower-case, no dot). Anything else is part of the base name. */
    val NOTE_EXTENSIONS = setOf("md", "markdown", "mdown", "mkd", "txt", "text")

    /** "Groceries.md" -> ("Groceries", "md"); "notes.backup" -> ("notes.backup", null). */
    fun splitName(fileName: String): Pair<String, String?> {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0) return fileName to null
        val ext = fileName.substring(dot + 1).lowercase()
        return if (ext in NOTE_EXTENSIONS) fileName.substring(0, dot) to ext else fileName to null
    }

    /** "Untitled.md", "Untitled 2.md", "Untitled 3.md" … [taken] holds lower-cased sibling names. */
    fun numbered(base: String, ext: String, taken: Set<String>): String =
        generateSequence(1) { it + 1 }.map { n -> if (n == 1) "$base.$ext" else "$base $n.$ext" }
            .first { it.lowercase() !in taken }

    /** "Groceries copy.md", "Groceries copy 2.md" … */
    fun copyOf(base: String, ext: String, taken: Set<String>): String = numbered("$base copy", ext, taken)
}
```

### §B `Excerpt.fromPrefix` — a one-line delegate: `fun fromPrefix(text: String): String? = DocTitle.excerpt(text)`.
T04's `DocTitle.excerpt` (cap `DocTitle.EXCERPT_MAX`) is THE excerpt rule; never re-implement it here. `ExcerptTest` runs the
rows below through `fromPrefix`; if a row disagrees with T04's `DocTitleTest`, T04 wins (fix the row, note it in STATUS).
Reference rules (as T04 implements them): skip a leading YAML front-matter block (`---` … `---`); skip the first non-blank line (it is the title); skip
lines that are only markup (`^(\s*)(`{3,}|~{3,}|-{3,}|\*{3,}|_{3,}|={3,})`); take the next non-blank line and strip:
leading `#{1,6}\s+`, repeated `>\s?`, list markers `[-*+]\s+` / `\d+[.)]\s+`, task `\[[ xX]\]\s+`; inline
`!?\[([^\]]*)\]\([^)]*\)` → `$1`, `<(https?://[^>]+)>` → `$1`, `(\*\*|__|~~|==)(.+?)\1` → `$2`,
`(?<![\w*])([*_])(?!\s)(.+?)(?<!\s)\1(?![\w*])` → `$2`, remove backticks; collapse whitespace; trim; cap 160 chars.
Returns null when nothing is left. Test rows (input → output): `"# Title\n\nIt had been **raining**."` → `"It had been
raining."`; `"Title\n- [ ] call *Ana*"` → `"call Ana"`; `"---\ntitle: x\n---\n# T\n> quoted"` → `"quoted"`;
`"Only one line"` → `null`; `"T\n```\ncode here"` → `"code here"`; `"T\nsee [notes](https://e.com) and snake_case_word"`
→ `"see notes and snake_case_word"`.

### §C `RelativeDate` (copy)
```kotlin
package dev.mdwriter.ui.library

object RelativeDate {
    /**
     * 02 §7: today "14:02" (or "2:02 PM"), yesterday [yesterday], 2–6 days ago "Mon", same year "21 Sep",
     * other years / future beyond today "21 Sep 2025". Pure: zone, locale and 24 h flag are parameters.
     */
    fun format(
        epochMillis: Long?, now: Instant, zone: ZoneId, locale: Locale, use24h: Boolean, yesterday: String,
    ): String {
        if (epochMillis == null) return ""
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(t.toLocalDate(), today)
        val pattern = when {
            days == 0L -> if (use24h) "HH:mm" else "h:mm a"
            days == 1L -> return yesterday
            days in 2..6 -> "EEE"
            t.year == today.year -> "d MMM"
            else -> "d MMM yyyy"
        }
        return DateTimeFormatter.ofPattern(pattern, locale).format(t)
    }
}
```
Call site: `RelativeDate.format(entry.lastModified, now, ZoneId.systemDefault(), Locale.getDefault(),
DateFormat.is24HourFormat(context), stringResource(R.string.library_yesterday))`, with `now` remembered per drawer
opening. `RelativeDateTest` (Locale.US, `ZoneOffset.UTC`, now = `2026-09-25T15:00:00Z`, a Friday): 14:02Z →
"14:02" and (12 h) "2:02 PM"; `2026-09-24T23:59Z` → "Yesterday"; `2026-09-21T10:00Z` → "Mon"; `2026-09-19` → "Sat";
`2026-09-18` → "18 Sep"; `2026-01-02` → "2 Jan"; `2025-09-21` → "21 Sep 2025"; `2026-09-26T10:00Z` → "26 Sep";
zone `Europe/Berlin` with now `2026-09-25T00:30+02:00` and t `2026-09-24T23:30+02:00` → "Yesterday"; null → "".

### §D `DocumentSession` (copy)
```kotlin
package dev.mdwriter.ui.editor

/** What the library needs from the open-document session. Implemented by EditorViewModel. */
interface DocumentSession {
    val current: StateFlow<DocRef?>
    /** Awaits the pending save of the current document (AutosaveCoordinator.flush). */
    suspend fun flush()
    /** Leaves the current doc (flush; auto-name / delete-if-empty unless [leaveCurrent] = false), then opens [ref].
     *  IME is shown after the install iff [showIme]. */
    suspend fun open(ref: DocRef, showIme: Boolean, leaveCurrent: Boolean = true)
    /** The library renamed or moved the open document: re-key autosave, recovery, positions, ui state. */
    fun onCurrentRefChanged(old: DocRef, new: DocRef)
}
```

### §E `AutoNamer` (sketch — adapt accessor names to T11; keep the rules exactly)
```kotlin
enum class LeaveReason { Switch, DrawerOpened, Stopped }
sealed interface LeaveOutcome { data object Kept : LeaveOutcome; data object Deleted : LeaveOutcome
    data class Renamed(val newRef: DocRef) : LeaveOutcome }

class AutoNamer(
    private val library: LibraryRepository, private val documents: DocumentRepository,
    private val settings: SettingsRepository, private val positions: PositionStore,
) {
    suspend fun onLeave(ref: DocRef, reason: LeaveReason): LeaveOutcome {
        if (ref is DocRef.External) return LeaveOutcome.Kept
        val key = ref.key()
        if (key.value !in settings.settings.first().autoNamed) return LeaveOutcome.Kept   // user renamed it once
        val text = runCatching { documents.load(ref).text }.getOrElse { return LeaveOutcome.Kept }
        if (text.isBlank()) {                         // untitled + empty: delete only on a real switch
            if (reason != LeaveReason.Switch) return LeaveOutcome.Kept
            library.trash(ref)
            settings.update { it.copy(autoNamed = it.autoNamed - key.value) }
            positions.remove(key)
            return LeaveOutcome.Deleted
        }
        val base = DocTitle.fromContent(text)?.let(DocTitle::sanitizeFileName)?.trim()
        if (base.isNullOrEmpty()) return LeaveOutcome.Kept
        val folder = library.parentOf(ref) ?: return LeaveOutcome.Kept
        val current = UniqueName.splitName(library.nameOf(ref) ?: return LeaveOutcome.Kept).first
        if (current == base || Regex("^${Regex.escape(base)} \\d+$").matches(current)) return LeaveOutcome.Kept
        val newRef = library.rename(ref, folder, base)            // "Groceries.md" or "Groceries 2.md"
        settings.update { it.copy(autoNamed = it.autoNamed - key.value + newRef.key().value) }
        positions.move(key, newRef.key())
        return LeaveOutcome.Renamed(newRef)
    }
}
```
`AutoNamerTest` (fake stores, TemporaryFolder-backed `InternalStore` is fine): not in `autoNamed` → Kept, no rename;
"# Groceries\n- bread" → `Groceries.md`, key replaced in `autoNamed`, position moved; existing `Groceries.md` →
`Groceries 2.md`; current `Groceries 2.md` with the same title → Kept (no churn); blank text + Switch → Deleted and
the file is gone from the listing; blank text + DrawerOpened / Stopped → Kept; `DocRef.External` → Kept; title with
`/:*?` → sanitized name.

### §F `LibraryViewModel` — the parts that must be exactly like this (sketch)
```kotlin
class LibraryViewModel(
    private val library: LibraryRepository, private val settings: SettingsRepository,
    private val positions: PositionStore, private val session: DocumentSession,
    private val appScope: CoroutineScope, private val io: CoroutineDispatcher,
) : ViewModel() {
    private val crumbs = MutableStateFlow(listOf(Crumb(library.rootOf(LocationId.Internal), ON_THIS_DEVICE)))
    private val pending = MutableStateFlow<PendingDelete?>(null)
    private val query = MutableStateFlow<String?>(null)                 // null = search inactive
    private val _events = Channel<LibraryEvent>(Channel.BUFFERED); val events = _events.receiveAsFlow()
    private var commitJob: Job? = null

    private val entries = crumbs.map { it.last().folder }.distinctUntilChanged()
        .flatMapLatest { library.entries(it) }
    private val hits = query.debounce(250).mapLatest { q ->
        if (q.isNullOrBlank()) null else withContext(io) { library.search(crumbs.value.first().folder.location, q) }
    }
    // uiState = combine(entries, settings.settings, pending, query, hits, crumbs) { … sortEntries(…), hide pending … }
    //   .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)
    // FileItem.excerpt = entry.excerpt ?: Excerpt.fromPrefix(library.prefix(entry)) — computed on io, per entry

    fun delete(entry: LibraryEntry) = viewModelScope.launch {
        val ref = entry.doc ?: return@launch
        commitPendingNow()                                   // a second delete commits the first (platform §2.7)
        pending.value = PendingDelete(entry)                 // hidden from the list immediately
        if (ref.key() == session.current.value?.key()) {     // deleting the OPEN doc
            session.flush()
            val next = uiState.value.files.map { it.entry }.filter { it.doc?.key() != ref.key() }
                .maxByOrNull { it.lastModified ?: 0L }?.doc
                ?: newUntitledIn(currentFolder())            // fresh note, key added to autoNamed
            session.open(next, showIme = false, leaveCurrent = false)   // never auto-name a doc being deleted
        }
        commitJob = viewModelScope.launch { delay(UNDO_WINDOW_MS); commitPendingNow() }
    }
    fun undoDelete() { commitJob?.cancel(); pending.value = null }
    /** Snackbar timeout, a second delete, or ON_STOP. Runs in appScope so a finishing activity cannot drop it. */
    fun commitPendingNow() {
        commitJob?.cancel()
        val p = pending.getAndUpdate { null } ?: return
        val ref = p.entry.doc ?: return
        appScope.launch(NonCancellable) {
            library.trash(ref)
            settings.update { it.copy(autoNamed = it.autoNamed - ref.key().value) }
            positions.remove(ref.key())
        }
    }
    companion object { const val UNDO_WINDOW_MS = 5_000L }
}
```
Other actions (all `viewModelScope.launch`, errors → `LibraryEvent.Message(text)` shown in the snackbar host):
- `newNote()`: `ref = library.createUnique(folder, "Untitled", settings.newNoteExtension)`; add key to `autoNamed`;
  `session.open(ref, showIme = true)`; send `LibraryEvent.CloseDrawer`.
- `open(entry)`: same doc → just `CloseDrawer`; else `session.open(ref, showIme = false)` then `CloseDrawer`.
- `rename(entry, newBase)`: if open → `session.flush()`; `newRef = library.rename(ref, folder, newBase)`; remove the
  old key from `autoNamed` (explicit rename ends auto-naming); `positions.move`; if open → `onCurrentRefChanged`.
- `duplicate(entry)`: flush if open; `library.duplicate(ref, folder)` → `"<base> copy.<ext>"` (not auto-named).
- `move(entry, target)`: flush if open; `newRef = library.move(ref, target)`; move the `autoNamed` key and the
  position; if open → `onCurrentRefChanged`. The dialog disables the doc's current folder.
- `createFolder(name)`, `openFolder(entry)` (push crumb), `goTo(crumbIndex)` (truncate), `up()`,
  `setSort(order)` (persist via `settings.update`), `startSearch()`, `setQuery(q)`, `closeSearch()`,
  `onDrawerOpened()` (`library.invalidate()`), `onDrawerClosed()` (`closeSearch()`), `onStop()` (`commitPendingNow()`).
- `sortEntries(entries, order)` is a top-level pure function: folders first, always by name A–Z
  (`String.CASE_INSENSITIVE_ORDER`); files by the chosen order (null `lastModified` sorts as 0).
- `validateName(input, currentName, siblingsLower, ext)` (in `LibraryDialogs.kt`, pure): returns
  `NameCheck.Ok(sanitizedBase)` / `Empty` / `Exists` / `Unchanged`; uses `DocTitle.sanitizeFileName`. Decide `Empty` on the
  RAW input first (`input.isBlank()`, or blank once the extension is dropped): `sanitizeFileName` never returns "" (it falls back to `"Untitled"`).

### §G Root wiring (sketch; Compose 1.12 / material3 1.4.0 APIs verified in factcheck A7)
```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MdWriterRoot(container: AppContainer) {
    val editorVm: EditorViewModel = viewModel { /* T11's initializer */ }
    val libraryVm: LibraryViewModel = viewModel {
        LibraryViewModel(container.library, container.settings, container.positions, editorVm,
            container.applicationScope, container.dispatchers.io)
    }
    val controller = remember { EditorController(context, initialStyle) }           // hoisted (was in EditorScreen?)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    var restoreIme by remember { mutableStateOf(false) }
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.targetValue }.drop(1).distinctUntilChanged().collect { target ->
            if (target == DrawerValue.Open) {
                restoreIme = imeVisible && controller.hasFocus()                  // add hasFocus() if missing
                controller.collapseSelection(); controller.hideIme()
                editorVm.onDrawerOpened(); libraryVm.onDrawerOpened()
            } else {
                libraryVm.onDrawerClosed()
                if (restoreIme) { controller.requestFocus(); controller.showIme() }
                restoreIme = false
            }
        }
    }
    LaunchedEffect(libraryVm) {
        libraryVm.events.collect { if (it == LibraryEvent.CloseDrawer) { restoreIme = false; drawerState.close() } }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { libraryVm.onStop() }
    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open,
            scrimColor = colors.scrim,
            drawerContent = {
                ModalDrawerSheet(drawerState = drawerState, modifier = Modifier.width(drawerWidth),
                    drawerShape = RectangleShape, drawerContainerColor = colors.surface,
                    drawerContentColor = colors.text, drawerTonalElevation = 0.dp) {
                    LibraryDrawer(libraryVm, openDoc = editorUi.doc?.key())
                }
            },
        ) { EditorScreen(editorVm, controller, onOpenLibrary = { scope.launch { drawerState.open() } }) }
        DeleteUndoSnackbarHost(libraryVm, Modifier.align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)))  // drawn ABOVE the drawer
    }
}
```
`DeleteUndoSnackbarHost`: `LaunchedEffect(pending?.id) { if (pending != null) { if (host.showSnackbar(
"Deleted ‘${pending.title}’", actionLabel = "Undo", duration = SnackbarDuration.Indefinite) ==
SnackbarResult.ActionPerformed) vm.undoDelete() } else host.currentSnackbarData?.dismiss() }` — the 5 s timer lives
in the ViewModel (testable), the snackbar only mirrors it. Style: `surface` container, `text` content and action,
12 dp shape, hairline `divider` border, no elevation. The same host shows `LibraryEvent.Message`s (Short).

## Acceptance criteria
1. `RelativeDateTest`, `UniqueNameTest`, `ExcerptTest`, `AutoNamerTest` pass with every row listed in §A–§C/§E.
2. `LibraryViewModelTest` (Turbine + `StandardTestDispatcher`, `Dispatchers.setMain`, `FakeDocumentSession`) passes:
   (a) entries sorted folders-first then by each `SortOrder`; (b) `delete` hides the entry at once, `advanceTimeBy(4_999)`
   → still on disk, `advanceTimeBy(2)` → trashed; (c) `undoDelete` within 5 s → entry back, never trashed; (d) a second
   `delete` commits the first immediately; (e) `onStop()` commits; (f) deleting the open doc calls
   `session.open(newestOther, showIme = false, leaveCurrent = false)`, or a fresh `Untitled.md` when it was the only
   file; (g) `newNote()` creates `Untitled.md` then `Untitled 2.md`, adds keys to `autoNamed`, opens with
   `showIme = true`, emits `CloseDrawer`; (h) `rename` of the open doc calls `flush()` before and
   `onCurrentRefChanged` after, and removes the key from `autoNamed`; (i) search "rain" (after `advanceTimeBy(300)`)
   returns a note whose name lacks "rain" but whose content has it, name matches first; (j) crumbs push/`goTo`/`up`.
3. `LibraryUiTest` (Robolectric, v2 `createComposeRule`): file row shows "Groceries" (not "Groceries.md") and with
   `showExtensions = true` shows "Groceries.md"; the open row has a node tagged `activeFileBar`; empty folder shows
   "No notes yet" and clicking "New note" calls `onNewNote`; `NameDialog` pre-fills the current name, "Rename" is
   disabled for blank input and for an existing sibling name (error text "A note with this name already exists"),
   and confirming calls back with the sanitized base.
4. On the emulator (debug app): the temporary glyph opens the drawer; the IME (if it was up) goes down; closing the
   drawer with the scrim brings it back only if the editor had focus.
5. "+" in the header → drawer closes, editor is empty and focused, IME visible, and
   `adb shell run-as dev.mdwriter.debug ls files/library` lists `Untitled.md`. Type "# Shopping list", open the
   drawer → the row now reads "Shopping list" (file `Shopping list.md`).
6. "+" and then immediately open another note → `Untitled*.md` no longer exists in `files/library` (empty untitled
   deleted on switch). The welcome note is never renamed automatically.
7. Rename / Duplicate / Move… / Delete work from the long-press menu; Delete shows "Deleted ‘X’ · Undo" for 5 s; Undo
   restores the row; without Undo the file is gone from `files/library` (it is in `files/.trash`); deleting the open
   note switches the editor to the newest other note.
8. Folder menu → "New folder…" creates a folder row; tapping it drills in (breadcrumb "On this device › Drafts");
   tapping the first crumb returns to the root.
9. Screenshots light + dark of the open drawer match 02 §7/§12: "Library" header with search + blue new-note icon,
   "Locations" / "On this device" (bold), hairline, breadcrumb + sort icon, the open note bold with a 3 dp blue bar,
   excerpts grey, dates end-aligned; drawer ≤ 360 dp wide (on the 448 dp AVD: 360 dp); no shadows, no tonal tint.
10. `make check` is green.

## Verification commands
```bash
cd /Users/gcg/Work/src/github.com/gcg/mdwriter
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"; ADB=~/Library/Android/sdk/platform-tools/adb
./gradlew :app:testDebugUnitTest --tests 'dev.mdwriter.ui.library.*' --tests 'dev.mdwriter.data.library.*'
make format && make check
make emulator && make devices                                   # serial assumed emulator-5554 below
make install-debug DEVICE=emulator-5554
$ADB -s emulator-5554 shell run-as dev.mdwriter.debug ls -la files/library files/.trash
$ADB -s emulator-5554 exec-out screencap -p > /tmp/t12-drawer-light.png
$ADB -s emulator-5554 shell cmd uimode night yes && sleep 1 && $ADB -s emulator-5554 exec-out screencap -p > /tmp/t12-drawer-dark.png
$ADB -s emulator-5554 shell cmd uimode night no
$ADB -s emulator-5554 logcat -b crash -d | grep dev.mdwriter || echo "no crashes"
```

## Pitfalls
- **Do not rely on M3's drag-to-open.** `gesturesEnabled` must be false while closed (platform §4.1 #3–#5: the
  EditText's cursor-drag wins over `anchoredDraggable`). While open it must be true, otherwise the scrim tap does
  not dismiss. Use the `ModalDrawerSheet(drawerState, …)` overload — the other one does not handle predictive back.
- **Undo must not depend on restore.** The pending delete hides the entry and only calls `trash()` on commit
  (platform §2.3: SAF trash revokes our grant, so T14 could never restore). Commit runs in `applicationScope` +
  `NonCancellable`.
- **Never auto-name a document that is being deleted** (`leaveCurrent = false`), and never rename on every
  keystroke — only on leave (platform §2.6). Always `flush()` before rename/move/duplicate of the open doc, and use
  the **returned** `DocRef` afterwards (01 §6.3 `rename` contract).
- **Delete-if-empty only on a real switch**, not on drawer open or `ON_STOP` (the user is still "in" that note).
- **All I/O off main** (01 §7): listings/prefix reads on `io`, search debounced 250 ms and `mapLatest`-cancelled.
- **No `beginBatchEdit`, no `setText`** from the library: opening a document goes through T11's install path.
- **Flat UI**: `tonalElevation = 0.dp`, `shadowElevation = 0.dp` everywhere (02 §2); `accent` only for the caret-ish
  affordances (new-note icon, active bar), never for text (contrast 2.2:1).
- **Hairlines are 1 physical px**, not 1 dp (design.md §5.4).
- `java.time` month abbreviations: `Locale.UK` gives "Sept" on JDK 21 and Android ICU — the tests use `Locale.US`.
- `WindowInsets.isImeVisible` is `@ExperimentalLayoutApi` — opt in locally.
- Strings go to `strings.xml` (including "On this device", "Yesterday", "Deleted ‘%1$s’", "Undo").

## Definition of done
- [ ] Acceptance criteria 1–10 verified; evidence (test counts, adb listings, screenshot descriptions) in STATUS.
- [ ] `make check` green; no new dependencies; no hard rule broken (01 §10).
- [ ] `01-architecture.md` §3 package map updated with `DocumentSession.kt`, `AutoNamer.kt`, `UniqueName.kt`,
      `Excerpt.kt`, `LibraryUiState.kt`, `LibrarySnackbar.kt`, `LibraryDialogs.kt` (additive; note in STATUS).
- [ ] STATUS entry `## T12 — Library drawer UI + file operations — DONE — <date>` including "Notes for the next
      task": where the drawer state lives, the `rowMenuExtras` slot (T18), the `locations` list (T14), and the names
      of any Settings/LibraryRepository members you added.
- [ ] One commit `T12: library drawer, file operations, auto-naming`.
